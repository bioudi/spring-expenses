package com.expensetracker.service;

import com.expensetracker.dto.RecurringIncomeRequest;
import com.expensetracker.dto.RecurringIncomeResponse;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.Income;
import com.expensetracker.entity.IncomeCategory;
import com.expensetracker.entity.IncomeType;
import com.expensetracker.entity.RecurringIncome;
import com.expensetracker.entity.User;
import com.expensetracker.exception.AccountNotFoundException;
import com.expensetracker.exception.IncomeNotFoundException;
import com.expensetracker.exception.RecurringIncomeNotFoundException;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.IncomeRepository;
import com.expensetracker.repository.RecurringIncomeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD + scheduled materialisation for {@link RecurringIncome} templates,
 * mirroring {@link RecurringExpenseService} so that the two recurring engines
 * stay in lock-step on recurrence math, account-validation rules, and the
 * end-of-day materialisation job.
 *
 * <p>Recurrence math (first occurrence, next occurrence, "from today") is
 * duplicated rather than shared because the inputs differ (income templates
 * carry {@code name}/{@code type}/{@code category} instead of
 * {@code merchant}/{@code category}/{@code amount} semantics), and keeping
 * the two services symmetrical lets future refactors recognise divergences
 * quickly. If a third template kind lands, lift the recurrence helpers into
 * a {@code RecurrenceCalculator} utility — for two, copy-paste is cheaper.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringIncomeService {

    private final RecurringIncomeRepository recurringIncomeRepository;
    private final IncomeRepository incomeRepository;
    private final AccountRepository accountRepository;
    private final EntityManager entityManager;

    @Transactional
    public RecurringIncomeResponse createRecurringIncome(RecurringIncomeRequest request, UUID userId) {
        User userRef = entityManager.getReference(User.class, userId);
        Account account = resolveAccount(request.getAccountId(), userId);

        LocalDate firstOccurrence = computeFirstOccurrence(request);

        RecurringIncome recurring = RecurringIncome.builder()
                .name(request.getName())
                .type(request.getType())
                .category(request.getCategory())
                .amount(request.getAmount())
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

        RecurringIncome saved = recurringIncomeRepository.save(recurring);
        log.info("Created recurring income {}: name='{}', frequency={}",
                saved.getId(), saved.getName(), saved.getFrequency());

        // Materialise the first income immediately if the first occurrence is
        // today or in the past — mirrors RecurringExpenseService so users who
        // backdate a template see the leading income row today, not tomorrow.
        if (!firstOccurrence.isAfter(LocalDate.now())) {
            createIncomeFromTemplate(saved);
            saved.setNextOccurrence(computeNextOccurrence(firstOccurrence, saved));
            recurringIncomeRepository.save(saved);
        }

        return RecurringIncomeResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<RecurringIncomeResponse> getRecurringIncomes(UUID userId) {
        return recurringIncomeRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(RecurringIncomeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RecurringIncomeResponse getRecurringIncomeById(UUID id, UUID userId) {
        RecurringIncome recurring = recurringIncomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RecurringIncomeNotFoundException(id));
        return RecurringIncomeResponse.fromEntity(recurring);
    }

    @Transactional
    public RecurringIncomeResponse updateRecurringIncome(UUID id, RecurringIncomeRequest request, UUID userId) {
        RecurringIncome recurring = recurringIncomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RecurringIncomeNotFoundException(id));

        Account account = resolveAccount(request.getAccountId(), userId);

        recurring.setName(request.getName());
        recurring.setType(request.getType());
        recurring.setCategory(request.getCategory());
        recurring.setAmount(request.getAmount());
        recurring.setNotes(request.getNotes());
        recurring.setFrequency(request.getFrequency());
        recurring.setDayOfWeek(request.getDayOfWeek());
        recurring.setDayOfMonth(request.getDayOfMonth());
        recurring.setStartDate(request.getStartDate());
        recurring.setEndDate(request.getEndDate());
        recurring.setAccount(account);

        // Recompute next occurrence — same rule as create: backdated edits
        // shouldn't strand a leading row outside its recurrence window.
        LocalDate nextOcc = computeFirstOccurrence(request);
        if (nextOcc.isBefore(LocalDate.now())) {
            nextOcc = computeNextOccurrenceFromToday(recurring);
        }
        recurring.setNextOccurrence(nextOcc);

        RecurringIncome saved = recurringIncomeRepository.save(recurring);
        log.info("Updated recurring income {}: name='{}', frequency={}",
                id, saved.getName(), saved.getFrequency());
        return RecurringIncomeResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteRecurringIncome(UUID id, UUID userId) {
        RecurringIncome recurring = recurringIncomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RecurringIncomeNotFoundException(id));
        recurringIncomeRepository.delete(recurring);
        log.info("Deleted recurring income {}: name='{}'", id, recurring.getName());
        // Materialised Incomes are NOT reversed on delete — once the row hit
        // the user's ledger, the deposit stands. Matches the recurring-expense
        // contract where deleting a template leaves already-materialised
        // expenses in place.
    }

    @Transactional
    public RecurringIncomeResponse toggleRecurringIncome(UUID id, UUID userId) {
        RecurringIncome recurring = recurringIncomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RecurringIncomeNotFoundException(id));

        recurring.setActive(!recurring.isActive());

        if (recurring.isActive()) {
            // On resume, recompute nextOccurrence from today so the user
            // doesn't wait a full cycle to receive their first deposit back.
            recurring.setNextOccurrence(computeNextOccurrenceFromToday(recurring));
        }

        RecurringIncome saved = recurringIncomeRepository.save(recurring);
        log.info("Toggled recurring income {} to active={}", id, saved.isActive());
        return RecurringIncomeResponse.fromEntity(saved);
    }

    @Transactional
    public RecurringIncomeResponse createFromIncome(UUID incomeId, RecurringIncomeRequest recurrenceFields, UUID userId) {
        Income income = incomeRepository.findByIdAndUserId(incomeId, userId)
                .orElseThrow(() -> new IncomeNotFoundException(incomeId));

        // Prefer the accountId supplied in the request; otherwise inherit
        // from the source income so the recurring template reflects how the
        // user was already receiving their money.
        UUID accountId = recurrenceFields.getAccountId() != null
                ? recurrenceFields.getAccountId()
                : income.getAccountId();

        RecurringIncomeRequest request = RecurringIncomeRequest.builder()
                .name(income.getName())
                .type(income.getType() != null ? income.getType() : IncomeType.TRANSFER)
                .category(income.getCategory() != null ? income.getCategory() : IncomeCategory.PAYCHECK)
                .amount(income.getAmount())
                .notes(income.getNotes())
                .frequency(recurrenceFields.getFrequency())
                .dayOfWeek(recurrenceFields.getDayOfWeek())
                .dayOfMonth(recurrenceFields.getDayOfMonth())
                .startDate(recurrenceFields.getStartDate())
                .endDate(recurrenceFields.getEndDate())
                .accountId(accountId)
                .build();

        return createRecurringIncome(request, userId);
        // Note: unlike RecurringExpenseService.createFromExpense, we don't
        // link the source Income.recurringIncomeId back — the Income entity
        // doesn't carry that column today, and adding it would be out of
        // scope for this PR.
    }

    @Scheduled(cron = "0 30 8 * * ?")
    @Transactional
    public void processRecurringIncomes() {
        LocalDate today = LocalDate.now();
        List<RecurringIncome> dueIncomes = recurringIncomeRepository.findDueRecurringIncomes(today);

        log.info("Processing {} due recurring incomes", dueIncomes.size());

        for (RecurringIncome recurring : dueIncomes) {
            try {
                // Handle missed days by creating incomes for each missed occurrence.
                while (!recurring.getNextOccurrence().isAfter(today)) {
                    createIncomeFromTemplate(recurring);
                    LocalDate next = computeNextOccurrence(recurring.getNextOccurrence(), recurring);
                    recurring.setNextOccurrence(next);

                    if (recurring.getEndDate() != null && next.isAfter(recurring.getEndDate())) {
                        recurring.setActive(false);
                        log.info("Deactivated recurring income {} (past end date)", recurring.getId());
                        break;
                    }
                }

                recurringIncomeRepository.save(recurring);
            } catch (Exception e) {
                log.error("Failed to process recurring income {}: {}", recurring.getId(), e.getMessage(), e);
            }
        }
    }

    private void createIncomeFromTemplate(RecurringIncome template) {
        Income income = Income.builder()
                .name(template.getName())
                .type(template.getType())
                .category(template.getCategory())
                .amount(template.getAmount())
                .notes(template.getNotes())
                .user(template.getUser())
                .accountId(template.getAccount() != null ? template.getAccount().getId() : null)
                .timestamp(template.getNextOccurrence().atStartOfDay())
                .build();

        incomeRepository.save(income);

        // Adjust the linked account balance the same way IncomeService does
        // for ad-hoc creates — only if the template carried an account.
        // Sign is account-type aware: money into a CREDIT account pays down
        // debt (−amount), money into a real account grows it (+amount).
        Account linkedAccount = template.getAccount();
        if (linkedAccount != null) {
            BigDecimal delta = linkedAccount.getType() == AccountType.CREDIT
                    ? template.getAmount().negate()
                    : template.getAmount();
            accountRepository.addToBalance(linkedAccount.getId(), delta);
        }

        log.info("Created income from recurring template {}: name='{}', amount={}, date={}",
                template.getId(), template.getName(), template.getAmount(), template.getNextOccurrence());
    }

    private LocalDate computeFirstOccurrence(RecurringIncomeRequest request) {
        LocalDate start = request.getStartDate();

        switch (request.getFrequency()) {
            case WEEKLY:
            case BI_WEEKLY:
                if (request.getDayOfWeek() != null) {
                    return start.with(TemporalAdjusters.nextOrSame(request.getDayOfWeek()));
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

    private LocalDate computeNextOccurrence(LocalDate current, RecurringIncome recurring) {
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

    private LocalDate computeNextOccurrenceFromToday(RecurringIncome recurring) {
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

    /**
     * Resolves an accountId from a recurring-income request into the managed
     * {@link Account} entity, scoped to the calling user. Returns {@code null}
     * when no accountId is supplied. Throws {@link AccountNotFoundException}
     * (404) when the supplied id does not exist or belongs to a different
     * user, mirroring {@link RecurringExpenseService#resolveAccount} so the
     * recurring-income and recurring-expense controllers agree on what
     * "unknown account" means.
     */
    private Account resolveAccount(UUID accountId, UUID userId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
