package com.expensetracker.service;

import com.expensetracker.config.ExpenseCategory;
import com.expensetracker.dto.*;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ExpenseNotFoundException;
import com.expensetracker.exception.InvalidCategoryException;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.ExpenseRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategorizationService categorizationService;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final EntityManager entityManager;

    private static final String DEFAULT_CATEGORY = "Uncategorized";

    @Transactional
    public ExpenseResponse createExpense(ExpenseRequest request, UUID userId) {
        String category;

        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            category = request.getCategory();
            validateCategory(category);
            log.info("Expense for merchant '{}' — using provided category: '{}'", request.getMerchant(), category);
        } else {
            log.info("No category provided for merchant '{}', requesting AI categorization...", request.getMerchant());
            String aiCategory = categorizationService.categorize(request.getMerchant(), userId);
            category = aiCategory != null ? aiCategory : DEFAULT_CATEGORY;
            log.info("Expense for merchant '{}' — final category: '{}' (source: {})",
                    request.getMerchant(), category, aiCategory != null ? "Claude AI" : "default fallback");
        }

        // Use "name" field as notes if notes is empty
        String notes = request.getNotes();
        if ((notes == null || notes.isBlank()) && request.getName() != null) {
            notes = request.getName();
        }

        // Use "card" field if "cardName" is empty
        String cardName = request.getCardName();
        if ((cardName == null || cardName.isBlank()) && request.getCard() != null) {
            cardName = request.getCard();
        }

        // Determine payment method: default to "Card" if cardName is present, otherwise use provided or default
        String paymentMethod = request.getPaymentMethod();
        if (paymentMethod == null || paymentMethod.isBlank()) {
            paymentMethod = (cardName != null && !cardName.isBlank()) ? "Card" : "Cash";
        }

        User userRef = entityManager.getReference(User.class, userId);

        // Fetch account if accountId provided (for both reference and balance deduction)
        Account account = null;
        UUID accountId = request.getAccountId();
        if (accountId != null) {
            account = accountRepository.findByIdAndUserId(accountId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Account not found with id: " + accountId
                    ));
        }

        Expense expense = Expense.builder()
                .amount(request.getAmount())
                .category(category)
                .merchant(request.getMerchant())
                .paymentMethod(paymentMethod)
                .cardName(cardName)
                .timestamp(resolveTimestampForCreate(request))
                .notes(notes)
                .account(account)
                .user(userRef)
                .build();

        Expense saved = expenseRepository.save(expense);

        // Atomically deduct from the linked account balance if one was provided.
        // Uses a single SQL UPDATE guarded by "balance >= amount" so concurrent
        // POSTs cannot lose updates (the original bug) and so an over-the-balance
        // expense is rejected with InsufficientFundsException (422) instead of
        // silently driving the balance negative.
        if (account != null) {
            BigDecimal newBalance = accountService.adjustBalance(
                    accountId, request.getAmount().negate());
            if (account.getType() == AccountType.CREDIT) {
                log.info("Paid off ${} from CREDIT account '{}' — new outstanding balance: {}",
                        request.getAmount(), account.getName(), newBalance);
            } else {
                log.info("Deducted ${} from account '{}' — new balance: {}",
                        request.getAmount(), account.getName(), newBalance);
            }
        }

        return ExpenseResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(LocalDate startDate, LocalDate endDate, String category, UUID userId) {
        if (category != null && !category.isBlank()) {
            validateCategory(category);
        }

        List<Expense> expenses;

        if (startDate != null && endDate != null) {
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.plusDays(1).atStartOfDay();

            if (category != null && !category.isBlank()) {
                expenses = expenseRepository.findByUserIdAndDateRangeAndCategory(userId, start, end, category);
            } else {
                expenses = expenseRepository.findByUserIdAndDateRange(userId, start, end);
            }
        } else if (category != null && !category.isBlank()) {
            expenses = expenseRepository.findByUserIdAndCategory(userId, category);
        } else {
            expenses = expenseRepository.findAllByUserIdOrderByTimestampDesc(userId);
        }

        return expenses.stream()
                .map(ExpenseResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExpenseSummary getSummary(UUID userId) {
        List<Expense> allExpenses = expenseRepository.findAllByUserIdOrderByTimestampDesc(userId);

        if (allExpenses.isEmpty()) {
            return ExpenseSummary.builder()
                    .totalSpent(BigDecimal.ZERO)
                    .transactionCount(0)
                    .categoryBreakdown(Map.of())
                    .build();
        }

        BigDecimal totalSpent = allExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<Expense>> byCategory = allExpenses.stream()
                .collect(Collectors.groupingBy(Expense::getCategory));

        Map<String, ExpenseSummary.CategoryBreakdown> breakdown = new LinkedHashMap<>();

        for (String cat : ExpenseCategory.VALID_CATEGORIES) {
            List<Expense> catExpenses = byCategory.getOrDefault(cat, List.of());
            if (!catExpenses.isEmpty()) {
                BigDecimal catTotal = catExpenses.stream()
                        .map(Expense::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal percentage = catTotal
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalSpent, 2, RoundingMode.HALF_UP);

                breakdown.put(cat, ExpenseSummary.CategoryBreakdown.builder()
                        .total(catTotal)
                        .count(catExpenses.size())
                        .percentage(percentage)
                        .build());
            }
        }

        return ExpenseSummary.builder()
                .totalSpent(totalSpent)
                .transactionCount(allExpenses.size())
                .categoryBreakdown(breakdown)
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(LocalDate referenceDate, UUID userId) {
        LocalDate ref = referenceDate != null ? referenceDate : LocalDate.now();

        // Today: single day
        List<Expense> todayExpenses = expenseRepository.findByUserIdAndDateRange(
                userId, ref.atStartOfDay(), ref.plusDays(1).atStartOfDay());

        // Week: Monday → Sunday containing the reference date
        LocalDate weekStart = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = ref.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        // Month: 1st → last day of the reference month
        LocalDate monthStart = ref.withDayOfMonth(1);
        LocalDate monthEnd = ref.with(TemporalAdjusters.lastDayOfMonth());

        // Year: Jan 1 → Dec 31 of the reference year
        LocalDate yearStart = ref.withDayOfYear(1);
        LocalDate yearEnd = ref.withMonth(12).withDayOfMonth(31);

        List<Expense> weekExpenses = expenseRepository.findByUserIdAndDateRange(
                userId, weekStart.atStartOfDay(), weekEnd.plusDays(1).atStartOfDay());
        List<Expense> monthExpenses = expenseRepository.findByUserIdAndDateRange(
                userId, monthStart.atStartOfDay(), monthEnd.plusDays(1).atStartOfDay());
        List<Expense> yearExpenses = expenseRepository.findByUserIdAndDateRange(
                userId, yearStart.atStartOfDay(), yearEnd.plusDays(1).atStartOfDay());

        // Account balances for net worth
        List<Account> accounts = accountRepository.findByUserIdOrderByCreatedAtAsc(userId);
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalDebt = BigDecimal.ZERO;
        List<DashboardResponse.AccountBalanceEntry> accountBalances = new ArrayList<>();
        for (Account account : accounts) {
            if (account.getType() == AccountType.CREDIT) {
                totalDebt = totalDebt.add(account.getBalance());
            } else {
                totalAssets = totalAssets.add(account.getBalance());
            }
            accountBalances.add(DashboardResponse.AccountBalanceEntry.builder()
                    .id(account.getId())
                    .name(account.getName())
                    .balance(account.getBalance())
                    .type(account.getType())
                    .build());
        }
        BigDecimal netWorth = totalAssets.subtract(totalDebt);

        return DashboardResponse.builder()
                .netWorth(netWorth)
                .totalAssets(totalAssets)
                .totalDebt(totalDebt)
                .accountBalances(accountBalances)
                .today(buildPeriodSummary(todayExpenses, ref, ref))
                .week(buildPeriodSummary(weekExpenses, weekStart, weekEnd))
                .month(buildPeriodSummary(monthExpenses, monthStart, monthEnd))
                .year(buildPeriodSummary(yearExpenses, yearStart, yearEnd))
                .build();
    }

    private DashboardResponse.PeriodSummary buildPeriodSummary(List<Expense> expenses, LocalDate start, LocalDate end) {
        if (expenses.isEmpty()) {
            List<DashboardResponse.DailySpending> emptyDays = new ArrayList<>();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                emptyDays.add(DashboardResponse.DailySpending.builder()
                        .date(d).total(BigDecimal.ZERO).count(0).build());
            }
            return DashboardResponse.PeriodSummary.builder()
                    .startDate(start)
                    .endDate(end)
                    .totalSpent(BigDecimal.ZERO)
                    .transactionCount(0)
                    .avgPerTransaction(BigDecimal.ZERO)
                    .categoryBreakdown(Map.of())
                    .topMerchants(List.of())
                    .dailySpending(emptyDays)
                    .build();
        }

        BigDecimal totalSpent = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = expenses.size();
        BigDecimal avg = totalSpent.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        Map<String, List<Expense>> byCategory = expenses.stream()
                .collect(Collectors.groupingBy(Expense::getCategory));
        Map<String, DashboardResponse.CategoryBreakdown> categoryBreakdown = new LinkedHashMap<>();

        for (String cat : ExpenseCategory.VALID_CATEGORIES) {
            List<Expense> catExpenses = byCategory.getOrDefault(cat, List.of());
            if (!catExpenses.isEmpty()) {
                BigDecimal catTotal = catExpenses.stream()
                        .map(Expense::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal percentage = catTotal
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalSpent, 2, RoundingMode.HALF_UP);
                BigDecimal catAvg = catTotal.divide(BigDecimal.valueOf(catExpenses.size()), 2, RoundingMode.HALF_UP);

                categoryBreakdown.put(cat, DashboardResponse.CategoryBreakdown.builder()
                        .total(catTotal)
                        .count(catExpenses.size())
                        .percentage(percentage)
                        .avgPerTransaction(catAvg)
                        .build());
            }
        }

        Map<String, List<Expense>> byMerchant = expenses.stream()
                .collect(Collectors.groupingBy(Expense::getMerchant));
        List<DashboardResponse.MerchantSummary> topMerchants = byMerchant.entrySet().stream()
                .map(entry -> {
                    BigDecimal merchantTotal = entry.getValue().stream()
                            .map(Expense::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return DashboardResponse.MerchantSummary.builder()
                            .merchant(entry.getKey())
                            .total(merchantTotal)
                            .count(entry.getValue().size())
                            .build();
                })
                .sorted(Comparator.comparing(DashboardResponse.MerchantSummary::getTotal).reversed())
                .limit(5)
                .collect(Collectors.toList());

        Map<LocalDate, List<Expense>> byDate = expenses.stream()
                .collect(Collectors.groupingBy(e -> e.getTimestamp().toLocalDate()));
        List<DashboardResponse.DailySpending> dailySpending = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            List<Expense> dayExpenses = byDate.getOrDefault(d, List.of());
            BigDecimal dayTotal = dayExpenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            dailySpending.add(DashboardResponse.DailySpending.builder()
                    .date(d)
                    .total(dayTotal)
                    .count(dayExpenses.size())
                    .build());
        }

        return DashboardResponse.PeriodSummary.builder()
                .startDate(start)
                .endDate(end)
                .totalSpent(totalSpent)
                .transactionCount(count)
                .avgPerTransaction(avg)
                .categoryBreakdown(categoryBreakdown)
                .topMerchants(topMerchants)
                .dailySpending(dailySpending)
                .build();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getExpenseById(UUID id, UUID userId) {
        Expense expense = expenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
        return ExpenseResponse.fromEntity(expense);
    }

    @Transactional
    public ExpenseResponse updateExpense(UUID id, ExpenseRequest request, UUID userId) {
        Expense expense = expenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ExpenseNotFoundException(id));

        BigDecimal oldAmount = expense.getAmount();
        Account oldAccount = expense.getAccount();
        UUID oldAccountId = oldAccount != null ? oldAccount.getId() : null;

        // Determine category
        String category;
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            category = request.getCategory();
            validateCategory(category);
        } else {
            String merchant = request.getMerchant() != null ? request.getMerchant() : expense.getMerchant();
            String aiCategory = categorizationService.categorize(merchant, userId);
            category = aiCategory != null ? aiCategory : DEFAULT_CATEGORY;
        }

        expense.setAmount(request.getAmount());
        expense.setCategory(category);
        if (request.getMerchant() != null) {
            expense.setMerchant(request.getMerchant());
        }

        if (request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank()) {
            expense.setPaymentMethod(request.getPaymentMethod());
        }

        String cardName = request.getCardName();
        if ((cardName == null || cardName.isBlank()) && request.getCard() != null) {
            cardName = request.getCard();
        }
        expense.setCardName(cardName);

        LocalDateTime resolvedTimestamp = resolveTimestampForUpdate(request);
        if (resolvedTimestamp != null) {
            expense.setTimestamp(resolvedTimestamp);
        }

        String notes = request.getNotes();
        if ((notes == null || notes.isBlank()) && request.getName() != null) {
            notes = request.getName();
        }
        expense.setNotes(notes);

        // Resolve new account reference
        Account newAccount = null;
        UUID newAccountId = request.getAccountId();
        if (newAccountId != null) {
            newAccount = accountRepository.findByIdAndUserId(newAccountId, userId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Account not found with id: " + newAccountId
                    ));
        }
        expense.setAccount(newAccount);

        Expense saved = expenseRepository.save(expense);

        // Adjust balances: restore old (atomic add), then deduct new (atomic
        // guarded subtract). Each call is its own single SQL UPDATE so
        // concurrent updaters cannot lose updates.
        BigDecimal newAmount = request.getAmount();

        // If old account exists, restore its balance by the old amount.
        // Restore = positive delta on the OLD account.
        if (oldAccountId != null) {
            accountService.adjustBalance(oldAccountId, oldAmount);
        }

        // If new account exists, deduct the new amount. Deduct = negative delta
        // on the NEW account. adjustBalance throws InsufficientFundsException
        // for non-CREDIT accounts when the post-deduction balance would go
        // negative; that exception propagates out and rolls the transaction.
        if (newAccount != null) {
            accountService.adjustBalance(newAccountId, newAmount.negate());
        }

        log.info("Updated expense {}: merchant='{}', category='{}', amount={}",
                id, saved.getMerchant(), saved.getCategory(), saved.getAmount());
        return ExpenseResponse.fromEntity(saved);
    }

    @Transactional
    public ExpenseResponse deleteExpense(UUID id, UUID userId) {
        Expense expense = expenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ExpenseNotFoundException(id));

        // Atomically restore the account balance before deleting the expense.
        // A single SQL UPDATE avoids the race where two concurrent deletes
        // both read the same pre-restore balance and only one actually adds
        // the amount back.
        Account linkedAccount = expense.getAccount();
        if (linkedAccount != null) {
            accountService.adjustBalance(linkedAccount.getId(), expense.getAmount());
            log.info("Restored ${} to account '{}' on expense deletion", expense.getAmount(), linkedAccount.getName());
        }

        expenseRepository.delete(expense);
        log.info("Deleted expense {}: merchant='{}', amount={}", id, expense.getMerchant(), expense.getAmount());
        return ExpenseResponse.fromEntity(expense);
    }

    private void validateCategory(String category) {
        if (!ExpenseCategory.isValid(category)) {
            throw new InvalidCategoryException(
                    "Invalid category: " + category + ". Valid categories are: " +
                            String.join(", ", ExpenseCategory.VALID_CATEGORIES)
            );
        }
    }

    /**
     * Validates that the provided accountId (if non-null) references an
     * existing account belonging to the current user.
     *
     * @throws ResponseStatusException with 404 status if the account is not found
     */
    private void validateAccountIfProvided(UUID accountId, UUID userId) {
        if (accountId == null) {
            return;
        }
        accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found with id: " + accountId
                ));
    }

    /**
     * Resolves the expense timestamp for a create request.
     * Priority: {@code request.date} (start-of-day) &gt; {@code request.timestamp} &gt; {@code now()}.
     * Always returns a non-null LocalDateTime.
     */
    private LocalDateTime resolveTimestampForCreate(ExpenseRequest request) {
        if (request.getDate() != null) {
            log.debug("Using provided date '{}' for new expense timestamp", request.getDate());
            return request.getDate().atStartOfDay();
        }
        if (request.getTimestamp() != null) {
            return request.getTimestamp();
        }
        return LocalDateTime.now();
    }

    /**
     * Resolves the expense timestamp for an update request.
     * Priority: {@code request.date} (start-of-day) &gt; {@code request.timestamp} &gt; {@code null}
     * (caller leaves the existing timestamp untouched).
     * Returns null when neither field was provided.
     */
    private LocalDateTime resolveTimestampForUpdate(ExpenseRequest request) {
        if (request.getDate() != null) {
            log.debug("Using provided date '{}' to update expense timestamp", request.getDate());
            return request.getDate().atStartOfDay();
        }
        return request.getTimestamp();
    }
}
