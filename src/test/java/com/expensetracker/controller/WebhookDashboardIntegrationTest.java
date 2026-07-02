package com.expensetracker.controller;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.BudgetRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/webhook/dashboard.
 *
 * Covers:
 *   1. Valid API key  → 200 with correct aggregated data
 *   2. Missing API key → 401
 *   3. Invalid API key → 403
 *   4. ?date=YYYY-MM  → that month's data only
 *   5. Adjacent months → each month's data isolated
 *   6. Default date    → current month
 *   7. Invalid date format → 400
 *   8. No expenses     → zero totals
 *   9. Category breakdown accuracy
 *  10. Top merchants   → sorted by total descending, max 5
 *  11. Budget status   → included in response
 *  12. Multi-user isolation
 *  13. Account balances → netWorth, totalAssets, totalDebt, accountBalances
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("GET /api/webhook/dashboard")
class WebhookDashboardIntegrationTest {

    private static final String VALID_API_KEY = "dashboard-test-api-key-0001";
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
    private AccountRepository accountRepository;

    @MockBean
    private DataMigrationRunner dataMigrationRunner;

    @MockBean
    private SchemaMigrationRunner schemaMigrationRunner;

    private User testUser;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        budgetRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("dashboard-tester@example.com")
                .password("encoded-password")
                .displayName("Dashboard Tester")
                .apiKey(VALID_API_KEY)
                .build());
    }

    // =====================================================================
    //  Authentication
    // =====================================================================

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        void missingApiKey_returns401() throws Exception {
            mockMvc.perform(get("/api/webhook/dashboard"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message", containsString("Missing API key")));
        }

        @Test
        void invalidApiKey_returns403() throws Exception {
            mockMvc.perform(get("/api/webhook/dashboard")
                            .header("X-API-Key", INVALID_API_KEY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message", containsString("Invalid API key")));
        }
    }

    // =====================================================================
    //  Happy path — valid API key with expense, budget, and account data
    // =====================================================================

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @BeforeEach
        void setUpData() {
            // Create a budget
            budgetRepository.save(Budget.builder()
                    .categories(List.of("Groceries"))
                    .monthlyLimit(new BigDecimal("1500.00"))
                    .user(testUser)
                    .build());

            // Current-month expenses across multiple categories and merchants
            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("100.00"))
                    .category("Groceries")
                    .merchant("Walmart")
                    
                    .timestamp(CURRENT_MONTH.atDay(1).atTime(10, 0))
                    .user(testUser)
                    .build());

            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("50.00"))
                    .category("Groceries")
                    .merchant("Costco")
                    
                    .timestamp(CURRENT_MONTH.atDay(5).atTime(14, 0))
                    .user(testUser)
                    .build());

            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("30.00"))
                    .category("Restaurants")
                    .merchant("McDonald's")
                    
                    .timestamp(CURRENT_MONTH.atDay(10).atTime(12, 0))
                    .user(testUser)
                    .build());

            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("15.00"))
                    .category("Restaurants")
                    .merchant("Tim Hortons")
                    
                    .timestamp(CURRENT_MONTH.atDay(15).atTime(8, 0))
                    .user(testUser)
                    .build());

            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("75.00"))
                    .category("Gas & Fuel")
                    .merchant("Shell")
                    
                    .timestamp(CURRENT_MONTH.atDay(20).atTime(9, 0))
                    .user(testUser)
                    .build());

            // Accounts: BASE(5000), SAVINGS(10000), CREDIT(-1500)
            accountRepository.save(Account.builder()
                    .name("Checking")
                    .type(AccountType.BASE)
                    .balance(new BigDecimal("5000.00"))
                    .user(testUser)
                    .build());

            accountRepository.save(Account.builder()
                    .name("Savings")
                    .type(AccountType.SAVINGS)
                    .balance(new BigDecimal("10000.00"))
                    .user(testUser)
                    .build());

            accountRepository.save(Account.builder()
                    .name("Credit Card")
                    .type(AccountType.CREDIT)
                    .balance(new BigDecimal("1500.00"))
                    .user(testUser)
                    .build());
        }

        @Test
        void validApiKey_returns200_withCorrectAggregates() throws Exception {
            // Total spent: 100 + 50 + 30 + 15 + 75 = 270
            // Transactions: 5
            // Category breakdown: Groceries(150,2), Restaurants(45,2), Gas & Fuel(75,1)
            // Top merchants: Walmart(100), Shell(75), Costco(50), McDonald's(30), Tim Hortons(15)
            // Budget: Groceries(limit=1500, spent=150, percentage=10.00)
            // Accounts: BASE(5000) + SAVINGS(10000) = 15000 assets, CREDIT(1500) debt
            // netWorth = 15000 - 1500 = 13500
            mockMvc.perform(get("/api/webhook/dashboard")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    // totalSpent
                    .andExpect(jsonPath("$.totalSpent").value(closeTo(270.0, 0.01)))
                    // transactionCount
                    .andExpect(jsonPath("$.transactionCount").value(5))
                    // categoryBreakdown: 3 categories
                    .andExpect(jsonPath("$.categoryBreakdown").isMap())
                    .andExpect(jsonPath("$.categoryBreakdown.Groceries.total").value(closeTo(150.0, 0.01)))
                    .andExpect(jsonPath("$.categoryBreakdown.Groceries.count").value(2))
                    .andExpect(jsonPath("$.categoryBreakdown.Restaurants.total").value(closeTo(45.0, 0.01)))
                    .andExpect(jsonPath("$.categoryBreakdown.Restaurants.count").value(2))
                    .andExpect(jsonPath("$.categoryBreakdown['Gas & Fuel'].total").value(closeTo(75.0, 0.01)))
                    .andExpect(jsonPath("$.categoryBreakdown['Gas & Fuel'].count").value(1))
                    // topMerchants: 5 entries sorted by total descending
                    .andExpect(jsonPath("$.topMerchants.length()").value(5))
                    .andExpect(jsonPath("$.topMerchants[0].merchant").value("Walmart"))
                    .andExpect(jsonPath("$.topMerchants[0].total").value(closeTo(100.0, 0.01)))
                    .andExpect(jsonPath("$.topMerchants[0].count").value(1))
                    .andExpect(jsonPath("$.topMerchants[1].merchant").value("Shell"))
                    .andExpect(jsonPath("$.topMerchants[1].total").value(closeTo(75.0, 0.01)))
                    .andExpect(jsonPath("$.topMerchants[2].merchant").value("Costco"))
                    .andExpect(jsonPath("$.topMerchants[2].total").value(closeTo(50.0, 0.01)))
                    .andExpect(jsonPath("$.topMerchants[3].merchant").value("McDonald's"))
                    .andExpect(jsonPath("$.topMerchants[4].merchant").value("Tim Hortons"))
                    // budgetStatus: Groceries budget
                    .andExpect(jsonPath("$.budgetStatus.budgets.length()").value(1))
                    .andExpect(jsonPath("$.budgetStatus.budgets[0].category").value("Groceries"))
                    .andExpect(jsonPath("$.budgetStatus.budgets[0].limit").value(closeTo(1500.0, 0.01)))
                    .andExpect(jsonPath("$.budgetStatus.budgets[0].spent").value(closeTo(150.0, 0.01)))
                    .andExpect(jsonPath("$.budgetStatus.budgets[0].percentage").value(closeTo(10.0, 0.01)))
                    // Account balances: totalAssets = 5000 + 10000 = 15000
                    .andExpect(jsonPath("$.totalAssets").value(closeTo(15000.0, 0.01)))
                    // totalDebt = 1500
                    .andExpect(jsonPath("$.totalDebt").value(closeTo(1500.0, 0.01)))
                    // netWorth = 15000 - 1500 = 13500
                    .andExpect(jsonPath("$.netWorth").value(closeTo(13500.0, 0.01)))
                    // accountBalances: 3 accounts
                    .andExpect(jsonPath("$.accountBalances.length()").value(3))
                    .andExpect(jsonPath("$.accountBalances[0].name").value("Checking"))
                    .andExpect(jsonPath("$.accountBalances[0].type").value("BASE"))
                    .andExpect(jsonPath("$.accountBalances[0].balance").value(closeTo(5000.0, 0.01)))
                    .andExpect(jsonPath("$.accountBalances[1].name").value("Savings"))
                    .andExpect(jsonPath("$.accountBalances[1].type").value("SAVINGS"))
                    .andExpect(jsonPath("$.accountBalances[1].balance").value(closeTo(10000.0, 0.01)))
                    .andExpect(jsonPath("$.accountBalances[2].name").value("Credit Card"))
                    .andExpect(jsonPath("$.accountBalances[2].type").value("CREDIT"))
                    .andExpect(jsonPath("$.accountBalances[2].balance").value(closeTo(1500.0, 0.01)));
        }
    }

    // =====================================================================
    //  No data edge cases
    // =====================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        void noExpensesAndNoAccounts_returnsZeroTotals() throws Exception {
            mockMvc.perform(get("/api/webhook/dashboard")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpent").value(0))
                    .andExpect(jsonPath("$.transactionCount").value(0))
                    .andExpect(jsonPath("$.categoryBreakdown").isEmpty())
                    .andExpect(jsonPath("$.topMerchants").isEmpty())
                    .andExpect(jsonPath("$.budgetStatus.budgets").isEmpty())
                    // No accounts: all zeros
                    .andExpect(jsonPath("$.netWorth").value(0))
                    .andExpect(jsonPath("$.totalAssets").value(0))
                    .andExpect(jsonPath("$.totalDebt").value(0))
                    .andExpect(jsonPath("$.accountBalances").isEmpty());
        }

        @Test
        void accountsWithoutExpenses_returnsNetWorth() throws Exception {
            accountRepository.save(Account.builder()
                    .name("Checking")
                    .type(AccountType.BASE)
                    .balance(new BigDecimal("3000.00"))
                    .user(testUser)
                    .build());

            accountRepository.save(Account.builder()
                    .name("Credit Card")
                    .type(AccountType.CREDIT)
                    .balance(new BigDecimal("500.00"))
                    .user(testUser)
                    .build());

            mockMvc.perform(get("/api/webhook/dashboard")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpent").value(0))
                    .andExpect(jsonPath("$.totalAssets").value(closeTo(3000.0, 0.01)))
                    .andExpect(jsonPath("$.totalDebt").value(closeTo(500.0, 0.01)))
                    .andExpect(jsonPath("$.netWorth").value(closeTo(2500.0, 0.01)))
                    .andExpect(jsonPath("$.accountBalances.length()").value(2));
        }

        @Test
        void moreThanFiveMerchants_returnsOnlyTopFive() throws Exception {
            for (int i = 1; i <= 10; i++) {
                expenseRepository.save(Expense.builder()
                        .amount(BigDecimal.valueOf(i * 10))
                        .category("Groceries")
                        .merchant("Merchant" + i)
                        
                        .timestamp(CURRENT_MONTH.atDay(i).atTime(12, 0))
                        .user(testUser)
                        .build());
            }

            mockMvc.perform(get("/api/webhook/dashboard")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.topMerchants.length()").value(5))
                    .andExpect(jsonPath("$.topMerchants[0].merchant").value("Merchant10"))
                    .andExpect(jsonPath("$.topMerchants[4].merchant").value("Merchant6"));
        }

        @Test
        void sameCategoryAndMerchant_aggregatesCorrectly() throws Exception {
            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("25.00"))
                    .category("Groceries")
                    .merchant("Walmart")
                    
                    .timestamp(CURRENT_MONTH.atDay(1).atTime(10, 0))
                    .user(testUser)
                    .build());

            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("35.00"))
                    .category("Groceries")
                    .merchant("Walmart")
                    
                    .timestamp(CURRENT_MONTH.atDay(5).atTime(14, 0))
                    .user(testUser)
                    .build());

            mockMvc.perform(get("/api/webhook/dashboard")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transactionCount").value(2))
                    .andExpect(jsonPath("$.totalSpent").value(closeTo(60.0, 0.01)))
                    .andExpect(jsonPath("$.categoryBreakdown.Groceries.total").value(closeTo(60.0, 0.01)))
                    .andExpect(jsonPath("$.categoryBreakdown.Groceries.count").value(2))
                    .andExpect(jsonPath("$.topMerchants[0].merchant").value("Walmart"))
                    .andExpect(jsonPath("$.topMerchants[0].total").value(closeTo(60.0, 0.01)))
                    .andExpect(jsonPath("$.topMerchants[0].count").value(2));
        }
    }

    // =====================================================================
    //  Date parameter
    // =====================================================================

    @Nested
    @DisplayName("Date parameter")
    class DateParameter {

        @BeforeEach
        void setUpDateData() {
            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("100.00"))
                    .category("Groceries")
                    .merchant("Walmart")
                    
                    .timestamp(CURRENT_MONTH.atDay(15).atTime(10, 0))
                    .user(testUser)
                    .build());

            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("200.00"))
                    .category("Restaurants")
                    .merchant("Olive Garden")
                    
                    .timestamp(NEXT_MONTH.atDay(3).atTime(19, 0))
                    .user(testUser)
                    .build());

            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("50.00"))
                    .category("Groceries")
                    .merchant("Metro")
                    
                    .timestamp(PREVIOUS_MONTH.atDay(20).atTime(16, 0))
                    .user(testUser)
                    .build());
        }

        private static Stream<Arguments> dateFilterArgs() {
            return Stream.of(
                    Arguments.of(CURRENT_MONTH.toString(), 1, 100.0, "Groceries", 100.0),
                    Arguments.of(NEXT_MONTH.toString(), 1, 200.0, "Restaurants", 200.0),
                    Arguments.of(PREVIOUS_MONTH.toString(), 1, 50.0, "Groceries", 50.0)
            );
        }

        @ParameterizedTest(name = "date={0} → {1} txns, ${2}")
        @MethodSource("dateFilterArgs")
        void withDate_returnsDataForThatMonth(String date, int expectedCount, double expectedTotal,
                                              String expectedCategory, double expectedCategoryTotal) throws Exception {
            mockMvc.perform(get("/api/webhook/dashboard")
                            .param("date", date)
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transactionCount").value(expectedCount))
                    .andExpect(jsonPath("$.totalSpent").value(closeTo(expectedTotal, 0.01)))
                    .andExpect(jsonPath("$.categoryBreakdown['" + expectedCategory + "'].total")
                            .value(closeTo(expectedCategoryTotal, 0.01)));
        }

        @Test
        void withoutDate_usesCurrentMonth() throws Exception {
            // Only the current-month expense (100) falls in the default window
            mockMvc.perform(get("/api/webhook/dashboard")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpent").value(closeTo(100.0, 0.01)));
        }

        @ParameterizedTest(name = "invalid date \"{0}\" → 400")
        @CsvSource({
            "2026/06",
            "foobar",
            "2026-06-15",
            "not-a-date",
            "2026.06",
            "06-2026"
        })
        void invalidDateFormat_returns400(String invalidDate) throws Exception {
            mockMvc.perform(get("/api/webhook/dashboard")
                            .param("date", invalidDate)
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isBadRequest());
        }
    }

    // =====================================================================
    //  Multi-user isolation
    // =====================================================================

    @Nested
    @DisplayName("User isolation")
    class UserIsolation {

        @Test
        void otherUserData_notVisible() throws Exception {
            User otherUser = userRepository.save(User.builder()
                    .email("other@example.com")
                    .password("encoded-pw")
                    .displayName("Other")
                    .apiKey("other-user-api-key-9999")
                    .build());

            // Other user has expenses and accounts
            expenseRepository.save(Expense.builder()
                    .amount(new BigDecimal("999.99"))
                    .category("Electronics")
                    .merchant("Best Buy")
                    
                    .timestamp(CURRENT_MONTH.atDay(1).atTime(10, 0))
                    .user(otherUser)
                    .build());

            accountRepository.save(Account.builder()
                    .name("Other Checking")
                    .type(AccountType.BASE)
                    .balance(new BigDecimal("99999.00"))
                    .user(otherUser)
                    .build());

            // Test user sees own data (empty)
            mockMvc.perform(get("/api/webhook/dashboard")
                            .header("X-API-Key", VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSpent").value(0))
                    .andExpect(jsonPath("$.transactionCount").value(0))
                    .andExpect(jsonPath("$.netWorth").value(0))
                    .andExpect(jsonPath("$.totalAssets").value(0))
                    .andExpect(jsonPath("$.totalDebt").value(0))
                    .andExpect(jsonPath("$.accountBalances").isEmpty());
        }
    }
}
