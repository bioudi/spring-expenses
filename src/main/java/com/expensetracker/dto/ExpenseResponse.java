package com.expensetracker.dto;

import com.expensetracker.entity.Expense;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseResponse {

    private UUID id;
    private BigDecimal amount;
    private String category;
    private String merchant;
    private String paymentMethod;
    private String cardName;
    private LocalDateTime timestamp;
    private String notes;
    private LocalDateTime createdAt;

    public static ExpenseResponse fromEntity(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .amount(expense.getAmount())
                .category(expense.getCategory())
                .merchant(expense.getMerchant())
                .paymentMethod(expense.getPaymentMethod())
                .cardName(expense.getCardName())
                .timestamp(expense.getTimestamp())
                .notes(expense.getNotes())
                .createdAt(expense.getCreatedAt())
                .build();
    }
}
