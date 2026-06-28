package com.expensetracker.controller;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.dto.IncomeRequest;
import com.expensetracker.dto.IncomeResponse;
import com.expensetracker.entity.*;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.IncomeRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IncomeControllerIntegrationTest {

    private static final String TEST_EMAIL = "income-integration@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IncomeRepository incomeRepository;

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
            incomeRepository.deleteAll(
                    incomeRepository.findByUserIdOrderByTimestampDesc(user.getId()));
            accountRepository.deleteAll(
                    accountRepository.findByUserIdOrderByCreatedAtAsc(user.getId()));
            userRepository.delete(user);
        });

        testUser = userRepository.save(User.builder()
                        .email(TEST_EMAIL)
                        .password("$2a$10$dummy.bcrypt.password.that.does.not.matter")
                        .displayName("Income Test User")
                        .apiKey(UUID.randomUUID().toString())
                        .build());

        testAccount = accountRepository.save(Account.builder()
                .name("Test Checking")
                .balance(new BigDecimal("1000.00"))
                .type(AccountType.BASE)
                .user(testUser)
                .build());

        // Install a SecurityContext that SecurityUtils.getCurrentUserId() can resolve.
        // Done here (not via @WithUserDetails) because @WithUserDetails runs in
        // Spring's beforeTestMethod phase, BEFORE this @BeforeEach — it can't see
        // a user that doesn't exist yet.
        UserPrincipal principal = new UserPrincipal(
                testUser.getId(),
                testUser.getEmail(),
                testUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, "N/A", principal.getAuthorities());
        TestSecurityContextHolder.setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── POST /api/incomes ────────────────────────────────────

    @Test
    void createIncome_withoutAccount_returnsCreated() throws Exception {
        IncomeRequest request = IncomeRequest.builder()
                .name("Bi-weekly paycheck")
                .type(IncomeType.TRANSFER)
                .category(IncomeCategory.PAYCHECK)
                .amount(new BigDecimal("2500.00"))
                .notes("June 27 payroll")
                .build();

        mockMvc.perform(post("/api/incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Bi-weekly paycheck"))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.category").value("PAYCHECK"))
                .andExpect(jsonPath("$.amount").value(2500.00))
                .andExpect(jsonPath("$.accountId").isEmpty());
    }

    @Test
    void createIncome_withAccount_increasesBalance() throws Exception {
        IncomeRequest request = IncomeRequest.builder()
                .name("Freelance payment")
                .type(IncomeType.TRANSFER)
                .category(IncomeCategory.REFUND)
                .amount(new BigDecimal("500.00"))
                .accountId(testAccount.getId())
                .build();

        mockMvc.perform(post("/api/incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(testAccount.getId().toString()));

        // Verify account balance increased
        Account updated = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void createIncome_missingName_returnsBadRequest() throws Exception {
        IncomeRequest request = IncomeRequest.builder()
                .type(IncomeType.CASH)
                .category(IncomeCategory.PAYCHECK)
                .amount(new BigDecimal("100.00"))
                .build();

        mockMvc.perform(post("/api/incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIncome_invalidAccount_returnsNotFound() throws Exception {
        IncomeRequest request = IncomeRequest.builder()
                .name("Bad account income")
                .type(IncomeType.CASH)
                .category(IncomeCategory.REFUND)
                .amount(new BigDecimal("50.00"))
                .accountId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/api/incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ─── GET /api/incomes ─────────────────────────────────────

    @Test
    void getIncomes_returnsAllUserIncomes() throws Exception {
        // Create two incomes
        IncomeRequest req1 = IncomeRequest.builder()
                .name("Paycheck")
                .type(IncomeType.TRANSFER)
                .category(IncomeCategory.PAYCHECK)
                .amount(new BigDecimal("2000.00"))
                .build();

        IncomeRequest req2 = IncomeRequest.builder()
                .name("Tax refund")
                .type(IncomeType.TRANSFER)
                .category(IncomeCategory.TAX_RETURN)
                .amount(new BigDecimal("1500.00"))
                .build();

        mockMvc.perform(post("/api/incomes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)));
        mockMvc.perform(post("/api/incomes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)));

        mockMvc.perform(get("/api/incomes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ─── GET /api/incomes/{id} ────────────────────────────────

    @Test
    void getIncomeById_returnsIncome() throws Exception {
        IncomeRequest request = IncomeRequest.builder()
                .name("Specific income")
                .type(IncomeType.CASH)
                .category(IncomeCategory.REFUND)
                .amount(new BigDecimal("75.00"))
                .build();

        String createBody = mockMvc.perform(post("/api/incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        IncomeResponse created = objectMapper.readValue(createBody, IncomeResponse.class);

        mockMvc.perform(get("/api/incomes/{id}", created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.name").value("Specific income"))
                .andExpect(jsonPath("$.amount").value(75.00));
    }

    @Test
    void getIncomeById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/incomes/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/incomes/{id} ────────────────────────────────

    @Test
    void updateIncome_updatesFieldsAndAdjustsBalance() throws Exception {
        IncomeRequest createReq = IncomeRequest.builder()
                .name("Initial income")
                .type(IncomeType.CASH)
                .category(IncomeCategory.REFUND)
                .amount(new BigDecimal("200.00"))
                .accountId(testAccount.getId())
                .build();

        String createBody = mockMvc.perform(post("/api/incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        IncomeResponse created = objectMapper.readValue(createBody, IncomeResponse.class);
        // Balance should be 1000 + 200 = 1200
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1200.00"));

        // Update: change amount to 300 (still same account)
        IncomeRequest updateReq = IncomeRequest.builder()
                .name("Updated income")
                .type(IncomeType.TRANSFER)
                .category(IncomeCategory.PAYCHECK)
                .amount(new BigDecimal("300.00"))
                .accountId(testAccount.getId())
                .build();

        mockMvc.perform(put("/api/incomes/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated income"))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.amount").value(300.00));

        // Balance should revert 200 then add 300: 1000 + 300 = 1300
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1300.00"));
    }

    @Test
    void updateIncome_removeAccountId_reversesOldBalance() throws Exception {
        IncomeRequest createReq = IncomeRequest.builder()
                .name("With account")
                .type(IncomeType.TRANSFER)
                .category(IncomeCategory.PAYCHECK)
                .amount(new BigDecimal("500.00"))
                .accountId(testAccount.getId())
                .build();

        String createBody = mockMvc.perform(post("/api/incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        IncomeResponse created = objectMapper.readValue(createBody, IncomeResponse.class);

        // Update: remove accountId (null)
        IncomeRequest updateReq = IncomeRequest.builder()
                .name("Now without account")
                .type(IncomeType.CASH)
                .category(IncomeCategory.REFUND)
                .amount(new BigDecimal("500.00"))
                .build();

        mockMvc.perform(put("/api/incomes/{id}", created.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // Balance should revert: 1000 + 500 - 500 = 1000 (back to original)
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // ─── DELETE /api/incomes/{id} ─────────────────────────────

    @Test
    void deleteIncome_reversesBalance() throws Exception {
        IncomeRequest request = IncomeRequest.builder()
                .name("Delete test")
                .type(IncomeType.TRANSFER)
                .category(IncomeCategory.PAYCHECK)
                .amount(new BigDecimal("300.00"))
                .accountId(testAccount.getId())
                .build();

        String createBody = mockMvc.perform(post("/api/incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        IncomeResponse created = objectMapper.readValue(createBody, IncomeResponse.class);
        // Balance = 1000 + 300 = 1300
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1300.00"));

        mockMvc.perform(delete("/api/incomes/{id}", created.getId()))
                .andExpect(status().isOk());

        // Balance should revert: 1300 - 300 = 1000
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void deleteIncome_withoutAccount_noBalanceChange() throws Exception {
        IncomeRequest request = IncomeRequest.builder()
                .name("No account delete")
                .type(IncomeType.CASH)
                .category(IncomeCategory.REFUND)
                .amount(new BigDecimal("50.00"))
                .build();

        String createBody = mockMvc.perform(post("/api/incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        IncomeResponse created = objectMapper.readValue(createBody, IncomeResponse.class);

        mockMvc.perform(delete("/api/incomes/{id}", created.getId()))
                .andExpect(status().isOk());

        // Balance unchanged
        assertThat(accountRepository.findById(testAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
    }
}