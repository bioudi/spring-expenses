package com.expensetracker.controller;

import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.dto.WebhookBudgetsResponse;
import com.expensetracker.service.BudgetService;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final ExpenseService expenseService;
    private final BudgetService budgetService;

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

        // userId is set by ApiKeyFilter via SecurityContext
        UUID userId = SecurityUtils.getCurrentUserId();
        ExpenseResponse response = expenseService.createExpense(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/budgets")
    public ResponseEntity<WebhookBudgetsResponse> getBudgets(
            @RequestParam(name = "date", required = false) String date
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();
        LocalDate referenceDate = parseYearMonthOrDefault(date);
        WebhookBudgetsResponse response = budgetService.getWebhookBudgets(userId, referenceDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/expenses")
    public ResponseEntity<List<ExpenseResponse>> getExpenses(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String limit
    ) {
        log.info("Received webhook GET request: startDate={}, endDate={}, category={}, limit={}",
                startDate, endDate, category, limit);

        // Parse and validate dates (YYYY-MM-DD format)
        LocalDate parsedStartDate = null;
        LocalDate parsedEndDate = null;

        if (startDate != null) {
            try {
                parsedStartDate = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        if (endDate != null) {
            try {
                parsedEndDate = LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        // Parse and validate limit as positive integer
        Integer parsedLimit = null;
        if (limit != null) {
            try {
                parsedLimit = Integer.parseInt(limit);
                if (parsedLimit <= 0) {
                    return ResponseEntity.badRequest().build();
                }
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        // userId is set by ApiKeyFilter via SecurityContext
        UUID userId = SecurityUtils.getCurrentUserId();
        List<ExpenseResponse> expenses = expenseService.getExpenses(parsedStartDate, parsedEndDate, category, userId);

        // Apply limit if specified
        if (parsedLimit != null && parsedLimit < expenses.size()) {
            expenses = expenses.subList(0, parsedLimit);
        }

        return ResponseEntity.ok(expenses);
    }

    /**
     * Accepts either a null/blank value (defaults to current month) or a YYYY-MM string.
     * The day is forced to 1 so the service's month-window logic behaves identically
     * regardless of which day of the month was specified.
     */
    private LocalDate parseYearMonthOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        try {
            YearMonth ym = YearMonth.parse(value);
            return ym.atDay(1);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid date format. Expected YYYY-MM, got: " + value
            );
        }
    }
}
