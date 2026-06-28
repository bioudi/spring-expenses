package com.expensetracker.controller;

import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.dto.WebhookBudgetsResponse;
import com.expensetracker.dto.WebhookDashboardResponse;
import com.expensetracker.dto.WebhookDashboardResponse.AccountBalanceEntry;
import com.expensetracker.dto.WebhookDashboardResponse.CategoryEntry;
import com.expensetracker.dto.WebhookDashboardResponse.MerchantEntry;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.service.BudgetService;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final ExpenseService expenseService;
    private final BudgetService budgetService;
    private final AccountRepository accountRepository;

    @PostMapping("/expense")
    public ResponseEntity<?> createExpense(
            @Valid @RequestBody ExpenseRequest request
    ) {
        log.info("Received webhook request: amount={}, merchant={}, cardName={}, card={}, name={}, category={}, notes={}, timestamp={}, accountId={}",
                request.getAmount(),
                request.getMerchant(),
                request.getCardName(),
                request.getCard(),
                request.getName(),
                request.getCategory(),
                request.getNotes(),
                request.getTimestamp(),
                request.getAccountId());

        // userId is set by ApiKeyFilter via SecurityContext
        UUID userId = SecurityUtils.getCurrentUserId();

        // Validate account_id early so a missing/invalid reference produces a clear 400
        // instead of bubbling up as a generic 404 from the service layer. The service
        // still revalidates inside the transaction, but failing fast here lets us
        // return a webhook-friendly error shape without touching the expense record.
        UUID accountId = request.getAccountId();
        if (accountId != null) {
            if (accountRepository.findByIdAndUserId(accountId, userId).isEmpty()) {
                log.warn("Webhook /expense rejected: account_id={} not found for userId={}",
                        accountId, userId);
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid account_id",
                        "message", "Account not found with id: " + accountId
                ));
            }
        }

        ExpenseResponse response = expenseService.createExpense(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/budgets")
    public ResponseEntity<?> getBudgets(
            @RequestParam(name = "date", required = false) String date
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();

        // Parse date parameter (YYYY-MM), default to current month.
        // Invalid format must produce 400, not propagate as 500.
        LocalDate referenceDate;
        try {
            referenceDate = parseYearMonthOrDefault(date);
        } catch (ResponseStatusException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid date format. Expected YYYY-MM, got: " + (date != null ? date : "")
            ));
        }

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

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard(
            @RequestParam(name = "date", required = false) String dateParam
    ) {
        UUID userId = SecurityUtils.getCurrentUserId();

        // Parse date parameter (YYYY-MM), default to current month
        LocalDate referenceDate;
        try {
            referenceDate = parseYearMonthOrDefault(dateParam);
        } catch (ResponseStatusException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid date format. Expected YYYY-MM, got: " + (dateParam != null ? dateParam : "")
            ));
        }
        YearMonth yearMonth = YearMonth.from(referenceDate);

        log.info("Webhook dashboard request for userId={}, month={}", userId, yearMonth);

        // Fetch expenses for the month
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        List<ExpenseResponse> expenses = expenseService.getExpenses(monthStart, monthEnd, null, userId);

        // Total spent and count
        BigDecimal totalSpent = expenses.stream()
                .map(ExpenseResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Category breakdown (category -> total + count)
        Map<String, List<ExpenseResponse>> byCategory = expenses.stream()
                .collect(Collectors.groupingBy(ExpenseResponse::getCategory));

        Map<String, CategoryEntry> categoryBreakdown = new LinkedHashMap<>();
        for (Map.Entry<String, List<ExpenseResponse>> entry : byCategory.entrySet()) {
            BigDecimal catTotal = entry.getValue().stream()
                    .map(ExpenseResponse::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            categoryBreakdown.put(entry.getKey(), CategoryEntry.builder()
                    .total(catTotal)
                    .count(entry.getValue().size())
                    .build());
        }

        // Top 5 merchants
        Map<String, List<ExpenseResponse>> byMerchant = expenses.stream()
                .collect(Collectors.groupingBy(ExpenseResponse::getMerchant));

        List<MerchantEntry> topMerchants = byMerchant.entrySet().stream()
                .map(entry -> {
                    BigDecimal merchantTotal = entry.getValue().stream()
                            .map(ExpenseResponse::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return MerchantEntry.builder()
                            .merchant(entry.getKey())
                            .total(merchantTotal)
                            .count(entry.getValue().size())
                            .build();
                })
                .sorted(Comparator.comparing(MerchantEntry::getTotal).reversed())
                .limit(5)
                .collect(Collectors.toList());

        // Budget status for this month
        WebhookBudgetsResponse budgetStatus = budgetService.getWebhookBudgets(userId, referenceDate);

        // Account balances
        List<Account> accounts = accountRepository.findByUserIdOrderByCreatedAtAsc(userId);
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalDebt = BigDecimal.ZERO;
        List<AccountBalanceEntry> accountBalances = new ArrayList<>();

        for (Account account : accounts) {
            AccountBalanceEntry entry = AccountBalanceEntry.builder()
                    .id(account.getId())
                    .name(account.getName())
                    .balance(account.getBalance())
                    .type(account.getType())
                    .build();
            accountBalances.add(entry);

            if (account.getType() == AccountType.CREDIT) {
                totalDebt = totalDebt.add(account.getBalance());
            } else {
                totalAssets = totalAssets.add(account.getBalance());
            }
        }

        BigDecimal netWorth = totalAssets.subtract(totalDebt);

        WebhookDashboardResponse response = WebhookDashboardResponse.builder()
                .totalSpent(totalSpent)
                .transactionCount(expenses.size())
                .categoryBreakdown(categoryBreakdown)
                .topMerchants(topMerchants)
                .budgetStatus(budgetStatus)
                .netWorth(netWorth)
                .totalAssets(totalAssets)
                .totalDebt(totalDebt)
                .accountBalances(accountBalances)
                .build();

        return ResponseEntity.ok(response);
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
