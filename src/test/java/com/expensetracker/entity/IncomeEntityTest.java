package com.expensetracker.entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test that the {@link Income} entity is mapped correctly against the
 * H2 schema (used in test profile) and persists/loads without error.
 */
@SpringBootTest
class IncomeEntityTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private IncomeRepositoryStub repo;

    @org.springframework.stereotype.Repository
    interface IncomeRepositoryStub extends org.springframework.data.repository.Repository<Income, UUID> {
        Income save(Income income);
        java.util.Optional<Income> findById(UUID id);
    }

    @Test
    void entityIsRegisteredAndPersists() {
        // Verify the entity is registered with JPA
        EntityType<Income> meta = em.getMetamodel().entity(Income.class);
        assertThat(meta.getName()).isEqualTo("Income");

        // Create a User (required FK)
        User user = User.builder()
                .email("income-test@example.com")
                .password("pw")
                .apiKey(UUID.randomUUID().toString())
                .build();
        em.persist(user);
        em.flush();

        // Create an Income
        Income income = Income.builder()
                .name("Bi-weekly paycheck")
                .type(IncomeType.TRANSFER)
                .category(IncomeCategory.PAYCHECK)
                .amount(new BigDecimal("2500.00"))
                .user(user)
                .timestamp(LocalDateTime.now())
                .notes("June 27 payroll")
                .build();

        Income saved = repo.save(income);
        em.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Income reloaded = repo.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Bi-weekly paycheck");
        assertThat(reloaded.getType()).isEqualTo(IncomeType.TRANSFER);
        assertThat(reloaded.getCategory()).isEqualTo(IncomeCategory.PAYCHECK);
        assertThat(reloaded.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(reloaded.getNotes()).isEqualTo("June 27 payroll");
    }

    @Test
    void accountIdIsOptional() {
        User user = User.builder()
                .email("no-account@example.com")
                .password("pw")
                .apiKey(UUID.randomUUID().toString())
                .build();
        em.persist(user);
        em.flush();

        Income cashIncome = Income.builder()
                .name("Found $20 on the sidewalk")
                .type(IncomeType.CASH)
                .category(IncomeCategory.REFUND)
                .amount(new BigDecimal("20.00"))
                .user(user)
                .timestamp(LocalDateTime.now())
                .build();

        Income saved = repo.save(cashIncome);
        em.flush();

        assertThat(saved.getAccountId()).isNull();
    }
}