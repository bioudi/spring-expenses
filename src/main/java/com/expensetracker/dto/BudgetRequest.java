package com.expensetracker.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.expensetracker.config.FlexibleBigDecimalDeserializer;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetRequest {

    @NotEmpty(message = "At least one category is required")
    private List<String> categories;

    @NotNull(message = "Monthly limit is required")
    @Positive(message = "Monthly limit must be positive")
    @JsonDeserialize(using = FlexibleBigDecimalDeserializer.class)
    private BigDecimal monthlyLimit;
}
