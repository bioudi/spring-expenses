package com.expensetracker.exception;

import java.util.UUID;

public class RecurringIncomeNotFoundException extends RuntimeException {

    public RecurringIncomeNotFoundException(UUID id) {
        super("Recurring income not found with id: " + id);
    }
}
