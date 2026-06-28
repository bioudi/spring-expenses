package com.expensetracker.dto;

import com.expensetracker.entity.Income;
import com.expensetracker.entity.IncomeCategory;
import com.expensetracker.entity.IncomeType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomeResponse {

    private UUID id;
    private String name;
    private IncomeType type;
    private IncomeCategory category;
    private BigDecimal amount;
    private UUID accountId;
    private LocalDateTime timestamp;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static IncomeResponse fromEntity(Income income) {
        return IncomeResponse.builder()
                .id(income.getId())
                .name(income.getName())
                .type(income.getType())
                .category(income.getCategory())
                .amount(income.getAmount())
                .accountId(income.getAccountId())
                .timestamp(income.getTimestamp())
                .notes(income.getNotes())
                .createdAt(income.getCreatedAt())
                .updatedAt(income.getUpdatedAt())
                .build();
    }
}
