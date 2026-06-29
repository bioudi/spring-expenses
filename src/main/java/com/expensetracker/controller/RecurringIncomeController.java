package com.expensetracker.controller;

import com.expensetracker.dto.RecurringIncomeRequest;
import com.expensetracker.dto.RecurringIncomeResponse;
import com.expensetracker.service.RecurringIncomeService;
import com.expensetracker.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST CRUD over {@link com.expensetracker.entity.RecurringIncome} templates,
 * mirroring {@link RecurringExpenseController} one-for-one so the recurring
 * expense and recurring income UIs can share plumbing on the frontend.
 *
 * <p>No pagination — the recurring templates list is bounded by what a single
 * user can plausibly maintain (typically &lt; 20 rows), and the
 * recurring-expense counterpart already returns the entire list in a single
 * payload. Add pagination later if a power user shows up with hundreds.
 */
@RestController
@RequestMapping("/api/recurring-incomes")
@RequiredArgsConstructor
public class RecurringIncomeController {

    private final RecurringIncomeService recurringIncomeService;

    @GetMapping
    public ResponseEntity<List<RecurringIncomeResponse>> getRecurringIncomes() {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringIncomeService.getRecurringIncomes(userId));
    }

    @PostMapping
    public ResponseEntity<RecurringIncomeResponse> createRecurringIncome(
            @Valid @RequestBody RecurringIncomeRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        RecurringIncomeResponse response = recurringIncomeService.createRecurringIncome(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/from-income/{incomeId}")
    public ResponseEntity<RecurringIncomeResponse> createFromIncome(
            @PathVariable UUID incomeId,
            @Valid @RequestBody RecurringIncomeRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        RecurringIncomeResponse response = recurringIncomeService.createFromIncome(incomeId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringIncomeResponse> getRecurringIncome(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringIncomeService.getRecurringIncomeById(id, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringIncomeResponse> updateRecurringIncome(
            @PathVariable UUID id,
            @Valid @RequestBody RecurringIncomeRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringIncomeService.updateRecurringIncome(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecurringIncome(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        recurringIncomeService.deleteRecurringIncome(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<RecurringIncomeResponse> toggleRecurringIncome(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(recurringIncomeService.toggleRecurringIncome(id, userId));
    }
}
