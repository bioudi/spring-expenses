package com.expensetracker.repository;

import com.expensetracker.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);
}
