package com.expensetracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.expensetracker.config.FlexibleBigDecimalDeserializer;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @JsonDeserialize(using = FlexibleBigDecimalDeserializer.class)
    private BigDecimal amount;

    private String category;

    private String merchant;

    private String cardName;

    private String card;

    private String name;

    private LocalDateTime timestamp;

    private String notes;
}
