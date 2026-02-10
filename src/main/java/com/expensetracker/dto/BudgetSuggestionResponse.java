package com.expensetracker.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetSuggestionResponse {
    private List<String> categories;
    private BigDecimal suggestedLimit;
    private String reasoning;
}
