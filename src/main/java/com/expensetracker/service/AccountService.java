package com.expensetracker.service;

import com.expensetracker.dto.AccountRequest;
import com.expensetracker.dto.AccountResponse;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.User;
import com.expensetracker.exception.AccountHasLinkedExpensesException;
import com.expensetracker.exception.AccountNotFoundException;
import com.expensetracker.exception.InsufficientFundsException;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.ExpenseRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final ExpenseRepository expenseRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(UUID userId) {
        return accountRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(AccountResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(UUID id, UUID userId) {
        Account account = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return AccountResponse.fromEntity(account);
    }

    @Transactional
    public AccountResponse createAccount(AccountRequest request, UUID userId) {
        User userRef = entityManager.getReference(User.class, userId);

        Account account = Account.builder()
                .name(request.getName())
                .balance(request.getBalance() != null ? request.getBalance() : java.math.BigDecimal.ZERO)
                .type(request.getType())
                .user(userRef)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Created account '{}' ({}): {} for user {}", saved.getName(), saved.getType(), saved.getId(), userId);
        return AccountResponse.fromEntity(saved);
    }

    @Transactional
    public AccountResponse updateAccount(UUID id, AccountRequest request, UUID userId) {
        Account account = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AccountNotFoundException(id));

        account.setName(request.getName());
        if (request.getBalance() != null) {
            account.setBalance(request.getBalance());
        }
        account.setType(request.getType());

        Account saved = accountRepository.save(account);
        log.info("Updated account {}: name='{}', type={}, balance={}", id, saved.getName(), saved.getType(), saved.getBalance());
        return AccountResponse.fromEntity(saved);
    }

    @Transactional
    public AccountResponse deleteAccount(UUID id, UUID userId) {
        Account account = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AccountNotFoundException(id));

        // Reject deletion when expenses are still linked to this account. Without
        // this guard the database foreign-key constraint on expenses.account_id
        // throws a raw DataIntegrityViolationException, which would otherwise
        // surface as a 500. The 400 + count here covers the common case. The
        // {@code GlobalExceptionHandler} catches the rare race-condition FK
        // violation as a belt-and-braces fallback.
        long linkedExpenseCount = expenseRepository.countByAccountId(id);
        if (linkedExpenseCount > 0) {
            throw new AccountHasLinkedExpensesException(id, linkedExpenseCount);
        }

        accountRepository.delete(account);
        log.info("Deleted account {}: name='{}', type={}", id, account.getName(), account.getType());
        return AccountResponse.fromEntity(account);
    }

    /**
     * Atomically adjusts an account's balance by the given delta. The delta can be
     * positive (increase balance, e.g. income or expense delete-restore) or
     * negative (decrease balance, e.g. expense deduction).
     *
     * <p>This uses a single SQL {@code UPDATE} statement guarded by the
     * appropriate balance predicate, so concurrent callers cannot lose updates
     * the way a read-then-write pattern would. For non-{@link AccountType#CREDIT}
     * accounts, a negative delta is rejected if it would drive the balance
     * below zero (throws {@link InsufficientFundsException}). CREDIT accounts
     * can grow debt, so negative deltas are always allowed there.
     *
     * <p>Returns the account's balance after the adjustment.
     *
     * @param accountId the account to adjust (must exist)
     * @param delta     the amount to add (positive) or subtract (negative)
     * @return the account's balance after the adjustment
     * @throws AccountNotFoundException if no account exists with that id
     * @throws InsufficientFundsException if a negative delta on a non-CREDIT
     *         account would drive the balance below zero
     */
    @Transactional
    public BigDecimal adjustBalance(UUID accountId, BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("Skipping zero-delta balance adjustment for account {}", accountId);
            // Caller still wants the current balance.
            return accountRepository.findById(accountId)
                    .map(Account::getBalance)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
        }

        boolean isCredit = accountRepository.findById(accountId)
                .map(a -> a.getType() == AccountType.CREDIT)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        int rowsAffected;
        if (delta.signum() < 0) {
            BigDecimal absAmount = delta.negate();
            if (isCredit) {
                // Credit debt can grow — no balance guard.
                rowsAffected = accountRepository.addToBalance(accountId, delta);
            } else {
                // Non-credit account: reject if balance would go negative.
                rowsAffected = accountRepository.decrementBalanceIfSufficient(accountId, absAmount);
            }
        } else {
            rowsAffected = accountRepository.addToBalance(accountId, delta);
        }

        if (rowsAffected == 0) {
            // Either the account disappeared (vanishingly unlikely inside a
            // single transaction) or — for non-credit, negative deltas — the
            // balance was insufficient. Fetch the current balance to give the
            // caller a useful error message.
            Account current = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            throw new InsufficientFundsException(
                    "Account " + accountId + " has insufficient balance (" +
                            current.getBalance() + ") to apply delta " + delta,
                    current.getBalance(),
                    delta
            );
        }

        // Re-read to surface the authoritative post-update balance to the caller.
        BigDecimal newBalance = accountRepository.findById(accountId)
                .map(Account::getBalance)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        log.debug("Adjusted balance of account {} by {} (new balance: {})",
                accountId, delta, newBalance);
        return newBalance;
    }
}
