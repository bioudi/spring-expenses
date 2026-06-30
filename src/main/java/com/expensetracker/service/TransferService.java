package com.expensetracker.service;

import com.expensetracker.dto.TransferRequest;
import com.expensetracker.dto.TransferResponse;
import com.expensetracker.dto.TransferResponse.AccountSnapshot;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.exception.AccountNotFoundException;
import com.expensetracker.repository.AccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
 * <p><b>Balance convention</b>: CREDIT account balances represent outstanding
 * debt and are stored as the amount owed (positive number). Real-money
 * accounts (BASE / SAVINGS / EMERGENCY) store the available balance (also
 * positive). Spending $100 on a credit card with $500 of debt raises the
 * credit balance to $600; paying $200 toward that card lowers it to $300.
 *
 * <p><b>Four transfer cases (from-type × to-type)</b>
 * <table>
 *   <tr><th>From</th><th>To</th><th>Semantics</th><th>Source Δ</th><th>Dest Δ</th></tr>
 *   <tr><td>non-CREDIT</td><td>non-CREDIT</td><td>Move money between real accounts</td><td>−amount</td><td>+amount</td></tr>
 *   <tr><td>non-CREDIT</td><td>CREDIT</td><td>Pay a credit card from a real account (dest debt shrinks)</td><td>−amount</td><td>−amount</td></tr>
 *   <tr><td>CREDIT</td><td>non-CREDIT</td><td>Cash advance / refund to real account (source debt grows)</td><td>+amount</td><td>+amount</td></tr>
 *   <tr><td>CREDIT</td><td>CREDIT</td><td>Balance transfer between cards (source debt grows, dest debt shrinks)</td><td>+amount</td><td>−amount</td></tr>
 * </table>
 *
 * <p>Both deltas are sign-aware by account type. The non-CREDIT source guard
 * (no overdraft) is enforced by the atomic
 * {@code UPDATE … WHERE balance &gt;= :amount} path in
 * {@link AccountService#adjustBalance(UUID, BigDecimal)} — see
 * {@link com.expensetracker.repository.AccountRepository#decrementBalanceIfSufficient(UUID, BigDecimal)}.
 * CREDIT sources have no guard: cash advances and balance transfers can grow
 * the source debt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final AccountRepository accountRepository;
    private final AccountService accountService;

    @PersistenceContext
    private EntityManager entityManager;

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

        // Sign of the source/dest deltas depends on the account type of each
        // side. CREDIT balances represent outstanding debt (positive = amount
        // owed); spending on credit grows debt, paying it down shrinks debt.
        // The four-case matrix:
        //   non-CREDIT → non-CREDIT: source −amount, dest +amount
        //   non-CREDIT → CREDIT:     source −amount, dest −amount (pay card)
        //   CREDIT     → non-CREDIT: source +amount, dest +amount (cash advance)
        //   CREDIT     → CREDIT:     source +amount, dest −amount (balance transfer)
        // adjustBalance handles the per-type guard internally: non-CREDIT
        // sources reject insufficient funds (InsufficientFundsException → 422),
        // CREDIT sources never throw on the source side. We discard the
        // returned balances and re-read below so the snapshot reflects the
        // authoritative post-update value.
        boolean fromIsCredit = from.getType() == AccountType.CREDIT;
        boolean toIsCredit = to.getType() == AccountType.CREDIT;
        BigDecimal sourceDelta = fromIsCredit ? amount : amount.negate();
        BigDecimal destDelta = toIsCredit ? amount.negate() : amount;

        accountService.adjustBalance(fromId, sourceDelta);
        accountService.adjustBalance(toId, destDelta);

        // Force the persistence context to reload these two entities from
        // the database. Without this, Hibernate's first-level cache would
        // return the pre-update Account instances that were loaded at the
        // top of this method — and the snapshot would show stale balances
        // even though the database was updated correctly. {@code refresh()}
        // issues a SELECT and overwrites the cached entity in place.
        entityManager.refresh(from);
        entityManager.refresh(to);

        return TransferResponse.builder()
                .transferId(UUID.randomUUID()) // No transfer row is persisted; this is a request-correlation id
                .fromAccount(AccountSnapshot.builder()
                        .id(from.getId())
                        .name(from.getName())
                        .type(from.getType())
                        .balance(from.getBalance())
                        .build())
                .toAccount(AccountSnapshot.builder()
                        .id(to.getId())
                        .name(to.getName())
                        .type(to.getType())
                        .balance(to.getBalance())
                        .build())
                .amount(amount)
                .description(request.getDescription())
                .timestamp(LocalDateTime.now())
                .build();
    }
}