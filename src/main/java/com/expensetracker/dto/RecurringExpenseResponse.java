package com.expensetracker.dto;

import com.expensetracker.config.RecurrenceFrequency;
import com.expensetracker.entity.RecurringExpense;
import lombok.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringExpenseResponse {

    private UUID id;
    private BigDecimal amount;
    private String category;
    private String merchant;
    private String notes;
    private RecurrenceFrequency frequency;
    private DayOfWeek dayOfWeek;
    private Integer dayOfMonth;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextOccurrence;
    private boolean active;
    private UUID accountId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RecurringExpenseResponse fromEntity(RecurringExpense entity) {
        return RecurringExpenseResponse.builder()
                .id(entity.getId())
                .amount(entity.getAmount())
                .category(entity.getCategory())
                .merchant(entity.getMerchant())
                .notes(entity.getNotes())
                .frequency(entity.getFrequency())
                .dayOfWeek(entity.getDayOfWeek())
                .dayOfMonth(entity.getDayOfMonth())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .nextOccurrence(entity.getNextOccurrence())
                .active(entity.isActive())
                .accountId(entity.getAccount() != null ? entity.getAccount().getId() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
