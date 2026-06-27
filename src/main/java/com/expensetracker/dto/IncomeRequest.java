package com.expensetracker.dto;

import com.expensetracker.entity.IncomeCategory;
import com.expensetracker.entity.IncomeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomeRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Type is required")
    private IncomeType type;

    @NotNull(message = "Category is required")
    private IncomeCategory category;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    /**
     * Optional account ID. If provided, the income amount is added
     * to the account balance.
     */
    private UUID accountId;

    private LocalDateTime timestamp;

    private String notes;
}
