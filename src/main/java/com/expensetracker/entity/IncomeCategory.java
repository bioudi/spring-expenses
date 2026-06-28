package com.expensetracker.entity;

/**
 * Categorization of income. Extend by adding new values here; existing rows
 * are unaffected because the column is persisted as a VARCHAR.
 */
public enum IncomeCategory {
    PAYCHECK,
    REFUND,
    TAX_RETURN
}