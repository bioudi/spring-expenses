package com.expensetracker.service;

import com.expensetracker.config.ExpenseCategory;
import com.expensetracker.config.RecurrenceFrequency;
import com.expensetracker.dto.RecurringExpenseRequest;
import com.expensetracker.dto.RecurringExpenseResponse;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.RecurringExpense;
import com.expensetracker.entity.User;
import com.expensetracker.exception.AccountNotFoundException;
import com.expensetracker.exception.ExpenseNotFoundException;
import com.expensetracker.exception.InvalidCategoryException;
import com.expensetracker.exception.RecurringExpenseNotFoundException;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.RecurringExpenseRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringExpenseService {

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final ExpenseRepository expenseRepository;
    private final AccountRepository accountRepository;
    private final CategorizationService categorizationService;
    private final EntityManager entityManager;

    private static final String DEFAULT_CATEGORY = "Uncategorized";

    @Transactional
    public RecurringExpenseResponse createRecurringExpense(RecurringExpenseRequest request, UUID userId) {
        String category = resolveCategory(request.getCategory(), request.getMerchant(), userId);

        User userRef = entityManager.getReference(User.class, userId);
        Account account = resolveAccount(request.getAccountId(), userId);

        LocalDate firstOccurrence = computeFirstOccurrence(request);

        RecurringExpense recurring = RecurringExpense.builder()
                .amount(request.getAmount())
                .category(category)
                .merchant(request.getMerchant())
                .notes(request.getNotes())
                .frequency(request.getFrequency())
                .dayOfWeek(request.getDayOfWeek())
                .dayOfMonth(request.getDayOfMonth())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .nextOccurrence(firstOccurrence)
                .active(true)
                .user(userRef)
                .account(account)
                .build();

        RecurringExpense saved = recurringExpenseRepository.save(recurring);
        log.info("Created recurring expense {}: merchant='{}', frequency={}", saved.getId(), saved.getMerchant(), saved.getFrequency());

        // Create the first expense immediately if the first occurrence is today or in the past
        if (!firstOccurrence.isAfter(LocalDate.now())) {
            createExpenseFromTemplate(saved);
            saved.setNextOccurrence(computeNextOccurrence(firstOccurrence, saved));
            recurringExpenseRepository.save(saved);
        }

        return RecurringExpenseResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> getRecurringExpenses(UUID userId) {
        return recurringExpenseRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(RecurringExpenseResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RecurringExpenseResponse getRecurringExpenseById(UUID id, UUID userId) {
        RecurringExpense recurring = recurringExpenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RecurringExpenseNotFoundException(id));
        return RecurringExpenseResponse.fromEntity(recurring);
    }

    @Transactional
    public RecurringExpenseResponse updateRecurringExpense(UUID id, RecurringExpenseRequest request, UUID userId) {
        RecurringExpense recurring = recurringExpenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RecurringExpenseNotFoundException(id));

        String category = resolveCategory(request.getCategory(), request.getMerchant(), userId);
        Account account = resolveAccount(request.getAccountId(), userId);

        recurring.setAmount(request.getAmount());
        recurring.setCategory(category);
        recurring.setMerchant(request.getMerchant());
        recurring.setNotes(request.getNotes());
        recurring.setFrequency(request.getFrequency());
        recurring.setDayOfWeek(request.getDayOfWeek());
        recurring.setDayOfMonth(request.getDayOfMonth());
        recurring.setStartDate(request.getStartDate());
        recurring.setEndDate(request.getEndDate());
        recurring.setAccount(account);

        // Recompute next occurrence
        LocalDate nextOcc = computeFirstOccurrence(request);
        if (nextOcc.isBefore(LocalDate.now())) {
            nextOcc = computeNextOccurrenceFromToday(recurring);
        }
        recurring.setNextOccurrence(nextOcc);

        RecurringExpense saved = recurringExpenseRepository.save(recurring);
        log.info("Updated recurring expense {}: merchant='{}', frequency={}", id, saved.getMerchant(), saved.getFrequency());
        return RecurringExpenseResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteRecurringExpense(UUID id, UUID userId) {
        RecurringExpense recurring = recurringExpenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RecurringExpenseNotFoundException(id));
        recurringExpenseRepository.delete(recurring);
        log.info("Deleted recurring expense {}: merchant='{}'", id, recurring.getMerchant());
    }

    @Transactional
    public RecurringExpenseResponse toggleRecurringExpense(UUID id, UUID userId) {
        RecurringExpense recurring = recurringExpenseRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RecurringExpenseNotFoundException(id));

        recurring.setActive(!recurring.isActive());

        if (recurring.isActive()) {
            // On resume, recompute nextOccurrence from today
            recurring.setNextOccurrence(computeNextOccurrenceFromToday(recurring));
        }

        RecurringExpense saved = recurringExpenseRepository.save(recurring);
        log.info("Toggled recurring expense {} to active={}", id, saved.isActive());
        return RecurringExpenseResponse.fromEntity(saved);
    }

    @Transactional
    public RecurringExpenseResponse createFromExpense(UUID expenseId, RecurringExpenseRequest recurrenceFields, UUID userId) {
        Expense expense = expenseRepository.findByIdAndUserId(expenseId, userId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        // Prefer the accountId supplied in the request; otherwise inherit from
        // the source expense so the recurring template reflects how the user
        // was already paying for it.
        UUID accountId = recurrenceFields.getAccountId() != null
                ? recurrenceFields.getAccountId()
                : (expense.getAccount() != null ? expense.getAccount().getId() : null);

        RecurringExpenseRequest request = RecurringExpenseRequest.builder()
                .amount(expense.getAmount())
                .merchant(expense.getMerchant())
                .category(expense.getCategory())
                .notes(expense.getNotes())
                .frequency(recurrenceFields.getFrequency())
                .dayOfWeek(recurrenceFields.getDayOfWeek())
                .dayOfMonth(recurrenceFields.getDayOfMonth())
                .startDate(recurrenceFields.getStartDate())
                .endDate(recurrenceFields.getEndDate())
                .accountId(accountId)
                .build();

        RecurringExpenseResponse response = createRecurringExpense(request, userId);

        // Link the original expense to the recurring template
        expense.setRecurringExpenseId(response.getId());
        expenseRepository.save(expense);

        return response;
    }

    @Scheduled(cron = "0 30 8 * * ?")
    @Transactional
    public void processRecurringExpenses() {
        LocalDate today = LocalDate.now();
        List<RecurringExpense> dueExpenses = recurringExpenseRepository.findDueRecurringExpenses(today);

        log.info("Processing {} due recurring expenses", dueExpenses.size());

        for (RecurringExpense recurring : dueExpenses) {
            try {
                // Handle missed days by creating expenses for each missed occurrence
                while (!recurring.getNextOccurrence().isAfter(today)) {
                    createExpenseFromTemplate(recurring);
                    LocalDate next = computeNextOccurrence(recurring.getNextOccurrence(), recurring);
                    recurring.setNextOccurrence(next);

                    // Check if we've passed the end date
                    if (recurring.getEndDate() != null && next.isAfter(recurring.getEndDate())) {
                        recurring.setActive(false);
                        log.info("Deactivated recurring expense {} (past end date)", recurring.getId());
                        break;
                    }
                }

                recurringExpenseRepository.save(recurring);
            } catch (Exception e) {
                log.error("Failed to process recurring expense {}: {}", recurring.getId(), e.getMessage(), e);
            }
        }
    }

    private void createExpenseFromTemplate(RecurringExpense template) {
        Expense expense = Expense.builder()
                .amount(template.getAmount())
                .category(template.getCategory())
                .merchant(template.getMerchant())
                .notes(template.getNotes())
                .timestamp(template.getNextOccurrence().atStartOfDay())
                .recurringExpenseId(template.getId())
                .user(template.getUser())
                .account(template.getAccount())
                .build();

        expenseRepository.save(expense);
        log.info("Created expense from recurring template {}: merchant='{}', amount={}, date={}",
                template.getId(), template.getMerchant(), template.getAmount(), template.getNextOccurrence());
    }

    private LocalDate computeFirstOccurrence(RecurringExpenseRequest request) {
        LocalDate start = request.getStartDate();

        switch (request.getFrequency()) {
            case WEEKLY:
            case BI_WEEKLY:
                if (request.getDayOfWeek() != null) {
                    LocalDate adjusted = start.with(TemporalAdjusters.nextOrSame(request.getDayOfWeek()));
                    return adjusted;
                }
                return start;
            case MONTHLY:
                if (request.getDayOfMonth() != null) {
                    int targetDay = Math.min(request.getDayOfMonth(), start.lengthOfMonth());
                    LocalDate adjusted = start.withDayOfMonth(targetDay);
                    if (adjusted.isBefore(start)) {
                        adjusted = start.plusMonths(1);
                        targetDay = Math.min(request.getDayOfMonth(), adjusted.lengthOfMonth());
                        adjusted = adjusted.withDayOfMonth(targetDay);
                    }
                    return adjusted;
                }
                return start;
            default:
                return start;
        }
    }

    private LocalDate computeNextOccurrence(LocalDate current, RecurringExpense recurring) {
        switch (recurring.getFrequency()) {
            case DAILY:
                return current.plusDays(1);
            case WEEKLY:
                return current.plusWeeks(1);
            case BI_WEEKLY:
                return current.plusWeeks(2);
            case MONTHLY:
                LocalDate next = current.plusMonths(1);
                if (recurring.getDayOfMonth() != null) {
                    int targetDay = Math.min(recurring.getDayOfMonth(), next.lengthOfMonth());
                    next = next.withDayOfMonth(targetDay);
                }
                return next;
            default:
                return current.plusDays(1);
        }
    }

    private LocalDate computeNextOccurrenceFromToday(RecurringExpense recurring) {
        LocalDate today = LocalDate.now();

        switch (recurring.getFrequency()) {
            case DAILY:
                return today;
            case WEEKLY:
                if (recurring.getDayOfWeek() != null) {
                    return today.with(TemporalAdjusters.nextOrSame(recurring.getDayOfWeek()));
                }
                return today;
            case BI_WEEKLY:
                if (recurring.getDayOfWeek() != null) {
                    return today.with(TemporalAdjusters.nextOrSame(recurring.getDayOfWeek()));
                }
                return today;
            case MONTHLY:
                if (recurring.getDayOfMonth() != null) {
                    int targetDay = Math.min(recurring.getDayOfMonth(), today.lengthOfMonth());
                    LocalDate target = today.withDayOfMonth(targetDay);
                    if (target.isBefore(today)) {
                        LocalDate nextMonth = today.plusMonths(1);
                        targetDay = Math.min(recurring.getDayOfMonth(), nextMonth.lengthOfMonth());
                        target = nextMonth.withDayOfMonth(targetDay);
                    }
                    return target;
                }
                return today;
            default:
                return today;
        }
    }

    private String resolveCategory(String category, String merchant, UUID userId) {
        if (category != null && !category.isBlank()) {
            if (!ExpenseCategory.isValid(category)) {
                throw new InvalidCategoryException(
                        "Invalid category: " + category + ". Valid categories are: " +
                                String.join(", ", ExpenseCategory.VALID_CATEGORIES)
                );
            }
            return category;
        }

        String aiCategory = categorizationService.categorize(merchant, userId);
        return aiCategory != null ? aiCategory : DEFAULT_CATEGORY;
    }

    /**
     * Resolves an accountId from a recurring-expense request into the managed
     * {@link Account} entity, scoped to the calling user. Returns {@code null}
     * when no accountId is supplied. Throws {@link AccountNotFoundException}
     * (404) when the supplied id does not exist or belongs to a different user,
     * mirroring {@code ExpenseService}'s behavior so the recurring page and the
     * expense page agree on what "unknown account" means.
     */
    private Account resolveAccount(UUID accountId, UUID userId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
