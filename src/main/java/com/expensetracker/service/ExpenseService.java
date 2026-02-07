package com.expensetracker.service;

import com.expensetracker.config.ExpenseCategory;
import com.expensetracker.dto.*;
import com.expensetracker.entity.Expense;
import com.expensetracker.exception.InvalidCategoryException;
import com.expensetracker.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategorizationService categorizationService;

    private static final String DEFAULT_CATEGORY = "Uncategorized";

    @Transactional
    public ExpenseResponse createExpense(ExpenseRequest request) {
        String category;

        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            // Explicit category provided — validate it
            category = request.getCategory();
            validateCategory(category);
            log.info("Expense for merchant '{}' — using provided category: '{}'", request.getMerchant(), category);
        } else {
            // No category — try AI categorization based on merchant name
            log.info("No category provided for merchant '{}', requesting AI categorization...", request.getMerchant());
            String aiCategory = categorizationService.categorize(request.getMerchant());
            category = aiCategory != null ? aiCategory : DEFAULT_CATEGORY;
            log.info("Expense for merchant '{}' — final category: '{}' (source: {})",
                    request.getMerchant(), category, aiCategory != null ? "Claude AI" : "default fallback");
        }

        // Use "name" field as notes if notes is empty
        String notes = request.getNotes();
        if ((notes == null || notes.isBlank()) && request.getName() != null) {
            notes = request.getName();
        }

        // Use "card" field if "cardName" is empty
        String cardName = request.getCardName();
        if ((cardName == null || cardName.isBlank()) && request.getCard() != null) {
            cardName = request.getCard();
        }

        Expense expense = Expense.builder()
                .amount(request.getAmount())
                .category(category)
                .merchant(request.getMerchant())
                .cardName(cardName)
                .timestamp(request.getTimestamp() != null ? request.getTimestamp() : LocalDateTime.now())
                .notes(notes)
                .build();

        Expense saved = expenseRepository.save(expense);
        return ExpenseResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(LocalDate startDate, LocalDate endDate, String category) {
        if (category != null && !category.isBlank()) {
            validateCategory(category);
        }

        List<Expense> expenses;

        if (startDate != null && endDate != null) {
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(LocalTime.MAX);

            if (category != null && !category.isBlank()) {
                expenses = expenseRepository.findByDateRangeAndCategory(start, end, category);
            } else {
                expenses = expenseRepository.findByDateRange(start, end);
            }
        } else if (category != null && !category.isBlank()) {
            expenses = expenseRepository.findByCategory(category);
        } else {
            expenses = expenseRepository.findAllByOrderByTimestampDesc();
        }

        return expenses.stream()
                .map(ExpenseResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExpenseSummary getSummary() {
        List<Expense> allExpenses = expenseRepository.findAll();

        if (allExpenses.isEmpty()) {
            return ExpenseSummary.builder()
                    .totalSpent(BigDecimal.ZERO)
                    .transactionCount(0)
                    .categoryBreakdown(Map.of())
                    .build();
        }

        BigDecimal totalSpent = allExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<Expense>> byCategory = allExpenses.stream()
                .collect(Collectors.groupingBy(Expense::getCategory));

        Map<String, ExpenseSummary.CategoryBreakdown> breakdown = new LinkedHashMap<>();

        for (String cat : ExpenseCategory.VALID_CATEGORIES) {
            List<Expense> catExpenses = byCategory.getOrDefault(cat, List.of());
            if (!catExpenses.isEmpty()) {
                BigDecimal catTotal = catExpenses.stream()
                        .map(Expense::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal percentage = catTotal
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalSpent, 2, RoundingMode.HALF_UP);

                breakdown.put(cat, ExpenseSummary.CategoryBreakdown.builder()
                        .total(catTotal)
                        .count(catExpenses.size())
                        .percentage(percentage)
                        .build());
            }
        }

        return ExpenseSummary.builder()
                .totalSpent(totalSpent)
                .transactionCount(allExpenses.size())
                .categoryBreakdown(breakdown)
                .build();
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
