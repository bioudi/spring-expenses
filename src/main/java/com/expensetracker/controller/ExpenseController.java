package com.expensetracker.controller;

import com.expensetracker.dto.DashboardResponse;
import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.dto.ExpenseSummary;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<List<ExpenseResponse>> getExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<ExpenseResponse> expenses = expenseService.getExpenses(startDate, endDate, category, userId);
        return ResponseEntity.ok(expenses);
    }

    @GetMapping("/summary")
    public ResponseEntity<ExpenseSummary> getSummary() {
        UUID userId = SecurityUtils.getCurrentUserId();
        ExpenseSummary summary = expenseService.getSummary(userId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID userId = SecurityUtils.getCurrentUserId();
        DashboardResponse dashboard = expenseService.getDashboard(date, userId);
        return ResponseEntity.ok(dashboard);
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> createExpense(@Valid @RequestBody ExpenseRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ExpenseResponse expense = expenseService.createExpense(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(expense);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getExpenseById(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ExpenseResponse expense = expenseService.getExpenseById(id, userId);
        return ResponseEntity.ok(expense);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponse> updateExpense(@PathVariable UUID id, @Valid @RequestBody ExpenseRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ExpenseResponse expense = expenseService.updateExpense(id, request, userId);
        return ResponseEntity.ok(expense);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ExpenseResponse> deleteExpense(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        ExpenseResponse expense = expenseService.deleteExpense(id, userId);
        return ResponseEntity.ok(expense);
    }
}
