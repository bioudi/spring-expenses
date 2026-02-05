package com.expensetracker.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseSummary {

    private BigDecimal totalSpent;
    private long transactionCount;
    private Map<String, CategoryBreakdown> categoryBreakdown;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryBreakdown {
        private BigDecimal total;
        private long count;
        private BigDecimal percentage;
    }
}
