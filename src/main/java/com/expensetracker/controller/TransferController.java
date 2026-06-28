package com.expensetracker.controller;

import com.expensetracker.dto.TransferRequest;
import com.expensetracker.dto.TransferResponse;
import com.expensetracker.service.TransferService;
import com.expensetracker.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Endpoint for moving funds between the caller's own accounts — including
 * credit card payments. The four balance-adjustment cases (non-CREDIT ↔
 * non-CREDIT, non-CREDIT → CREDIT, CREDIT → non-CREDIT, CREDIT → CREDIT)
 * are handled inside {@link TransferService}.
 */
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    /**
     * Creates a transfer between two of the caller's accounts. Both
     * {@code fromAccountId} and {@code toAccountId} are required; the amount
     * must be strictly positive. Returns 201 with the post-transfer balances
     * for both sides.
     *
     * <p>Failure modes mapped by {@code GlobalExceptionHandler}:
     * <ul>
     *   <li>400 — bean-validation errors (missing fields, non-positive amount)</li>
     *   <li>404 — either account does not exist or belongs to another user</li>
     *   <li>409 — {@code fromAccountId} == {@code toAccountId}
     *       (raised as {@code IllegalArgumentException})</li>
     *   <li>422 — source account is non-CREDIT and the transfer would
     *       overdraw it ({@code InsufficientFundsException})</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(@Valid @RequestBody TransferRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        TransferResponse response = transferService.transfer(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}