package com.expensetracker.service;

import com.expensetracker.dto.IncomeRequest;
import com.expensetracker.dto.IncomeResponse;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.Income;
import com.expensetracker.entity.User;
import com.expensetracker.exception.IncomeNotFoundException;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.IncomeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final EntityManager entityManager;

    @Transactional
    public IncomeResponse createIncome(IncomeRequest request, UUID userId) {
        Account account = resolveAccountIfProvided(request.getAccountId(), userId);

        User userRef = entityManager.getReference(User.class, userId);

        Income income = Income.builder()
                .name(request.getName())
                .type(request.getType())
                .category(request.getCategory())
                .amount(request.getAmount())
                .accountId(request.getAccountId())
                .user(userRef)
                .timestamp(resolveTimestamp(request))
                .notes(request.getNotes())
                .build();

        Income saved = incomeRepository.save(income);

        // Auto-apply the amount to the account balance if accountId is
        // provided. Sign is account-type aware: money flowing into a CREDIT
        // account pays down debt (balance shrinks), mirroring the transfer
        // convention; real-money accounts simply grow.
        if (account != null) {
            accountService.adjustBalance(account.getId(), incomeDelta(account, request.getAmount()));
        }

        log.info("Income created: id={}, name='{}', amount={}, accountId={}",
                saved.getId(), saved.getName(), saved.getAmount(), saved.getAccountId());
        return IncomeResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<IncomeResponse> getIncomes(UUID userId) {
        return incomeRepository.findByUserIdOrderByTimestampDesc(userId)
                .stream()
                .map(IncomeResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public IncomeResponse getIncomeById(UUID id, UUID userId) {
        Income income = incomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IncomeNotFoundException(id));
        return IncomeResponse.fromEntity(income);
    }

    @Transactional
    public IncomeResponse updateIncome(UUID id, IncomeRequest request, UUID userId) {
        Income income = incomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IncomeNotFoundException(id));

        Account newAccount = resolveAccountIfProvided(request.getAccountId(), userId);

        // Reverse old account balance adjustment before updating
        reverseIncomeDeltaIfPossible(income);

        income.setName(request.getName());
        income.setType(request.getType());
        income.setCategory(request.getCategory());
        income.setAmount(request.getAmount());
        income.setAccountId(request.getAccountId());
        income.setTimestamp(resolveTimestamp(request));
        if (request.getNotes() != null) {
            income.setNotes(request.getNotes());
        }

        Income saved = incomeRepository.save(income);

        // Apply new account balance adjustment (account-type-aware sign)
        if (newAccount != null) {
            accountService.adjustBalance(newAccount.getId(), incomeDelta(newAccount, request.getAmount()));
        }

        log.info("Income updated: id={}, name='{}', amount={}, accountId={}",
                saved.getId(), saved.getName(), saved.getAmount(), saved.getAccountId());
        return IncomeResponse.fromEntity(saved);
    }

    @Transactional
    public IncomeResponse deleteIncome(UUID id, UUID userId) {
        Income income = incomeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IncomeNotFoundException(id));

        // Reverse account balance adjustment
        reverseIncomeDeltaIfPossible(income);

        incomeRepository.delete(income);
        log.info("Income deleted: id={}, name='{}', amount={}", id, income.getName(), income.getAmount());
        return IncomeResponse.fromEntity(income);
    }

    /**
     * Resolves an optional accountId to the caller's account, or returns
     * {@code null} when no accountId was supplied.
     *
     * @throws ResponseStatusException with 404 status if the account is not
     *         found or belongs to another user
     */
    private Account resolveAccountIfProvided(UUID accountId, UUID userId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found with id: " + accountId
                ));
    }

    /**
     * Sign convention for incomes, matching {@code TransferService}'s matrix:
     * money into a real account grows the balance (+amount); money into a
     * CREDIT account pays down outstanding debt (−amount).
     */
    private BigDecimal incomeDelta(Account account, BigDecimal amount) {
        return account.getType() == AccountType.CREDIT ? amount.negate() : amount;
    }

    /**
     * Reverses the balance effect this income had when it was created.
     * Accounts are not FK-linked from incomes, so the referenced account may
     * have been deleted since; in that case the reversal is skipped (with a
     * warning) instead of failing the whole update/delete with a 404.
     */
    private void reverseIncomeDeltaIfPossible(Income income) {
        if (income.getAccountId() == null) {
            return;
        }
        accountRepository.findById(income.getAccountId()).ifPresentOrElse(
                account -> accountService.adjustBalance(
                        account.getId(), incomeDelta(account, income.getAmount()).negate()),
                () -> log.warn("Skipping balance reversal for income {}: account {} no longer exists",
                        income.getId(), income.getAccountId()));
    }

    private LocalDateTime resolveTimestamp(IncomeRequest request) {
        return request.getTimestamp() != null ? request.getTimestamp() : LocalDateTime.now();
    }
}
