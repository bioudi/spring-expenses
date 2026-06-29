package com.expensetracker.dto;

import com.expensetracker.config.FlexibleBigDecimalDeserializer;
import com.expensetracker.config.RecurrenceFrequency;
import com.expensetracker.entity.IncomeCategory;
import com.expensetracker.entity.IncomeType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/recurring-incomes} and
 * {@code PUT /api/recurring-incomes/{id}}.
 *
 * <p>Mirrors {@link RecurringExpenseRequest} but carries income-specific
 * template fields ({@code name}, {@link IncomeType}, {@link IncomeCategory})
 * instead of {@code merchant}/{@code category}. The recurrence half is the
 * same vocabulary so the frontend and the reminder job can treat the two
 * sides symmetrically.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringIncomeRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Type is required")
    private IncomeType type;

    @NotNull(message = "Category is required")
    private IncomeCategory category;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @JsonDeserialize(using = FlexibleBigDecimalDeserializer.class)
    private BigDecimal amount;

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

    private UUID accountId;
}
