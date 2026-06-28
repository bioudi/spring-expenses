package com.expensetracker.repository;

import com.expensetracker.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findAllByUserIdOrderByTimestampDesc(UUID userId);

    List<Expense> findByUserIdAndCategoryOrderByTimestampDesc(UUID userId, String category);

    @Query("SELECT e FROM Expense e WHERE e.user.id = :userId AND e.timestamp >= :startDate AND e.timestamp < :endDate ORDER BY e.timestamp DESC")
    List<Expense> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT e FROM Expense e WHERE e.user.id = :userId AND e.timestamp >= :startDate AND e.timestamp < :endDate AND e.category = :category ORDER BY e.timestamp DESC")
    List<Expense> findByUserIdAndDateRangeAndCategory(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("category") String category
    );

    @Query("SELECT e FROM Expense e WHERE e.user.id = :userId AND e.category = :category ORDER BY e.timestamp DESC")
    List<Expense> findByUserIdAndCategory(
            @Param("userId") UUID userId,
            @Param("category") String category
    );

    Optional<Expense> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Counts the expenses that still reference the given account id. Used by
     * {@code AccountService.deleteAccount} to reject deletion when expenses
     * are still linked, surfacing a clear 400 instead of a raw
     * foreign-key-violation 500.
     */
    long countByAccountId(UUID accountId);
}
