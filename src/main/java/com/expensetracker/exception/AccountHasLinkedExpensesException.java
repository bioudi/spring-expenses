package com.expensetracker.exception;

import java.util.UUID;

/**
 * Thrown when an account cannot be deleted because it still has expenses
 * linked to it. Surfaced as a 400 Bad Request so the caller gets a clear,
 * actionable message instead of a raw foreign-key-violation 500.
 */
public class AccountHasLinkedExpensesException extends RuntimeException {

    private final UUID accountId;
    private final long linkedExpenseCount;

    public AccountHasLinkedExpensesException(UUID accountId, long linkedExpenseCount) {
        super(String.format(
                "Cannot delete account %s: %d expense(s) are still linked to it. " +
                "Delete or reassign the linked expenses first.",
                accountId, linkedExpenseCount));
        this.accountId = accountId;
        this.linkedExpenseCount = linkedExpenseCount;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getLinkedExpenseCount() {
        return linkedExpenseCount;
    }
}
