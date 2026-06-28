package com.expensetracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "incomes",
    indexes = {
        @Index(name = "idx_incomes_user_id", columnList = "user_id"),
        @Index(name = "idx_incomes_account_id", columnList = "account_id"),
        @Index(name = "idx_incomes_timestamp", columnList = "timestamp"),
        @Index(name = "idx_incomes_user_timestamp", columnList = "user_id,timestamp")
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

    /**
     * Optional reference to an account in the {@code accounts} table. The
     * {@code accounts} table is not yet defined in the application schema, so
     * the FK constraint is intentionally omitted here; once an Account entity
     * is introduced, switch this to a proper {@code @ManyToOne} relation.
     */
    @Column(name = "account_id")
    private UUID accountId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(columnDefinition = "TEXT")
    private String notes;

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