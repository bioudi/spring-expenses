package com.expensetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookBudgetsResponse {

    private List<BudgetCategoryStatus> budgets;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BudgetCategoryStatus {
        private String category;
        private BigDecimal limit;
        private BigDecimal spent;
        private BigDecimal percentage;
    }
}