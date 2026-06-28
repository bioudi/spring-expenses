package com.expensetracker.exception;

import java.util.UUID;

public class IncomeNotFoundException extends RuntimeException {

    public IncomeNotFoundException(UUID id) {
        super("Income not found with id: " + id);
    }
}
