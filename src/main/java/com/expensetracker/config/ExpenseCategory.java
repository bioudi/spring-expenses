package com.expensetracker.config;

import java.util.Set;

public final class ExpenseCategory {

    public static final Set<String> VALID_CATEGORIES = Set.of(
            // Food
            "Groceries",
            "Restaurants",
            "Coffee & Cafes",
            "Fast Food",
            "Bars & Alcohol",
            // Transportation
            "Gas & Fuel",
            "Public Transit",
            "Rideshare & Taxi",
            "Parking",
            "Car Maintenance",
            // Housing
            "Rent & Mortgage",
            "Utilities",
            "Internet & Phone",
            "Insurance",
            // Shopping
            "Clothing",
            "Electronics",
            "Home & Garden",
            // Health
            "Pharmacy",
            "Doctor & Dental",
            "Gym & Fitness",
            // Entertainment & Subscriptions
            "Subscriptions",
            "Streaming",
            "Movies & Events",
            "Gaming",
            // Travel
            "Flights",
            "Hotels",
            "Car Rental",
            // Other
            "Education",
            "Gifts & Donations",
            "Pet Care",
            "Personal Care",
            "Bank & Fees",
            "Other"
    );

    private ExpenseCategory() {
    }

    public static boolean isValid(String category) {
        return category != null && VALID_CATEGORIES.contains(category);
    }
}
