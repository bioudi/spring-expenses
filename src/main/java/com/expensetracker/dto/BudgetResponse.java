package com.expensetracker.dto;

import com.expensetracker.entity.Budget;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetResponse {

    private UUID id;
    private List<String> categories;
    private BigDecimal monthlyLimit;
    private BigDecimal spent;
    private BigDecimal remaining;
    private BigDecimal percentUsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BudgetResponse fromEntity(Budget budget, BigDecimal spent) {
        BigDecimal remaining = budget.getMonthlyLimit().subtract(spent);
        BigDecimal percentUsed = budget.getMonthlyLimit().compareTo(BigDecimal.ZERO) > 0
                ? spent.multiply(BigDecimal.valueOf(100)).divide(budget.getMonthlyLimit(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return BudgetResponse.builder()
                .id(budget.getId())
                .categories(budget.getCategories())
                .monthlyLimit(budget.getMonthlyLimit())
                .spent(spent)
                .remaining(remaining)
                .percentUsed(percentUsed)
                .createdAt(budget.getCreatedAt())
                .updatedAt(budget.getUpdatedAt())
                .build();
    }
}
