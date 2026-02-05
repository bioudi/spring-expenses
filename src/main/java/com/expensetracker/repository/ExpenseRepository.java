package com.expensetracker.repository;

import com.expensetracker.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findAllByOrderByTimestampDesc();

    List<Expense> findByCategoryOrderByTimestampDesc(String category);

    @Query("SELECT e FROM Expense e WHERE e.timestamp >= :startDate AND e.timestamp <= :endDate ORDER BY e.timestamp DESC")
    List<Expense> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT e FROM Expense e WHERE e.timestamp >= :startDate AND e.timestamp <= :endDate AND e.category = :category ORDER BY e.timestamp DESC")
    List<Expense> findByDateRangeAndCategory(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("category") String category
    );

    @Query("SELECT e FROM Expense e WHERE e.category = :category ORDER BY e.timestamp DESC")
    List<Expense> findByCategory(@Param("category") String category);
}
