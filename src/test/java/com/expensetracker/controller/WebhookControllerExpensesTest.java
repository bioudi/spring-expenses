package com.expensetracker.controller;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for GET /api/webhook/expenses.
 *
 * Covers:
 *   1. Valid API key → 200 + JSON array
 *   2. Missing API key → 401
 *   3. Blank API key → 401
 *   4. Invalid API key → 403
 *   5. Filter by startDate / endDate / startDate+endDate / category
 *   6. Limit parameter (positive, higher-than-count, zero, negative, non-numeric)
 *   7. Combined filters
 *   8. Invalid date format → 400
 *
 * Uses @SpringBootTest with real Spring context so ApiKeyFilter is wired into
 * the filter chain (same pattern as WebhookBudgetsIntegrationTest /
 * WebhookDashboardIntegrationTest). ExpenseService.getExpenses is mocked so we
 * can verify query-param parsing without seeding the DB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("GET /api/webhook/expenses")
class WebhookControllerExpensesTest {

    private static final String VALID_API_KEY = "expenses-test-api-key-0001";
    private static final String INVALID_API_KEY = "i-do-not-exist";
    private static final String API_KEY_HEADER = "X-API-Key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private ExpenseService expenseService;

    // Suppress side-effect runners that aren't needed for tests
    @MockBean
    private DataMigrationRunner dataMigrationRunner;

    @MockBean
    private SchemaMigrationRunner schemaMigrationRunner;

    private User testUser;
    private UUID testUserId;

    private ExpenseResponse expense1;
    private ExpenseResponse expense2;
    private ExpenseResponse expense3;

    @BeforeEach
    void setUp() {
        // Clean up only this test's user (created in the previous run).
        // Do NOT deleteAll() globally — other tests in the suite may be running
        // in parallel and their budgets/expenses reference their own users.
        // FK constraints would break them if we nuked their users.
        userRepository.findByApiKey(VALID_API_KEY).ifPresent(userRepository::delete);

        testUser = userRepository.save(User.builder()
                .email("expenses-tester@example.com")
                .password("encoded-password")
                .displayName("Expenses Tester")
                .apiKey(VALID_API_KEY)
                .build());
        testUserId = testUser.getId();

        expense1 = ExpenseResponse.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("12.50"))
                .category("Groceries")
                .merchant("Walmart")
                .timestamp(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build();

        expense2 = ExpenseResponse.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("45.00"))
                .category("Restaurants")
                .merchant("McDonald's")
                .timestamp(LocalDateTime.of(2026, 6, 5, 12, 30))
                .build();

        expense3 = ExpenseResponse.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("8.99"))
                .category("Groceries")
                .merchant("Costco")
                .timestamp(LocalDateTime.of(2026, 6, 10, 15, 0))
                .build();
    }

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("Returns 200 with valid API key")
        void validApiKeyReturns200() throws Exception {
            when(expenseService.getExpenses(eq(null), eq(null), eq(null), eq(testUserId)))
                    .thenReturn(List.of(expense1, expense2));

            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].merchant").value("Walmart"))
                    .andExpect(jsonPath("$[1].merchant").value("McDonald's"));
        }

        @Test
        @DisplayName("Returns 401 when API key header is missing")
        void missingApiKeyReturns401() throws Exception {
            mockMvc.perform(get("/api/webhook/expenses"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message", containsString("Missing API key")));
        }

        @Test
        @DisplayName("Returns 401 when API key header is blank")
        void blankApiKeyReturns401() throws Exception {
            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, ""))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message", containsString("Missing API key")));
        }

        @Test
        @DisplayName("Returns 403 when API key is invalid")
        void invalidApiKeyReturns403() throws Exception {
            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, INVALID_API_KEY))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message", containsString("Invalid API key")));
        }
    }

    @Nested
    @DisplayName("Query parameters")
    class QueryParameters {

        @Test
        @DisplayName("Returns empty list when no expenses exist")
        void noExpensesReturnsEmptyArray() throws Exception {
            when(expenseService.getExpenses(eq(null), eq(null), eq(null), eq(testUserId)))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Filters by startDate")
        void filterByStartDate() throws Exception {
            LocalDate start = LocalDate.of(2026, 6, 5);
            when(expenseService.getExpenses(eq(start), eq(null), eq(null), eq(testUserId)))
                    .thenReturn(List.of(expense2, expense3));

            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("startDate", "2026-06-05"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("Filters by endDate")
        void filterByEndDate() throws Exception {
            LocalDate end = LocalDate.of(2026, 6, 5);
            when(expenseService.getExpenses(eq(null), eq(end), eq(null), eq(testUserId)))
                    .thenReturn(List.of(expense1, expense2));

            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("endDate", "2026-06-05"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("Filters by startDate and endDate")
        void filterByStartAndEndDate() throws Exception {
            LocalDate start = LocalDate.of(2026, 6, 1);
            LocalDate end = LocalDate.of(2026, 6, 5);
            when(expenseService.getExpenses(eq(start), eq(end), eq(null), eq(testUserId)))
                    .thenReturn(List.of(expense1, expense2));

            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("startDate", "2026-06-01")
                            .param("endDate", "2026-06-05"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("Filters by category")
        void filterByCategory() throws Exception {
            when(expenseService.getExpenses(eq(null), eq(null), eq("Groceries"), eq(testUserId)))
                    .thenReturn(List.of(expense1, expense3));

            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("category", "Groceries"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].category").value("Groceries"))
                    .andExpect(jsonPath("$[1].category").value("Groceries"));
        }

        @Test
        @DisplayName("Applies limit parameter")
        void limitParameter() throws Exception {
            when(expenseService.getExpenses(eq(null), eq(null), eq(null), eq(testUserId)))
                    .thenReturn(List.of(expense1, expense2, expense3));

            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("Combines multiple filters: startDate, endDate, category, and limit")
        void combinedFilters() throws Exception {
            LocalDate start = LocalDate.of(2026, 6, 1);
            LocalDate end = LocalDate.of(2026, 6, 10);
            when(expenseService.getExpenses(eq(start), eq(end), eq("Groceries"), eq(testUserId)))
                    .thenReturn(List.of(expense1, expense3));

            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("startDate", "2026-06-01")
                            .param("endDate", "2026-06-10")
                            .param("category", "Groceries")
                            .param("limit", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].merchant").value("Walmart"));
        }

        @Test
        @DisplayName("Limit higher than result count returns all results")
        void limitHigherThanCount() throws Exception {
            when(expenseService.getExpenses(eq(null), eq(null), eq(null), eq(testUserId)))
                    .thenReturn(List.of(expense1, expense2));

            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("limit", "999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("Returns 400 when startDate has invalid format")
        void invalidStartDateFormat() throws Exception {
            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("startDate", "01-06-2026"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when endDate has invalid format")
        void invalidEndDateFormat() throws Exception {
            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("endDate", "2026/06/01"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when limit is not a number")
        void limitNotANumber() throws Exception {
            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("limit", "abc"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when limit is zero")
        void limitIsZero() throws Exception {
            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("limit", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when limit is negative")
        void limitIsNegative() throws Exception {
            mockMvc.perform(get("/api/webhook/expenses")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .param("limit", "-5"))
                    .andExpect(status().isBadRequest());
        }
    }
}
