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

@Entity
@Table(name = "recurring_expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Template fields (mirrors Expense)
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String merchant;

    @Column(name = "payment_method", nullable = false, columnDefinition = "VARCHAR(255) DEFAULT 'Card'")
    @Builder.Default
    private String paymentMethod = "Card";

    @Column(name = "card_name")
    private String cardName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Recurrence fields
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
