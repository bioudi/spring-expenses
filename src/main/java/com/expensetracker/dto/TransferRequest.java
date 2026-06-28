package com.expensetracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload for {@code POST /api/transfers}. Validates that:
 * <ul>
 *   <li>both accounts are provided (NotNull)</li>
 *   <li>the amount is strictly positive (Positive — zero or negative is rejected)</li>
 *   <li>the optional description stays within a reasonable size</li>
 * </ul>
 *
 * <p>Other validations (same-account rejection, ownership, balance guards) live
 * in {@link com.expensetracker.service.TransferService} because they require
 * lookups against the database.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferRequest {

    @NotNull(message = "From account is required")
    private UUID fromAccountId;

    @NotNull(message = "To account is required")
    private UUID toAccountId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @Size(max = 500, message = "Description must be 500 characters or fewer")
    private String description;
}