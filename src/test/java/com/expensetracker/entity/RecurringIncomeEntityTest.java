package com.expensetracker.entity;

import com.expensetracker.config.RecurrenceFrequency;
import com.expensetracker.repository.RecurringIncomeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test that the {@link RecurringIncome} entity is mapped correctly
 * against the H2 schema (used in test profile) and persists/loads without
 * error. Mirrors {@code IncomeEntityTest} so future schema drift on either
 * table is caught here rather than in controller integration tests.
 */
@SpringBootTest
@Transactional
class RecurringIncomeEntityTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private RecurringIncomeRepository repo;

    @Test
    @Transactional
    void entityIsRegisteredAndPersists() {
        // Verify the entity is registered with JPA
        EntityType<RecurringIncome> meta = em.getMetamodel().entity(RecurringIncome.class);
        assertThat(meta.getName()).isEqualTo("RecurringIncome");

        // Create a User (required FK)
        User user = User.builder()
                .email("recurring-income-test@example.com")
                .password("pw")
                .apiKey(UUID.randomUUID().toString())
                .build();
        em.persist(user);
        em.flush();

        // Create a RecurringIncome template
        LocalDate today = LocalDate.now();
        RecurringIncome template = RecurringIncome.builder()
                .name("Bi-weekly paycheck")
                .type(IncomeType.TRANSFER)
                .category(IncomeCategory.PAYCHECK)
                .amount(new BigDecimal("2500.00"))
                .frequency(RecurrenceFrequency.BI_WEEKLY)
                .dayOfWeek(java.time.DayOfWeek.FRIDAY)
                .startDate(today)
                .nextOccurrence(today)
                .active(true)
                .notes("June payroll")
                .user(user)
                .build();

        RecurringIncome saved = repo.save(template);
        em.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        RecurringIncome reloaded = repo.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Bi-weekly paycheck");
        assertThat(reloaded.getType()).isEqualTo(IncomeType.TRANSFER);
        assertThat(reloaded.getCategory()).isEqualTo(IncomeCategory.PAYCHECK);
        assertThat(reloaded.getFrequency()).isEqualTo(RecurrenceFrequency.BI_WEEKLY);
        assertThat(reloaded.getAmount()).isEqualByComparingTo("2500.00");
        assertThat(reloaded.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.FRIDAY);
        assertThat(reloaded.getStartDate()).isEqualTo(today);
        assertThat(reloaded.getNextOccurrence()).isEqualTo(today);
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getNotes()).isEqualTo("June payroll");
        assertThat(reloaded.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @Transactional
    void accountAndEndDateAreOptional() {
        User user = User.builder()
                .email("recurring-income-no-account@example.com")
                .password("pw")
                .apiKey(UUID.randomUUID().toString())
                .build();
        em.persist(user);
        em.flush();

        LocalDate today = LocalDate.now();
        RecurringIncome template = RecurringIncome.builder()
                .name("Tax refund (open-ended)")
                .type(IncomeType.CASH)
                .category(IncomeCategory.TAX_RETURN)
                .amount(new BigDecimal("500.00"))
                .frequency(RecurrenceFrequency.MONTHLY)
                .dayOfMonth(15)
                .startDate(today)
                .nextOccurrence(today)
                .user(user)
                .build();

        RecurringIncome saved = repo.save(template);
        em.flush();

        assertThat(saved.getAccount()).isNull();
        assertThat(saved.getEndDate()).isNull();
        assertThat(saved.getDayOfMonth()).isEqualTo(15);
    }

    @Test
    @Transactional
    void findAllByUserIdReturnsTemplatesForThatUser() {
        User user = User.builder()
                .email("recurring-income-list@example.com")
                .password("pw")
                .apiKey(UUID.randomUUID().toString())
                .build();
        em.persist(user);
        em.flush();

        LocalDate today = LocalDate.now();
        for (int i = 0; i < 3; i++) {
            repo.save(RecurringIncome.builder()
                    .name("Template " + i)
                    .type(IncomeType.TRANSFER)
                    .category(IncomeCategory.PAYCHECK)
                    .amount(new BigDecimal("100.00"))
                    .frequency(RecurrenceFrequency.MONTHLY)
                    .dayOfMonth(1)
                    .startDate(today)
                    .nextOccurrence(today)
                    .user(user)
                    .build());
        }
        em.flush();

        assertThat(repo.findAllByUserIdOrderByCreatedAtDesc(user.getId())).hasSize(3);
    }
}