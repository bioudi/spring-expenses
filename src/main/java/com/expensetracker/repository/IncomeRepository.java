package com.expensetracker.repository;

import com.expensetracker.entity.Income;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncomeRepository extends JpaRepository<Income, UUID> {

    List<Income> findAllByUserIdOrderByTimestampDesc(UUID userId);

    List<Income> findAllByAccountIdOrderByTimestampDesc(UUID accountId);

    Optional<Income> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT i FROM Income i WHERE i.user.id = :userId "
            + "AND i.timestamp >= :startDate AND i.timestamp < :endDate "
            + "ORDER BY i.timestamp DESC")
    List<Income> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT i FROM Income i WHERE i.user.id = :userId AND i.account.id = :accountId "
            + "ORDER BY i.timestamp DESC")
    List<Income> findByUserIdAndAccountId(
            @Param("userId") UUID userId,
            @Param("accountId") UUID accountId);
}