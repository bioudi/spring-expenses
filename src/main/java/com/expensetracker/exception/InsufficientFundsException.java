package com.expensetracker.exception;

import java.math.BigDecimal;

/**
 * Thrown when an expense (or other operation) would drive a real-money
 * account's balance below zero. CREDIT accounts are debt-tracking and are
 * allowed to go more negative, so this is never thrown for them.
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