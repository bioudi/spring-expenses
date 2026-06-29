package com.expensetracker.entity;

import com.expensetracker.config.RecurrenceFrequency;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Template row that describes a recurring income event (e.g. a bi-weekly
 * paycheck). Mirrors {@link RecurringExpense} so a single recurring-template
 * service can fan out either kind of transaction on its {@code nextOccurrence}.
 *
 * <p>Schema is owned by Hibernate {@code ddl-auto=update}; the authoritative
 * Flyway reference lives in
 * {@code V6__create_recurring_incomes_table.sql}.
 */
@Entity
@Table(
    name = "recurring_incomes",
    indexes = {
        @Index(name = "idx_recurring_incomes_user_id", columnList = "user_id"),
        @Index(name = "idx_recurring_incomes_account_id", columnList = "account_id"),
        @Index(name = "idx_recurring_incomes_next_occurrence", columnList = "next_occurrence"),
        @Index(name = "idx_recurring_incomes_active", columnList = "active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringIncome {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Template fields (mirror Income)
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncomeType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncomeCategory category;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Recurrence fields (mirror RecurringExpense)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurrenceFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private DayOfWeek dayOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_occurrence", nullable = false)
    private LocalDate nextOccurrence;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}