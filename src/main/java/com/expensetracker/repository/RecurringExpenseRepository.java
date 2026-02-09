package com.expensetracker.repository;

import com.expensetracker.entity.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {

    List<RecurringExpense> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<RecurringExpense> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT r FROM RecurringExpense r WHERE r.active = true AND r.nextOccurrence <= :today AND (r.endDate IS NULL OR r.endDate >= :today)")
    List<RecurringExpense> findDueRecurringExpenses(@Param("today") LocalDate today);

    @Query("SELECT r FROM RecurringExpense r JOIN FETCH r.user WHERE r.active = true AND r.nextOccurrence = :date AND (r.endDate IS NULL OR r.endDate >= :date)")
    List<RecurringExpense> findDueOnDate(@Param("date") LocalDate date);
}
