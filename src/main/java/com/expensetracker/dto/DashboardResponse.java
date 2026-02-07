package com.expensetracker.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private PeriodSummary week;
    private PeriodSummary month;
    private PeriodSummary year;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PeriodSummary {
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal totalSpent;
        private long transactionCount;
        private BigDecimal avgPerTransaction;
        private Map<String, CategoryBreakdown> categoryBreakdown;
        private List<MerchantSummary> topMerchants;
        private List<DailySpending> dailySpending;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryBreakdown {
        private BigDecimal total;
        private long count;
        private BigDecimal percentage;
        private BigDecimal avgPerTransaction;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MerchantSummary {
        private String merchant;
        private BigDecimal total;
        private long count;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailySpending {
        private LocalDate date;
        private BigDecimal total;
        private long count;
    }
}
