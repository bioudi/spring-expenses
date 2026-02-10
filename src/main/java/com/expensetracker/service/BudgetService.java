package com.expensetracker.service;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.expensetracker.config.ExpenseCategory;
import com.expensetracker.dto.BudgetRequest;
import com.expensetracker.dto.BudgetResponse;
import com.expensetracker.dto.BudgetSuggestionResponse;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.exception.BudgetNotFoundException;
import com.expensetracker.exception.InvalidCategoryException;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;
    private final EntityManager entityManager;
    private final AnthropicClientService anthropicClientService;
    private final ObjectMapper objectMapper;

    @Transactional
    public BudgetResponse createBudget(BudgetRequest request, UUID userId) {
        request.getCategories().forEach(this::validateCategory);
        checkForOverlap(request.getCategories(), null, userId);

        User userRef = entityManager.getReference(User.class, userId);

        Budget budget = Budget.builder()
                .categories(new ArrayList<>(request.getCategories()))
                .monthlyLimit(request.getMonthlyLimit())
                .user(userRef)
                .build();

        Budget saved = budgetRepository.save(budget);
        Map<String, BigDecimal> spentByCategory = getCurrentMonthSpent(userId);
        BigDecimal spent = sumSpentForCategories(saved.getCategories(), spentByCategory);
        log.info("Created budget for categories {} with limit {}", saved.getCategories(), saved.getMonthlyLimit());
        return BudgetResponse.fromEntity(saved, spent);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgets(UUID userId) {
        List<Budget> budgets = budgetRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        Map<String, BigDecimal> spentByCategory = getCurrentMonthSpent(userId);

        return budgets.stream()
                .map(b -> BudgetResponse.fromEntity(b, sumSpentForCategories(b.getCategories(), spentByCategory)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BudgetResponse getBudgetById(UUID id, UUID userId) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BudgetNotFoundException(id));
        Map<String, BigDecimal> spentByCategory = getCurrentMonthSpent(userId);
        BigDecimal spent = sumSpentForCategories(budget.getCategories(), spentByCategory);
        return BudgetResponse.fromEntity(budget, spent);
    }

    @Transactional
    public BudgetResponse updateBudget(UUID id, BudgetRequest request, UUID userId) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BudgetNotFoundException(id));

        request.getCategories().forEach(this::validateCategory);
        checkForOverlap(request.getCategories(), id, userId);

        budget.setCategories(new ArrayList<>(request.getCategories()));
        budget.setMonthlyLimit(request.getMonthlyLimit());

        Budget saved = budgetRepository.save(budget);
        Map<String, BigDecimal> spentByCategory = getCurrentMonthSpent(userId);
        BigDecimal spent = sumSpentForCategories(saved.getCategories(), spentByCategory);
        log.info("Updated budget {}: categories={}, limit={}", id, saved.getCategories(), saved.getMonthlyLimit());
        return BudgetResponse.fromEntity(saved, spent);
    }

    @Transactional
    public void deleteBudget(UUID id, UUID userId) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BudgetNotFoundException(id));
        budgetRepository.delete(budget);
        log.info("Deleted budget {}: categories={}", id, budget.getCategories());
    }

    private BigDecimal sumSpentForCategories(List<String> categories, Map<String, BigDecimal> spentByCategory) {
        return categories.stream()
                .map(c -> spentByCategory.getOrDefault(c, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void checkForOverlap(List<String> categories, UUID excludeBudgetId, UUID userId) {
        List<Budget> existing = budgetRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        for (Budget b : existing) {
            if (excludeBudgetId != null && b.getId().equals(excludeBudgetId)) continue;
            Set<String> overlap = new HashSet<>(b.getCategories());
            overlap.retainAll(categories);
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException(
                        "Category already in another budget: " + String.join(", ", overlap));
            }
        }
    }

    private Map<String, BigDecimal> getCurrentMonthSpent(UUID userId) {
        LocalDate now = LocalDate.now();
        LocalDate monthStart = now.withDayOfMonth(1);
        LocalDate monthEnd = now.with(TemporalAdjusters.lastDayOfMonth());

        List<Expense> expenses = expenseRepository.findByUserIdAndDateRange(
                userId, monthStart.atStartOfDay(), monthEnd.atTime(LocalTime.MAX));

        return expenses.stream()
                .collect(Collectors.groupingBy(
                        Expense::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, Expense::getAmount, BigDecimal::add)
                ));
    }

    @Transactional(readOnly = true)
    public List<BudgetSuggestionResponse> getSuggestions(UUID userId) {
        if (!anthropicClientService.isAvailable()) {
            log.debug("Skipping budget suggestions — Anthropic client not available");
            return List.of();
        }

        // Get last 3 months of expenses
        LocalDate now = LocalDate.now();
        LocalDate threeMonthsAgo = now.minusMonths(3).withDayOfMonth(1);
        List<Expense> expenses = expenseRepository.findByUserIdAndDateRange(
                userId, threeMonthsAgo.atStartOfDay(), now.atTime(LocalTime.MAX));

        if (expenses.isEmpty()) {
            return List.of();
        }

        // Group by category → compute total and avg monthly
        Map<String, BigDecimal> totalByCategory = new HashMap<>();
        for (Expense e : expenses) {
            totalByCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
        }
        Map<String, BigDecimal> avgMonthlyByCategory = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : totalByCategory.entrySet()) {
            avgMonthlyByCategory.put(entry.getKey(),
                    entry.getValue().divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP));
        }

        // Exclude categories that already have budgets
        List<Budget> existingBudgets = budgetRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        Set<String> budgetedCategories = new HashSet<>();
        for (Budget b : existingBudgets) {
            budgetedCategories.addAll(b.getCategories());
        }
        avgMonthlyByCategory.keySet().removeAll(budgetedCategories);

        if (avgMonthlyByCategory.isEmpty()) {
            return List.of();
        }

        // Build prompt
        StringBuilder dataJson = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, BigDecimal> entry : avgMonthlyByCategory.entrySet()) {
            if (!first) dataJson.append(",");
            first = false;
            dataJson.append("{\"category\":\"").append(entry.getKey())
                    .append("\",\"avgMonthly\":").append(entry.getValue())
                    .append(",\"total3Months\":").append(totalByCategory.get(entry.getKey()))
                    .append("}");
        }
        dataJson.append("]");

        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_HAIKU_4_5_20251001)
                    .maxTokens(500L)
                    .system("You are a budget advisor. Given spending data for unbudgeted categories over 3 months, " +
                            "return a JSON array of budget suggestions. Each object: {\"categories\": [\"Category1\"], " +
                            "\"suggestedLimit\": 150.00, \"reasoning\": \"brief reason\"}. " +
                            "Suggest limits slightly above the 3-month average (10-20% buffer). " +
                            "Group related low-spend categories together if appropriate. " +
                            "Return ONLY valid JSON, no markdown, no explanation.")
                    .addUserMessage("Spending data for unbudgeted categories:\n" + dataJson)
                    .build();

            Message message = anthropicClientService.getClient().messages().create(params);
            String responseText = message.content().get(0).asText().text().trim();

            List<BudgetSuggestionResponse> suggestions = objectMapper.readValue(
                    responseText, new TypeReference<>() {});

            log.info("Generated {} budget suggestions for user {}", suggestions.size(), userId);
            return suggestions;
        } catch (Exception e) {
            log.error("Failed to generate budget suggestions: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return List.of();
        }
    }

    private void validateCategory(String category) {
        if (!ExpenseCategory.isValid(category)) {
            throw new InvalidCategoryException(
                    "Invalid category: " + category + ". Valid categories are: " +
                            String.join(", ", ExpenseCategory.VALID_CATEGORIES)
            );
        }
    }
}
