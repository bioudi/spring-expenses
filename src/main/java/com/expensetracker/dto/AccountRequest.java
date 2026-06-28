package com.expensetracker.dto;

import com.expensetracker.entity.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountRequest {

    @NotBlank(message = "Account name is required")
    private String name;

    private BigDecimal balance;

    @NotNull(message = "Account type is required")
    private AccountType type;
}