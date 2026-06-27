package com.expensetracker.service;

import com.expensetracker.dto.AccountRequest;
import com.expensetracker.dto.AccountResponse;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.User;
import com.expensetracker.exception.AccountNotFoundException;
import com.expensetracker.repository.AccountRepository;
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
        accountRepository.delete(account);
        log.info("Deleted account {}: name='{}', type={}", id, account.getName(), account.getType());
        return AccountResponse.fromEntity(account);
    }

    /**
     * Atomically adjusts an account's balance by the given delta.
     * The delta can be positive (increase balance) or negative (decrease balance).
     *
     * @param accountId the account to adjust (must exist)
     * @param delta     the amount to add (positive) or subtract (negative)
     */
    @Transactional
    public void adjustBalance(UUID accountId, BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("Skipping zero-delta balance adjustment for account {}", accountId);
            return;
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.setBalance(account.getBalance().add(delta));
        accountRepository.save(account);
        log.debug("Adjusted balance of account {} by {} (new balance: {})",
                accountId, delta, account.getBalance());
    }
}