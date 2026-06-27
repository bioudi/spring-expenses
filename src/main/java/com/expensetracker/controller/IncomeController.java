package com.expensetracker.controller;

import com.expensetracker.dto.IncomeRequest;
import com.expensetracker.dto.IncomeResponse;
import com.expensetracker.service.IncomeService;
import com.expensetracker.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/incomes")
@RequiredArgsConstructor
public class IncomeController {

    private final IncomeService incomeService;

    @PostMapping
    public ResponseEntity<IncomeResponse> createIncome(@Valid @RequestBody IncomeRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        IncomeResponse income = incomeService.createIncome(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(income);
    }

    @GetMapping
    public ResponseEntity<List<IncomeResponse>> getIncomes() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<IncomeResponse> incomes = incomeService.getIncomes(userId);
        return ResponseEntity.ok(incomes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncomeResponse> getIncomeById(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        IncomeResponse income = incomeService.getIncomeById(id, userId);
        return ResponseEntity.ok(income);
    }

    @PutMapping("/{id}")
    public ResponseEntity<IncomeResponse> updateIncome(@PathVariable UUID id, @Valid @RequestBody IncomeRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        IncomeResponse income = incomeService.updateIncome(id, request, userId);
        return ResponseEntity.ok(income);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<IncomeResponse> deleteIncome(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        IncomeResponse income = incomeService.deleteIncome(id, userId);
        return ResponseEntity.ok(income);
    }
}
