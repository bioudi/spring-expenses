package com.expensetracker.controller;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/webhook/expense — account_id wiring.
 *
 * Covers the acceptance criteria from the spec:
 *   1. POST with a valid account_id → 201, expense linked, balance deducted.
 *   2. POST without account_id → 201, no link, balance untouched.
 *   3. POST with a non-existent account_id → 400 with a clear error message,
 *      and NO expense record / balance change committed.
 *   4. POST with another user's account_id → 400 (treated as invalid for this user).
 *   5. Authentication: missing / blank / invalid API key returns 401 / 403.
 *
 * Uses the same wiring as the other webhook test classes:
 *   - @SpringBootTest for a real context (so ApiKeyFilter participates in the filter chain)
 *   - ApiKeyFilter resolves the user from the X-API-Key header and installs a
 *     SecurityContext that SecurityUtils.getCurrentUserId() can read.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("POST /api/webhook/expense — account_id wiring")
class WebhookControllerExpenseCreateTest {

    private static final String VALID_API_KEY = "webhook-expense-create-api-key-0001";
    private static final String INVALID_API_KEY = "i-do-not-exist";
    private static final String API_KEY_HEADER = "X-API-Key";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ExpenseRepository expenseRepository;

    @MockBean private DataMigrationRunner dataMigrationRunner;
    @MockBean private SchemaMigrationRunner schemaMigrationRunner;

    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        // Clean only this test's data so we don't disturb parallel test classes.
        userRepository.findByApiKey(VALID_API_KEY).ifPresent(user -> {
            expenseRepository.deleteAll(
                    expenseRepository.findAllByUserIdOrderByTimestampDesc(user.getId()));
            List<Account> userAccounts = accountRepository.findByUserIdOrderByCreatedAtAsc(user.getId());
            accountRepository.deleteAll(userAccounts);
            userRepository.delete(user);
        });

        testUser = userRepository.save(User.builder()
                .email("webhook-expense-create@example.com")
                .password("encoded-password")
                .displayName("Webhook Expense Tester")
                .apiKey(VALID_API_KEY)
                .build());

        testAccount = accountRepository.save(Account.builder()
                .name("Webhook Test Checking")
                .balance(new BigDecimal("500.00"))
                .type(AccountType.BASE)
                .user(testUser)
                .build());
    }

    // ─── Positive path ────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("With valid account_id: 201, expense linked, balance deducted")
        void withValidAccountId_createsAndDeducts() throws Exception {
            ExpenseRequest request = ExpenseRequest.builder()
                    .amount(new BigDecimal("42.50"))
                    .merchant("Walmart")
                    .category("Groceries")
                    .paymentMethod("Card")
                    .accountId(testAccount.getId())
                    .build();

            mockMvc.perform(post("/api/webhook/expense")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.amount").value(42.50))
                    .andExpect(jsonPath("$.merchant").value("Walmart"))
                    .andExpect(jsonPath("$.accountId").value(testAccount.getId().toString()));

            // Expense persisted
            List<Expense> persisted = expenseRepository
                    .findAllByUserIdOrderByTimestampDesc(testUser.getId());
            assertThat(persisted).hasSize(1);
            assertThat(persisted.get(0).getAccount()).isNotNull();
            assertThat(persisted.get(0).getAccount().getId()).isEqualTo(testAccount.getId());

            // Balance deducted 500 - 42.50 = 457.50
            Account updated = accountRepository.findById(testAccount.getId()).orElseThrow();
            assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("457.50"));
        }

        @Test
        @DisplayName("Without account_id: 201, no account link, balance untouched")
        void withoutAccountId_createsWithoutLink() throws Exception {
            ExpenseRequest request = ExpenseRequest.builder()
                    .amount(new BigDecimal("15.00"))
                    .merchant("Coffee Shop")
                    .category("Coffee & Cafes")
                    .paymentMethod("Cash")
                    .build();

            mockMvc.perform(post("/api/webhook/expense")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.amount").value(15.00))
                    .andExpect(jsonPath("$.accountId").doesNotExist());

            // Expense persisted without account link
            List<Expense> persisted = expenseRepository
                    .findAllByUserIdOrderByTimestampDesc(testUser.getId());
            assertThat(persisted).hasSize(1);
            assertThat(persisted.get(0).getAccount()).isNull();

            // Balance unchanged
            Account untouched = accountRepository.findById(testAccount.getId()).orElseThrow();
            assertThat(untouched.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("With credit account: balance moves more negative, expense persists")
        void withCreditAccount_balanceDeducts() throws Exception {
            Account credit = accountRepository.save(Account.builder()
                    .name("Webhook Test Credit Card")
                    .balance(new BigDecimal("-100.00"))
                    .type(AccountType.CREDIT)
                    .user(testUser)
                    .build());

            ExpenseRequest request = ExpenseRequest.builder()
                    .amount(new BigDecimal("75.00"))
                    .merchant("Amazon")
                    .category("Electronics")
                    .paymentMethod("Card")
                    .accountId(credit.getId())
                    .build();

            mockMvc.perform(post("/api/webhook/expense")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accountId").value(credit.getId().toString()));

            // Credit balance: -100 - 75 = -175
            Account updated = accountRepository.findById(credit.getId()).orElseThrow();
            assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("-175.00"));
        }
    }

    // ─── Negative path ────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid account_id")
    class InvalidAccountId {

        @Test
        @DisplayName("Non-existent UUID: 400 with clear message, no expense persisted")
        void nonExistentAccountId_returns400() throws Exception {
            UUID ghostId = UUID.randomUUID();
            ExpenseRequest request = ExpenseRequest.builder()
                    .amount(new BigDecimal("30.00"))
                    .merchant("Ghost Merchant")
                    .category("Other")
                    .paymentMethod("Cash")
                    .accountId(ghostId)
                    .build();

            mockMvc.perform(post("/api/webhook/expense")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid account_id"))
                    .andExpect(jsonPath("$.message", containsString(ghostId.toString())));

            // No expense should have been persisted
            assertThat(expenseRepository.findAllByUserIdOrderByTimestampDesc(testUser.getId()))
                    .isEmpty();

            // Existing account balance untouched
            Account untouched = accountRepository.findById(testAccount.getId()).orElseThrow();
            assertThat(untouched.getBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("Another user's account_id: 400 (not visible to this user)")
        void anotherUsersAccountId_returns400() throws Exception {
            // A second user owns a valid account — but the first user must not be able
            // to deduct from it just by guessing the id.
            User other = userRepository.save(User.builder()
                    .email("other-webhook-user@example.com")
                    .password("encoded-password")
                    .displayName("Other User")
                    .apiKey("other-webhook-user-api-key-0002")
                    .build());
            Account otherAccount = accountRepository.save(Account.builder()
                    .name("Other User Checking")
                    .balance(new BigDecimal("999.00"))
                    .type(AccountType.BASE)
                    .user(other)
                    .build());

            ExpenseRequest request = ExpenseRequest.builder()
                    .amount(new BigDecimal("20.00"))
                    .merchant("Cross-user")
                    .category("Other")
                    .paymentMethod("Cash")
                    .accountId(otherAccount.getId())
                    .build();

            mockMvc.perform(post("/api/webhook/expense")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid account_id"))
                    .andExpect(jsonPath("$.message", containsString(otherAccount.getId().toString())));

            // No expense persisted for the requesting user
            assertThat(expenseRepository.findAllByUserIdOrderByTimestampDesc(testUser.getId()))
                    .isEmpty();

            // Other user's account balance untouched
            Account otherAfter = accountRepository.findById(otherAccount.getId()).orElseThrow();
            assertThat(otherAfter.getBalance()).isEqualByComparingTo(new BigDecimal("999.00"));
        }
    }

    // ─── Authentication ───────────────────────────────────────────────

    @Nested
    @DisplayName("Authentication")
    class Authentication {

        @Test
        @DisplayName("Returns 401 when X-API-Key header is missing")
        void missingApiKey_returns401() throws Exception {
            ExpenseRequest request = ExpenseRequest.builder()
                    .amount(new BigDecimal("10.00"))
                    .merchant("Test")
                    .category("Other")
                    .build();

            mockMvc.perform(post("/api/webhook/expense")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Returns 401 when X-API-Key header is blank")
        void blankApiKey_returns401() throws Exception {
            ExpenseRequest request = ExpenseRequest.builder()
                    .amount(new BigDecimal("10.00"))
                    .merchant("Test")
                    .category("Other")
                    .build();

            mockMvc.perform(post("/api/webhook/expense")
                            .header(API_KEY_HEADER, "")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Returns 403 when API key is invalid")
        void invalidApiKey_returns403() throws Exception {
            ExpenseRequest request = ExpenseRequest.builder()
                    .amount(new BigDecimal("10.00"))
                    .merchant("Test")
                    .category("Other")
                    .build();

            mockMvc.perform(post("/api/webhook/expense")
                            .header(API_KEY_HEADER, INVALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    // ─── Validation ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("Returns 400 when amount is missing")
        void missingAmount_returns400() throws Exception {
            // No amount on purpose
            String body = "{\"merchant\":\"X\",\"category\":\"Other\"}";

            mockMvc.perform(post("/api/webhook/expense")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Returns 400 when account_id is not a valid UUID")
        void malformedAccountId_returns400() throws Exception {
            String body = "{\"amount\":10,\"merchant\":\"X\",\"category\":\"Other\",\"accountId\":\"not-a-uuid\"}";

            mockMvc.perform(post("/api/webhook/expense")
                            .header(API_KEY_HEADER, VALID_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
    }
}