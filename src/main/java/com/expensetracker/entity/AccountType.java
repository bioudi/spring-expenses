package com.expensetracker.entity;

/**
 * Type of an {@link Account}.
 *
 * <p>Stored in the {@code accounts.type} column as a VARCHAR (see
 * {@link Account#getType()} which uses {@code @Enumerated(EnumType.STRING)}),
 * so renaming or reordering values here is safe but renaming the persisted
 * string itself is a breaking schema change.
 */
public enum AccountType {
    BASE,
    SAVINGS,
    EMERGENCY,
    CREDIT
}