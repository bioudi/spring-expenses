package com.expensetracker.controller;

import com.expensetracker.dto.BudgetRequest;
import com.expensetracker.dto.BudgetResponse;
import com.expensetracker.dto.BudgetSuggestionResponse;
import com.expensetracker.service.BudgetService;
import com.expensetracker.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public ResponseEntity<List<BudgetResponse>> getBudgets(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<BudgetResponse> budgets = budgetService.getBudgets(userId, date);
        return ResponseEntity.ok(budgets);
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<BudgetSuggestionResponse>> getSuggestions() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<BudgetSuggestionResponse> suggestions = budgetService.getSuggestions(userId);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BudgetResponse> getBudgetById(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        BudgetResponse budget = budgetService.getBudgetById(id, userId);
        return ResponseEntity.ok(budget);
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> createBudget(@Valid @RequestBody BudgetRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        BudgetResponse budget = budgetService.createBudget(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(budget);
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponse> updateBudget(@PathVariable UUID id, @Valid @RequestBody BudgetRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        BudgetResponse budget = budgetService.updateBudget(id, request, userId);
        return ResponseEntity.ok(budget);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        budgetService.deleteBudget(id, userId);
        return ResponseEntity.noContent().build();
    }
}
