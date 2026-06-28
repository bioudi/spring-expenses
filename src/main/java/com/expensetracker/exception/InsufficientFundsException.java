package com.expensetracker.exception;

import java.math.BigDecimal;

/**
 * Thrown when a balance mutation would drive a non-CREDIT account's balance
 * below zero. The atomic {@code adjustBalance} path uses the database's
 * {@code WHERE balance &gt;= :amount} predicate to detect this race-free.
 *
 * <p>Translated to HTTP 422 by {@code GlobalExceptionHandler}.
 */
public class InsufficientFundsException extends RuntimeException {

    private final BigDecimal balance;
    private final BigDecimal amount;

    public InsufficientFundsException(String message, BigDecimal balance, BigDecimal amount) {
        super(message);
        this.balance = balance;
        this.amount = amount;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
