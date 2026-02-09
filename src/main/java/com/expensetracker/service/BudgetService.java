package com.expensetracker.service;

import com.expensetracker.config.ExpenseCategory;
import com.expensetracker.dto.BudgetRequest;
import com.expensetracker.dto.BudgetResponse;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.exception.BudgetNotFoundException;
import com.expensetracker.exception.InvalidCategoryException;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.ExpenseRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private void validateCategory(String category) {
        if (!ExpenseCategory.isValid(category)) {
            throw new InvalidCategoryException(
                    "Invalid category: " + category + ". Valid categories are: " +
                            String.join(", ", ExpenseCategory.VALID_CATEGORIES)
            );
        }
    }
}
