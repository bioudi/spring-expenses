package com.expensetracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.expensetracker.config.FlexibleBigDecimalDeserializer;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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

    private String paymentMethod;

    private String cardName;

    private String card;

    private String name;

    /**
     * Optional transaction date (date-only, ISO-8601 "YYYY-MM-DD").
     * If provided, takes precedence over {@link #timestamp} for the
     * persisted expense timestamp (converted to start-of-day LocalDateTime).
     * If absent, falls back to {@link #timestamp} then to LocalDateTime.now().
     */
    @JsonProperty("date")
    private LocalDate date;

    private LocalDateTime timestamp;

    private String notes;

    /**
     * Optional account this expense was charged against. May be null for
     * legacy expenses or expenses that aren't tied to a specific account.
     */
    private UUID accountId;
}
