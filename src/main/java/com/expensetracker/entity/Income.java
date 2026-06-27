package com.expensetracker.entity;

import com.expensetracker.config.IncomeType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Income entry recorded by a user. Belongs to a {@link User} (owner) and
 * targets a specific {@link Account} (whose balance will be credited by
 * the income service layer).
 *
 * <p>Storage notes:
 * <ul>
 *   <li>{@code id} is auto-generated as a UUID via JPA.</li>
 *   <li>{@code amount} uses {@code BigDecimal(19,4)} — matches the precision
 *       used by {@link Expense} and {@link Budget} in this project.</li>
 *   <li>{@code type} is stored as a {@code VARCHAR} via {@link EnumType#STRING}
 *       so renaming the enum never breaks historical rows.</li>
 *   <li>Indexes are placed on {@code user_id}, {@code account_id}, and
 *       {@code timestamp} because the primary query patterns are
 *       "list by user", "list by account", and "range scan by date".</li>
 * </ul>
 */
@Entity
@Table(
    name = "incomes",
    indexes = {
        @Index(name = "idx_incomes_user_id", columnList = "user_id"),
        @Index(name = "idx_incomes_account_id", columnList = "account_id"),
        @Index(name = "idx_incomes_timestamp", columnList = "timestamp"),
        @Index(name = "idx_incomes_user_timestamp", columnList = "user_id, timestamp")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Short human-readable label, e.g. "March paycheck" or "Client invoice #42". */
    @Column(nullable = false)
    private String name;

    /** Positive monetary value of the income (currency tracked on {@link Account}). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /** Categorical source of income — see {@link IncomeType} for the allowed values. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncomeType type;

    /** Free-form category bucket — e.g. "Primary Job", "Side Hustle". */
    @Column(nullable = false)
    private String category;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** When the income was received (or planned to be received). */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /** Optional free-form notes. Stored as TEXT so they don't bloat row size. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (timestamp == null) {
            timestamp = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}