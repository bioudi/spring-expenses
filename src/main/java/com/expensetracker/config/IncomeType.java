package com.expensetracker.config;

/**
 * Type of income source. Stored as VARCHAR (EnumType.STRING) on the
 * {@code income.type} column.
 */
public enum IncomeType {
    SALARY,
    FREELANCE,
    INVESTMENT,
    GIFT,
    REFUND,
    RENTAL,
    BUSINESS,
    DIVIDEND,
    INTEREST,
    OTHER
}