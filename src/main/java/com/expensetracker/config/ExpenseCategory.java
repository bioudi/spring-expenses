package com.expensetracker.config;

import java.util.Set;

public final class ExpenseCategory {

    public static final Set<String> VALID_CATEGORIES = Set.of(
            "Food & Drinks",
            "Shopping",
            "Travel",
            "Services",
            "Entertainment",
            "Health",
            "Transportation"
    );

    private ExpenseCategory() {
    }

    public static boolean isValid(String category) {
        return category != null && VALID_CATEGORIES.contains(category);
    }
}
