package com.expensetracker.repository;

import com.expensetracker.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Atomically subtracts {@code amount} from {@code balance} for the row with
     * the given {@code id}, but only if the current balance is at least
     * {@code amount}.
     *
     * <p>Returns the number of rows affected (0 if the account does not exist OR
     * the balance is insufficient; 1 on a successful deduction). The database
     * evaluates the predicate and the write in a single statement, so concurrent
     * callers cannot lose updates the way a read-then-write pattern would.
     *
     * <p>Used by expense create/update paths to keep account balances
     * consistent under concurrent load and to reject expenses that would
     * overdraw a real-money account.
     */
    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance - :amount " +
           "WHERE a.id = :id AND a.balance >= :amount")
    int decrementBalanceIfSufficient(
            @Param("id") UUID id,
            @Param("amount") BigDecimal amount
    );

    /**
     * Atomically adds {@code amount} to {@code balance} for the row with the
     * given {@code id}. The amount may be negative (to subtract from balance).
     * Returns the number of rows affected (0 if the account does not exist;
     * 1 on a successful write). Used for expense delete restores, income
     * adds, and CREDIT-account debt growth where no balance guard applies.
     */
    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance + :amount WHERE a.id = :id")
    int addToBalance(
            @Param("id") UUID id,
            @Param("amount") BigDecimal amount
    );
}
