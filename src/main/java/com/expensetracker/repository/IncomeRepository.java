package com.expensetracker.repository;

import com.expensetracker.entity.Income;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncomeRepository extends JpaRepository<Income, UUID> {

    List<Income> findByUserIdOrderByTimestampDesc(UUID userId);

    List<Income> findByUserIdAndAccountIdOrderByTimestampDesc(UUID userId, UUID accountId);

    Optional<Income> findByIdAndUserId(UUID id, UUID userId);
}
