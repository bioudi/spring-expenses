package com.expensetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDashboardResponse {

    private BigDecimal totalSpent;
    private long transactionCount;
    private Map<String, CategoryEntry> categoryBreakdown;
    private List<MerchantEntry> topMerchants;
    private WebhookBudgetsResponse budgetStatus;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryEntry {
        private BigDecimal total;
        private long count;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MerchantEntry {
        private String merchant;
        private BigDecimal total;
        private long count;
    }
}