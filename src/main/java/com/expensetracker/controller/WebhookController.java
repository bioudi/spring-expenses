package com.expensetracker.controller;

import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final ExpenseService expenseService;

    @PostMapping("/expense")
    public ResponseEntity<ExpenseResponse> createExpense(
            @Valid @RequestBody ExpenseRequest request
    ) {
        log.info("Received webhook request: amount={}, merchant={}, cardName={}, card={}, name={}, category={}, notes={}, timestamp={}",
                request.getAmount(),
                request.getMerchant(),
                request.getCardName(),
                request.getCard(),
                request.getName(),
                request.getCategory(),
                request.getNotes(),
                request.getTimestamp());

        ExpenseResponse response = expenseService.createExpense(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
