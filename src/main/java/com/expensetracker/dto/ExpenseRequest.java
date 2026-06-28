package com.expensetracker.dto;

import com.expensetracker.config.FlexibleBigDecimalDeserializer;
import com.expensetracker.config.StrictUuidDeserializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
     * Optional account ID. If provided, the account must exist and belong
     * to the current user.
     *
     * <p>An empty / blank string is rejected with HTTP 400 rather than being
     * silently coerced to {@code null} (which Jackson's default
     * {@code ACCEPT_EMPTY_STRING_AS_NULL_OBJECT} would otherwise do). The
     * dedicated {@link StrictUuidDeserializer} raises an
     * {@code InvalidFormatException} so {@code GlobalExceptionHandler} can
     * surface a clear "Invalid account_id format" message — matching the
     * behaviour for non-UUID strings (e.g. {@code "not-a-uuid"}).
     */
    @JsonDeserialize(using = StrictUuidDeserializer.class)
    private UUID accountId;
}
