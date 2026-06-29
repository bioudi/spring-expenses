package com.expensetracker.dto;

import com.expensetracker.config.RecurrenceFrequency;
import com.expensetracker.entity.IncomeCategory;
import com.expensetracker.entity.IncomeType;
import com.expensetracker.entity.RecurringIncome;
import lombok.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wire shape returned by every {@code /api/recurring-incomes*} endpoint.
 *
 * <p>Mirrors {@link RecurringExpenseResponse} so the frontend can reuse the
 * same list/edit modal plumbing; the income-specific half (name, type,
 * category) lines up one-for-one with {@code IncomeResponse}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringIncomeResponse {

    private UUID id;
    private String name;
    private IncomeType type;
    private IncomeCategory category;
    private BigDecimal amount;
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

    public static RecurringIncomeResponse fromEntity(RecurringIncome entity) {
        return RecurringIncomeResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .category(entity.getCategory())
                .amount(entity.getAmount())
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
