package com.expensetracker.dto;

import com.expensetracker.config.RecurrenceFrequency;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.expensetracker.config.FlexibleBigDecimalDeserializer;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringExpenseRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @JsonDeserialize(using = FlexibleBigDecimalDeserializer.class)
    private BigDecimal amount;

    @NotBlank(message = "Merchant is required")
    private String merchant;

    private String category;

    private String notes;

    @NotNull(message = "Frequency is required")
    private RecurrenceFrequency frequency;

    private DayOfWeek dayOfWeek;

    @Min(value = 1, message = "Day of month must be between 1 and 31")
    @Max(value = 31, message = "Day of month must be between 1 and 31")
    private Integer dayOfMonth;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate endDate;
}
