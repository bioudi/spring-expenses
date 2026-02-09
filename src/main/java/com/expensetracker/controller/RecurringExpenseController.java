package com.expensetracker.controller;

import com.expensetracker.dto.RecurringExpenseRequest;
import com.expensetracker.dto.RecurringExpenseResponse;
import com.expensetracker.service.RecurringExpenseService;
import com.expensetracker.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recurring-expenses")
@RequiredArgsConstructor
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    @GetMapping
    public ResponseEntity<List<RecurringExpenseResponse>> getRecurringExpenses() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringExpenseService.getRecurringExpenses(userId));
    }

    @PostMapping
    public ResponseEntity<RecurringExpenseResponse> createRecurringExpense(@Valid @RequestBody RecurringExpenseRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        RecurringExpenseResponse response = recurringExpenseService.createRecurringExpense(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/from-expense/{expenseId}")
    public ResponseEntity<RecurringExpenseResponse> createFromExpense(
            @PathVariable UUID expenseId,
            @Valid @RequestBody RecurringExpenseRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        RecurringExpenseResponse response = recurringExpenseService.createFromExpense(expenseId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringExpenseResponse> getRecurringExpense(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringExpenseService.getRecurringExpenseById(id, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringExpenseResponse> updateRecurringExpense(
            @PathVariable UUID id,
            @Valid @RequestBody RecurringExpenseRequest request
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringExpenseService.updateRecurringExpense(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecurringExpense(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        recurringExpenseService.deleteRecurringExpense(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<RecurringExpenseResponse> toggleRecurringExpense(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringExpenseService.toggleRecurringExpense(id, userId));
    }
}
