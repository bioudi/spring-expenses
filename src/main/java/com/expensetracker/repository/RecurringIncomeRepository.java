package com.expensetracker.repository;

import com.expensetracker.entity.RecurringIncome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringIncomeRepository extends JpaRepository<RecurringIncome, UUID> {

    List<RecurringIncome> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<RecurringIncome> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT r FROM RecurringIncome r WHERE r.active = true AND r.nextOccurrence <= :today AND (r.endDate IS NULL OR r.endDate >= :today)")
    List<RecurringIncome> findDueRecurringIncomes(@Param("today") LocalDate today);

    @Query("SELECT r FROM RecurringIncome r JOIN FETCH r.user WHERE r.active = true AND r.nextOccurrence = :date AND (r.endDate IS NULL OR r.endDate >= :date)")
    List<RecurringIncome> findDueOnDate(@Param("date") LocalDate date);
}