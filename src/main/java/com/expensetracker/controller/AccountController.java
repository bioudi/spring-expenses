package com.expensetracker.controller;

import com.expensetracker.dto.AccountRequest;
import com.expensetracker.dto.AccountResponse;
import com.expensetracker.service.AccountService;
import com.expensetracker.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAccounts() {
        UUID userId = SecurityUtils.getCurrentUserId();
        List<AccountResponse> accounts = accountService.getAccounts(userId);
        return ResponseEntity.ok(accounts);
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        AccountResponse account = accountService.createAccount(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        AccountResponse account = accountService.getAccountById(id, userId);
        return ResponseEntity.ok(account);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccountResponse> updateAccount(@PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        AccountResponse account = accountService.updateAccount(id, request, userId);
        return ResponseEntity.ok(account);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<AccountResponse> deleteAccount(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getCurrentUserId();
        AccountResponse account = accountService.deleteAccount(id, userId);
        return ResponseEntity.ok(account);
    }
}