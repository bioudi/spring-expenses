package com.expensetracker.service;

import com.expensetracker.dto.TransferRequest;
import com.expensetracker.dto.TransferResponse;
import com.expensetracker.dto.TransferResponse.AccountSnapshot;
import com.expensetracker.entity.Account;
import com.expensetracker.exception.AccountNotFoundException;
import com.expensetracker.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Handles fund transfers between a user's own accounts — including credit
 * card payments — in a single {@code @Transactional} block so the two balance
 * mutations either both commit or both roll back.
 *
 * <p><b>Four transfer cases (from-type × to-type)</b>
 * <table>
 *   <tr><th>From</th><th>To</th><th>Semantics</th><th>Source guard?</th></tr>
 *   <tr><td>non-CREDIT</td><td>non-CREDIT</td><td>Move money between real accounts</td><td>yes (no overdraft)</td></tr>
 *   <tr><td>non-CREDIT</td><td>CREDIT</td><td>Pay a credit card from a real account</td><td>yes (no overdraft)</td></tr>
 *   <tr><td>CREDIT</td><td>non-CREDIT</td><td>Cash advance / refund to real account</td><td>no (debt can grow)</td></tr>
 *   <tr><td>CREDIT</td><td>CREDIT</td><td>Balance transfer between cards</td><td>no (debt can grow)</td></tr>
 * </table>
 *
 * <p>Every case still performs the destination add; only the source guard
 * changes. The guard is enforced by the atomic
 * {@code UPDATE … WHERE balance &gt;= :amount} path in
 * {@link AccountService#adjustBalance(UUID, BigDecimal)} — see
 * {@link com.expensetracker.repository.AccountRepository#decrementBalanceIfSufficient(UUID, BigDecimal)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepository;
    private final AccountService accountService;

    /**
     * Moves {@code request.amount} from one of the caller's accounts to
     * another. Both sides of the transfer must belong to the same user. The
     * amount must be positive (already enforced by
     * {@link TransferRequest}{@code .amount}'s {@code @Positive}); the same
     * account guard runs here as a defensive belt-and-braces check.
     *
     * @throws AccountNotFoundException     if either account does not exist or
     *                                      is not owned by {@code userId}
     * @throws IllegalArgumentException     if both ids are the same (mapped to
     *                                      409 Conflict by
     *                                      {@code GlobalExceptionHandler})
     * @throws InsufficientFundsException   if the source account is non-CREDIT
     *                                      and the transfer would overdraw it
     */
    @Transactional
    public TransferResponse transfer(TransferRequest request, UUID userId) {
        UUID fromId = request.getFromAccountId();
        UUID toId = request.getToAccountId();
        BigDecimal amount = request.getAmount();

        // Defensive same-account check — the controller could be hit by a
        // crafted request that bypasses client-side guards. We treat this as
        // a 409 Conflict rather than 400 because the request is structurally
        // valid; the conflict is with the resource state.
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException(
                    "Cannot transfer to the same account (id: " + fromId + ")");
        }

        // Fetch both accounts and confirm ownership in one pass.
        Account from = accountRepository.findByIdAndUserId(fromId, userId)
                .orElseThrow(() -> new AccountNotFoundException(fromId));
        Account to = accountRepository.findByIdAndUserId(toId, userId)
                .orElseThrow(() -> new AccountNotFoundException(toId));

        log.info("Transferring {} from '{}' ({}) to '{}' ({}) for user {}",
                amount, from.getName(), from.getType(),
                to.getName(), to.getType(), userId);

        // adjustBalance handles the per-type guard internally: non-CREDIT
        // sources reject insufficient funds (InsufficientFundsException → 422),
        // CREDIT sources never throw on the source side. We discard the
        // returned balances and re-read below so the snapshot reflects the
        // authoritative post-update value (in case Hibernate's first-level
        // cache returned a stale value).
        accountService.adjustBalance(fromId, amount.negate());
        accountService.adjustBalance(toId, amount);

        // Re-read for the snapshot. Inside the same transaction the entities
        // are already managed, but the snapshot should be independent of the
        // entity reference held above.
        Account fromAfter = accountRepository.findById(fromId).orElseThrow();
        Account toAfter = accountRepository.findById(toId).orElseThrow();

        return TransferResponse.builder()
                .transferId(UUID.randomUUID()) // No transfer row is persisted; this is a request-correlation id
                .fromAccount(AccountSnapshot.builder()
                        .id(fromAfter.getId())
                        .name(fromAfter.getName())
                        .type(fromAfter.getType())
                        .balance(fromAfter.getBalance())
                        .build())
                .toAccount(AccountSnapshot.builder()
                        .id(toAfter.getId())
                        .name(toAfter.getName())
                        .type(toAfter.getType())
                        .balance(toAfter.getBalance())
                        .build())
                .amount(amount)
                .description(request.getDescription())
                .timestamp(LocalDateTime.now())
                .build();
    }
}