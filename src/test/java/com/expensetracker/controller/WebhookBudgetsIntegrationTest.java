package com.expensetracker.controller;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/webhook/budgets.
 *
 * Covers:
 *   1. Valid API key → 200 with correct budget data
 *   2. Missing API key  → 401
 *   3. Invalid API key  → 403
 *   4. ?date=YYYY-MM    → that month's data only
 *   5. Default date     → current month
 *   6. Invalid date format → 400
 *
 * Uses H2 in-memory DB (MODE=PostgreSQL for compatibility) and MockMvc
 * to exercise the full Spring web stack including ApiKeyFilter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class WebhookBudgetsIntegrationTest {

    private static final String VALID_API_KEY = "valid-test-api-key-0001";
    private static final String INVALID_API_KEY = "i-do-not-exist";

    // Fixture months are derived from the real clock so the "no date param →
    // current month" tests never expire. Hardcoded months (June 2026 in the
    // original version) turn into time bombs the moment the calendar rolls
    // past them.
    private static final YearMonth CURRENT_MONTH = YearMonth.now();
    private static final YearMonth PREVIOUS_MONTH = CURRENT_MONTH.minusMonths(1);
    private static final YearMonth NEXT_MONTH = CURRENT_MONTH.plusMonths(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private com.expensetracker.repository.AccountRepository accountRepository;

    // Suppress side-effect runners that aren't needed for tests
    @MockBean
    private DataMigrationRunner dataMigrationRunner;

    @MockBean
    private SchemaMigrationRunner schemaMigrationRunner;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Delete in FK order: dependents first, then users.
        // Accounts were added after the original budget test was written — without
        // an accountRepository.deleteAll() here, accounts left over by sibling
        // tests (ExpenseControllerIntegrationTest, AccountControllerIntegrationTest,
        // WebhookControllerExpenseCreateTest, …) cascade-fail this setUp() with
        // a FK violation the moment userRepository.deleteAll() tries to remove
        // their owning user.
        //
        // Order matters: expenses FK → accounts FK → users. Expenses must come
        // off before accounts, and accounts before users.
        expenseRepository.deleteAll();
        accountRepository.deleteAll();
        budgetRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("budget-tester@example.com")
                .password("encoded-password")
                .displayName("Budget Tester")
                .apiKey(VALID_API_KEY)
                .build());
    }

    // =====================================================================
    //  Authentication
    // =====================================================================

    @Nested
    class Authentication {

        @Test
        void missingApiKey_returns401() throws Exception {
            mockMvc.perform(get("/api/webhook/budgets"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message", containsString("Missing API key")));
        }

        @Test
        void invalidApiKey_returns403() throws Exception {
            mockMvc.perform(get("/api/webhook/budgets")
                            .header("X-API-Key", INVALID_API_KEY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message", containsString("Invalid API key")));
        }
    }

    // =====================================================================
    //  Happy path — valid API key with budget data
    // =====================================================================

    @Nested
    class HappyPath {

        @BeforeEach
        void setUpBudgets() {
            // Budget A: single category, limit 1500 (saved first → last in response)
            budgetRepository.save(Budget.builder()
                    .categories(List.of("Groceries"))
                    .monthlyLimit(new BigDecimal("1500.00"))
                    .user(testUser)
                    .build());

            // Budget B: multi-category, limit 1000 → 500 per category (saved second → first in response)
            budgetRepository.save(Budget.builder()
                    .categories(List.of("Restaurants", "Fast Food"))
                    .monthlyLimit(new BigDecimal("1000.00"))
                    .user(testUser)
                    .build());

            // Expenses in the current month
            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("300.00"))
                    .category("Groceries")
                    .merchant("Walmart")
                    .timestamp(CURRENT_MONTH.atDay(15).atTime(10, 0))
                    .user(testUser)
                    .build());

            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("50.00"))
                    .category("Restaurants")
                    .merchant("McDonald's")
                    .timestamp(CURRENT_MONTH.atDay(10).atTime(12, 0))
                    .user(testUser)
                    .build());

            // Expense for a category without a budget (should NOT appear in response)
            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("25.00"))
                    .category("Coffee & Cafes")
                    .merchant("Tim Hortons")
                    .timestamp(CURRENT_MONTH.atDay(12).atTime(8, 0))
                    .user(testUser)
                    .build());
        }

        @Test
        void validApiKey_returns200_withCorrectBudgets() throws Exception {
            // Budget order: Budget B (newer) → Budget A (older)
            // Budget B categories: [Restaurants(limit=500, spent=50), Fast Food(limit=500, spent=0)]
            // Budget A categories: [Groceries(limit=1500, spent=300)]
            mockMvc.perform(get("/api/webhook/budgets")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.budgets").isArray())
                    .andExpect(jsonPath("$.budgets.length()").value(3))

                    // [0] Restaurants — limit=500, spent=50, percentage=10.00
                    .andExpect(jsonPath("$.budgets[0].category").value("Restaurants"))
                    .andExpect(jsonPath("$.budgets[0].limit").value(closeTo(500.0, 0.01)))
                    .andExpect(jsonPath("$.budgets[0].spent").value(closeTo(50.0, 0.01)))
                    .andExpect(jsonPath("$.budgets[0].percentage").value(closeTo(10.0, 0.01)))

                    // [1] Fast Food — limit=500, spent=0, percentage=0
                    .andExpect(jsonPath("$.budgets[1].category").value("Fast Food"))
                    .andExpect(jsonPath("$.budgets[1].limit").value(closeTo(500.0, 0.01)))
                    .andExpect(jsonPath("$.budgets[1].spent").value(0))
                    .andExpect(jsonPath("$.budgets[1].percentage").value(0))

                    // [2] Groceries — limit=1500, spent=300, percentage=20.00
                    .andExpect(jsonPath("$.budgets[2].category").value("Groceries"))
                    .andExpect(jsonPath("$.budgets[2].limit").value(closeTo(1500.0, 0.01)))
                    .andExpect(jsonPath("$.budgets[2].spent").value(closeTo(300.0, 0.01)))
                    .andExpect(jsonPath("$.budgets[2].percentage").value(closeTo(20.0, 0.01)));
        }

        @Test
        void budgetsWithoutExpenses_showsZeroSpent() throws Exception {
            // Add a budget whose category has no transactions yet
            budgetRepository.save(Budget.builder()
                    .categories(List.of("Electronics"))
                    .monthlyLimit(new BigDecimal("2000.00"))
                    .user(testUser)
                    .build());

            mockMvc.perform(get("/api/webhook/budgets")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    // Electronics is newest → $.budgets[0]
                    .andExpect(jsonPath("$.budgets[0].category").value("Electronics"))
                    .andExpect(jsonPath("$.budgets[0].spent").value(0))
                    .andExpect(jsonPath("$.budgets[0].percentage").value(0));
        }

        @Test
        void budgets_withZeroMonthlyLimit_returnsZeroPercentage() throws Exception {
            budgetRepository.save(Budget.builder()
                    .categories(List.of("Gifts & Donations"))
                    .monthlyLimit(BigDecimal.ZERO)
                    .user(testUser)
                    .build());

            mockMvc.perform(get("/api/webhook/budgets")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    // Gifts & Donations is newest → $.budgets[0]
                    .andExpect(jsonPath("$.budgets[0].category").value("Gifts & Donations"))
                    .andExpect(jsonPath("$.budgets[0].percentage").value(0));
        }
    }

    // =====================================================================
    //  Date parameter
    // =====================================================================

    @Nested
    class DateParameter {

        @BeforeEach
        void setUpBudgetsAndExpenses() {
            // Single budget, single category
            budgetRepository.save(Budget.builder()
                    .categories(List.of("Gas & Fuel"))
                    .monthlyLimit(new BigDecimal("600.00"))
                    .user(testUser)
                    .build());

            // Current-month expense
            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("80.00"))
                    .category("Gas & Fuel")
                    .merchant("Shell")
                    .timestamp(CURRENT_MONTH.atDay(5).atTime(14, 0))
                    .user(testUser)
                    .build());

            // Next-month expense
            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("45.00"))
                    .category("Gas & Fuel")
                    .merchant("Petro-Canada")
                    .timestamp(NEXT_MONTH.atDay(3).atTime(9, 0))
                    .user(testUser)
                    .build());

            // Previous-month expense
            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("60.00"))
                    .category("Gas & Fuel")
                    .merchant("Esso")
                    .timestamp(PREVIOUS_MONTH.atDay(20).atTime(16, 0))
                    .user(testUser)
                    .build());
        }

        @Test
        void withDateParameter_returnsDataForThatMonth() throws Exception {
            mockMvc.perform(get("/api/webhook/budgets")
                            .param("date", CURRENT_MONTH.toString())
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.budgets.length()").value(1))
                    .andExpect(jsonPath("$.budgets[0].spent").value(closeTo(80.0, 0.01)));
        }

        @Test
        void withDifferentMonth_returnsDataForThatMonth() throws Exception {
            mockMvc.perform(get("/api/webhook/budgets")
                            .param("date", NEXT_MONTH.toString())
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.budgets.length()").value(1))
                    .andExpect(jsonPath("$.budgets[0].spent").value(closeTo(45.0, 0.01)));
        }

        @Test
        void withoutDate_usesCurrentMonth() throws Exception {
            // Only the current-month expense (80) falls in the default window
            mockMvc.perform(get("/api/webhook/budgets")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.budgets[0].spent").value(closeTo(80.0, 0.01)));
        }

        @Test
        void invalidDateFormat_returns400() throws Exception {
            mockMvc.perform(get("/api/webhook/budgets")
                            .param("date", "2026/06")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void nonNumericDateFormat_returns400() throws Exception {
            mockMvc.perform(get("/api/webhook/budgets")
                            .param("date", "foobar")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void invalidMonth_returns400() throws Exception {
            // Month 13 is out of range — must not crash with 500
            mockMvc.perform(get("/api/webhook/budgets")
                            .param("date", "2026-13")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void missingHyphen_returns400() throws Exception {
            // "202605" is missing the hyphen separator
            mockMvc.perform(get("/api/webhook/budgets")
                            .param("date", "202605")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", containsString("Invalid date format")));
        }
    }

    // =====================================================================
    //  Multi-user isolation
    // =====================================================================

    @Nested
    class UserIsolation {

        @Test
        void otherUserBudgets_notVisible() throws Exception {
            // Another user with their own budget
            User otherUser = userRepository.save(User.builder()
                    .email("other@example.com")
                    .password("encoded-pw")
                    .displayName("Other")
                    .apiKey("other-user-api-key-9999")
                    .build());

            budgetRepository.save(Budget.builder()
                    .categories(List.of("Groceries"))
                    .monthlyLimit(new BigDecimal("9999.00"))
                    .user(otherUser)
                    .build());

            // Test user has no budgets → empty array
            mockMvc.perform(get("/api/webhook/budgets")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.budgets").isEmpty());
        }
    }

    // =====================================================================
    //  Edge cases
    // =====================================================================

    @Test
    void noBudgets_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/webhook/budgets")
                        .header("X-API-Key", VALID_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgets").isArray())
                .andExpect(jsonPath("$.budgets").isEmpty());
    }
}