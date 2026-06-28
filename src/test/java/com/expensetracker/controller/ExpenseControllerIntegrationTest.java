package com.expensetracker.controller;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.User;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseControllerIntegrationTest {

    private static final String TEST_EMAIL = "expense-integration@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @MockBean
    private DataMigrationRunner dataMigrationRunner;

    @MockBean
    private SchemaMigrationRunner schemaMigrationRunner;

    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        // Clean up only this test's data to avoid interfering with other
        // integration test classes running in the same JVM.
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> {
            expenseRepository.deleteAll(
                    expenseRepository.findAllByUserIdOrderByTimestampDesc(user.getId()));
            accountRepository.deleteAll(
                    accountRepository.findByUserIdOrderByCreatedAtAsc(user.getId()));
            userRepository.delete(user);
        });

        testUser = userRepository.save(User.builder()
                .email(TEST_EMAIL)
                .password("$2a$10$dummy.bcrypt.password.that.does.not.matter")
                .displayName("Expense Test User")
                .apiKey(UUID.randomUUID().toString())
                .build());

        testAccount = accountRepository.save(Account.builder()
                .name("Test Checking")
                .balance(new BigDecimal("1000.00"))
                .type(AccountType.BASE)
                .user(testUser)
                .build());

        // Set up security context manually (runs before @WithUserDetails would resolve)
        UserPrincipal principal = new UserPrincipal(
                testUser.getId(),
                testUser.getEmail(),
                testUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── POST /api/expenses ────────────────────────────────────

    @Test
    void createExpense_withoutAccount_returnsCreated() throws Exception {
        ExpenseRequest request = ExpenseRequest.builder()
                .amount(new BigDecimal("45.00"))
                .merchant("Walmart")
                .category("Groceries")
                .paymentMethod("Cash")
                .build();

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(45.00))
                .andExpect(jsonPath("$.merchant").value("Walmart"))
                .andExpect(jsonPath("$.accountId").isEmpty());
    }

    @Test
    void createExpense_withAccount_decreasesBalance() throws Exception {
        ExpenseRequest request = ExpenseRequest.builder()
                .amount(new BigDecimal("150.00"))
                .merchant("Costco")
                .category("Groceries")
                .paymentMethod("Card")
                .accountId(testAccount.getId())
                .build();

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(testAccount.getId().toString()));

        // Balance should be 1000 - 150 = 850
        Account updated = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("850.00"));
    }

    @Test
    void createExpense_withCreditAccount_decreasesBalance() throws Exception {
        Account creditAccount = accountRepository.save(Account.builder()
                .name("Test Credit Card")
                .balance(new BigDecimal("-500.00"))
                .type(AccountType.CREDIT)
                .user(testUser)
                .build());

        ExpenseRequest request = ExpenseRequest.builder()
                .amount(new BigDecimal("200.00"))
                .merchant("Amazon")
                .category("Electronics")
                .paymentMethod("Card")
                .accountId(creditAccount.getId())
                .build();

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Credit balance should be -500 - 200 = -700
        Account updated = accountRepository.findById(creditAccount.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("-700.00"));
    }

    @Test
    void createExpense_amountExceedsBalance_returnsUnprocessableEntity() throws Exception {
        // testAccount starts at 1000.00 (see setUp)
        ExpenseRequest request = ExpenseRequest.builder()
                .amount(new BigDecimal("999999.00"))
                .merchant("QA Test")
                .category("Other")
                .paymentMethod("Card")
                .accountId(testAccount.getId())
                .build();

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Insufficient Funds"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Insufficient funds")));

        // Balance must NOT have been mutated
        Account unchanged = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(unchanged.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void createExpense_amountExactlyBalance_succeeds() throws Exception {
        // Boundary: amount == balance must succeed (zero balance is allowed).
        ExpenseRequest request = ExpenseRequest.builder()
                .amount(new BigDecimal("1000.00"))
                .merchant("Edge Case")
                .category("Other")
                .paymentMethod("Card")
                .accountId(testAccount.getId())
                .build();

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Account updated = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createExpense_amountExceedsBalanceOnCreditAccount_succeeds() throws Exception {
        // CREDIT accounts track outstanding debt — must be allowed to go more negative.
        Account creditAccount = accountRepository.save(Account.builder()
                .name("Test Credit")
                .balance(new BigDecimal("0.00"))
                .type(AccountType.CREDIT)
                .user(testUser)
                .build());

        ExpenseRequest request = ExpenseRequest.builder()
                .amount(new BigDecimal("5000.00"))
                .merchant("Big Spender")
                .category("Other")
                .paymentMethod("Card")
                .accountId(creditAccount.getId())
                .build();

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Account updated = accountRepository.findById(creditAccount.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("-5000.00"));
    }

    @Test
    void createExpense_invalidAccount_returnsNotFound() throws Exception {
        ExpenseRequest request = ExpenseRequest.builder()
                .amount(new BigDecimal("50.00"))
                .merchant("Bad Account")
                .category("Other")
                .accountId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/expenses/{id} ────────────────────────────────

    @Test
    void updateExpense_withSameAccount_adjustsBalance() throws Exception {
        // Create expense with account
        ExpenseRequest createReq = ExpenseRequest.builder()
                .amount(new BigDecimal("100.00"))
                .merchant("Grocery")
                .category("Groceries")
                .paymentMethod("Card")
                .accountId(testAccount.getId())
                .build();

        String createBody = mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ExpenseResponse created = objectMapper.readValue(createBody, ExpenseResponse.class);
        // Balance = 1000 - 100 = 900
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("900.00"));

        // Update: change amount to 150 (same account)
        ExpenseRequest updateReq = ExpenseRequest.builder()
                .amount(new BigDecimal("150.00"))
                .merchant("Updated Grocery")
                .category("Groceries")
                .paymentMethod("Card")
                .accountId(testAccount.getId())
                .build();

        mockMvc.perform(put("/api/expenses/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(150.00));

        // Balance should revert 100 then deduct 150: 900 + 100 - 150 = 850
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("850.00"));
    }

    @Test
    void updateExpense_withDifferentAccount_switchesBalance() throws Exception {
        // Create a second account
        Account secondAccount = accountRepository.save(Account.builder()
                .name("Savings")
                .balance(new BigDecimal("2000.00"))
                .type(AccountType.SAVINGS)
                .user(testUser)
                .build());

        // Create expense linked to first account
        ExpenseRequest createReq = ExpenseRequest.builder()
                .amount(new BigDecimal("300.00"))
                .merchant("Best Buy")
                .category("Electronics")
                .paymentMethod("Card")
                .accountId(testAccount.getId())
                .build();

        String createBody = mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ExpenseResponse created = objectMapper.readValue(createBody, ExpenseResponse.class);
        // Checking balance = 1000 - 300 = 700
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("700.00"));

        // Update: switch to second account with amount 250
        ExpenseRequest updateReq = ExpenseRequest.builder()
                .amount(new BigDecimal("250.00"))
                .merchant("Best Buy")
                .category("Electronics")
                .paymentMethod("Card")
                .accountId(secondAccount.getId())
                .build();

        mockMvc.perform(put("/api/expenses/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // Checking balance: 700 + 300 (restored) = 1000
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
        // Savings balance: 2000 - 250 = 1750
        assertThat(accountRepository.findById(secondAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1750.00"));
    }

    @Test
    void updateExpense_removeAccount_reversesOldBalance() throws Exception {
        ExpenseRequest createReq = ExpenseRequest.builder()
                .amount(new BigDecimal("75.00"))
                .merchant("Restaurant")
                .category("Restaurants")
                .paymentMethod("Card")
                .accountId(testAccount.getId())
                .build();

        String createBody = mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ExpenseResponse created = objectMapper.readValue(createBody, ExpenseResponse.class);

        // Update: remove account (null accountId)
        ExpenseRequest updateReq = ExpenseRequest.builder()
                .amount(new BigDecimal("75.00"))
                .merchant("Restaurant")
                .category("Restaurants")
                .paymentMethod("Cash")
                .build();

        mockMvc.perform(put("/api/expenses/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // Balance should be restored: 1000 - 75 + 75 = 1000
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void updateExpense_switchToAccountWithInsufficientFunds_returnsUnprocessableEntity() throws Exception {
        // Pre-create an expense on testAccount (1000.00 balance)
        ExpenseRequest createReq = ExpenseRequest.builder()
                .amount(new BigDecimal("100.00"))
                .merchant("Lunch")
                .category("Restaurants")
                .paymentMethod("Card")
                .accountId(testAccount.getId())
                .build();

        String createBody = mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ExpenseResponse created = objectMapper.readValue(createBody, ExpenseResponse.class);
        // testAccount balance is now 1000 - 100 = 900
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("900.00"));

        // Low-balance savings account
        Account savings = accountRepository.save(Account.builder()
                .name("Piggy Bank")
                .balance(new BigDecimal("50.00"))
                .type(AccountType.SAVINGS)
                .user(testUser)
                .build());

        // Try to switch the expense to savings with amount 999 — must fail
        ExpenseRequest updateReq = ExpenseRequest.builder()
                .amount(new BigDecimal("999.00"))
                .merchant("Lunch")
                .category("Restaurants")
                .paymentMethod("Card")
                .accountId(savings.getId())
                .build();

        mockMvc.perform(put("/api/expenses/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Insufficient Funds"));

        // Transaction rollback: neither account balance should be touched by the
        // failed update (the +100 restore on testAccount is rolled back too).
        Account checking = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(checking.getBalance()).isEqualByComparingTo(new BigDecimal("900.00"));
        Account savingsAfter = accountRepository.findById(savings.getId()).orElseThrow();
        assertThat(savingsAfter.getBalance()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ─── DELETE /api/expenses/{id} ─────────────────────────────

    @Test
    void deleteExpense_withAccount_restoresBalance() throws Exception {
        ExpenseRequest request = ExpenseRequest.builder()
                .amount(new BigDecimal("50.00"))
                .merchant("Gas Station")
                .category("Gas & Fuel")
                .paymentMethod("Card")
                .accountId(testAccount.getId())
                .build();

        String createBody = mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ExpenseResponse created = objectMapper.readValue(createBody, ExpenseResponse.class);
        // Balance = 1000 - 50 = 950
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("950.00"));

        mockMvc.perform(delete("/api/expenses/{id}", created.getId()))
                .andExpect(status().isOk());

        // Balance should be restored: 950 + 50 = 1000
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void deleteExpense_withoutAccount_noBalanceChange() throws Exception {
        ExpenseRequest request = ExpenseRequest.builder()
                .amount(new BigDecimal("30.00"))
                .merchant("Coffee Shop")
                .category("Coffee & Cafes")
                .paymentMethod("Cash")
                .build();

        String createBody = mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        ExpenseResponse created = objectMapper.readValue(createBody, ExpenseResponse.class);

        mockMvc.perform(delete("/api/expenses/{id}", created.getId()))
                .andExpect(status().isOk());

        // Balance unchanged
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // ─── GET /api/expenses ─────────────────────────────────────

    @Test
    void getExpenses_withAccountId_returnsAccountIdInResponse() throws Exception {
        ExpenseRequest request = ExpenseRequest.builder()
                .amount(new BigDecimal("99.99"))
                .merchant("Amazon")
                .category("Electronics")
                .paymentMethod("Card")
                .accountId(testAccount.getId())
                .build();

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(testAccount.getId().toString()));
    }

    // ─── GET /api/expenses/dashboard ────────────────────────────

    @Test
    void getDashboard_includesNetWorth() throws Exception {
        // Create an expense to affect balance
        ExpenseRequest request = ExpenseRequest.builder()
                .amount(new BigDecimal("200.00"))
                .merchant("Test")
                .category("Other")
                .paymentMethod("Card")
                .accountId(testAccount.getId())
                .build();

        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Dashboard should include net worth
        mockMvc.perform(get("/api/expenses/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netWorth").isNumber())
                .andExpect(jsonPath("$.totalAssets").isNumber())
                .andExpect(jsonPath("$.totalDebt").isNumber())
                .andExpect(jsonPath("$.accountBalances").isArray());
    }
}