package com.expensetracker.controller;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.RecurrenceFrequency;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.dto.RecurringIncomeRequest;
import com.expensetracker.dto.RecurringIncomeResponse;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.Income;
import com.expensetracker.entity.IncomeCategory;
import com.expensetracker.entity.IncomeType;
import com.expensetracker.entity.User;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.IncomeRepository;
import com.expensetracker.repository.RecurringIncomeRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link RecurringIncomeController}. Mirrors
 * {@code RecurringExpenseControllerIntegrationTest} so the two recurring
 * controllers stay in lock-step on the contracts the frontend depends on:
 * accountId round-trip, validation, foreign-key cross-user guard, and the
 * scheduled materialisation rule that converts a backdated start date into
 * a real Income row.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecurringIncomeControllerIntegrationTest {

    private static final String TEST_EMAIL = "recurring-income-controller-integration@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private IncomeRepository incomeRepository;
    @Autowired private RecurringIncomeRepository recurringIncomeRepository;

    @MockBean private DataMigrationRunner dataMigrationRunner;
    @MockBean private SchemaMigrationRunner schemaMigrationRunner;

    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        // Clean only our test data so concurrent test classes stay isolated.
        // Order matters for FK constraints — recurring_incomes → income → accounts → users.
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> {
            recurringIncomeRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
                    .forEach(r -> recurringIncomeRepository.deleteById(r.getId()));
            incomeRepository.deleteAll(
                    incomeRepository.findByUserIdOrderByTimestampDesc(user.getId()));
            accountRepository.deleteAll(
                    accountRepository.findByUserIdOrderByCreatedAtAsc(user.getId()));
            userRepository.delete(user);
        });

        testUser = userRepository.save(User.builder()
                .email(TEST_EMAIL)
                .password("$2a$10$dummy.bcrypt.password.that.does.not.matter")
                .displayName("Recurring Income Test User")
                .apiKey(UUID.randomUUID().toString())
                .build());

        testAccount = accountRepository.save(Account.builder()
                .name("Test Checking")
                .balance(new BigDecimal("1000.00"))
                .type(AccountType.BASE)
                .user(testUser)
                .build());

        UserPrincipal principal = new UserPrincipal(
                testUser.getId(),
                testUser.getEmail(),
                testUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        // Wipe any rows this test class wrote so a sibling test class'
        // @BeforeEach (which does an unconditional accountRepository.deleteAll())
        // doesn't trip the new recurring_incomes.account_id FK. Runs after
        // EVERY method (including the last) so the table is empty by the time
        // the next class' setUp() runs.
        recurringIncomeRepository.deleteAll();
        incomeRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        SecurityContextHolder.clearContext();
    }

    // ─── POST returns accountId; GET roundtrips it ──────────────────

    @Test
    void createRecurringIncome_withAccount_returnsAccountIdAndRoundtrips() throws Exception {
        RecurringIncomeRequest request = baseMonthlyRequest()
                .accountId(testAccount.getId())
                .build();

        String body = mockMvc.perform(post("/api/recurring-incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(testAccount.getId().toString()))
                .andExpect(jsonPath("$.name").value("Bi-weekly paycheck"))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(
                objectMapper.readValue(body, RecurringIncomeResponse.class).getId().toString());

        // GET single must include accountId
        mockMvc.perform(get("/api/recurring-incomes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(testAccount.getId().toString()));

        // GET list must include accountId on every entry
        mockMvc.perform(get("/api/recurring-incomes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(testAccount.getId().toString()));
    }

    @Test
    void createRecurringIncome_withoutAccount_returnsNullAccountId() throws Exception {
        RecurringIncomeRequest request = baseMonthlyRequest().build();

        mockMvc.perform(post("/api/recurring-incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/recurring-incomes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(org.hamcrest.Matchers.nullValue()));
    }

    // ─── PUT updates accountId ──────────────────────────────────────

    @Test
    void updateRecurringIncome_changesAccountId() throws Exception {
        Account secondAccount = accountRepository.save(Account.builder()
                .name("Secondary")
                .balance(new BigDecimal("500.00"))
                .type(AccountType.SAVINGS)
                .user(testUser)
                .build());

        RecurringIncomeRequest create = baseMonthlyRequest()
                .accountId(testAccount.getId())
                .build();

        String createdJson = mockMvc.perform(post("/api/recurring-incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(
                objectMapper.readValue(createdJson, RecurringIncomeResponse.class).getId().toString());

        RecurringIncomeRequest update = baseMonthlyRequest()
                .accountId(secondAccount.getId())
                .build();

        mockMvc.perform(put("/api/recurring-incomes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(secondAccount.getId().toString()));

        mockMvc.perform(get("/api/recurring-incomes/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(secondAccount.getId().toString()));
    }

    // ─── Foreign-key cross-user guard ───────────────────────────────

    @Test
    void createRecurringIncome_withUnknownAccountId_returns404() throws Exception {
        RecurringIncomeRequest request = baseMonthlyRequest()
                .accountId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/api/recurring-incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createRecurringIncome_withAnotherUsersAccount_returns404() throws Exception {
        User otherUser = userRepository.save(User.builder()
                .email("other-" + TEST_EMAIL)
                .password("$2a$10$dummy.bcrypt.password.that.does.not.matter")
                .displayName("Other User")
                .apiKey(UUID.randomUUID().toString())
                .build());
        Account otherAccount = accountRepository.save(Account.builder()
                .name("Other Checking")
                .balance(new BigDecimal("100.00"))
                .type(AccountType.BASE)
                .user(otherUser)
                .build());

        RecurringIncomeRequest request = baseMonthlyRequest()
                .accountId(otherAccount.getId())
                .build();

        mockMvc.perform(post("/api/recurring-incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        accountRepository.delete(otherAccount);
        userRepository.delete(otherUser);
    }

    // ─── Validation ────────────────────────────────────────────────

    @Test
    void createRecurringIncome_missingRequiredFields_returnsBadRequest() throws Exception {
        // Missing name + type + category + amount + frequency + startDate
        String body = "{}";
        mockMvc.perform(post("/api/recurring-incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void createRecurringIncome_negativeAmount_returnsBadRequest() throws Exception {
        RecurringIncomeRequest request = baseMonthlyRequest()
                .amount(new BigDecimal("-1.00"))
                .build();

        mockMvc.perform(post("/api/recurring-incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── Read on missing template ──────────────────────────────────

    @Test
    void getRecurringIncomeById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/recurring-incomes/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateRecurringIncome_notFound_returns404() throws Exception {
        RecurringIncomeRequest request = baseMonthlyRequest().build();

        mockMvc.perform(put("/api/recurring-incomes/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRecurringIncome_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/recurring-incomes/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ─── Toggle / delete on a real template ─────────────────────────

    @Test
    void toggleRecurringIncome_flipsActive() throws Exception {
        RecurringIncomeRequest request = baseMonthlyRequest()
                .accountId(testAccount.getId())
                .build();

        String createdJson = mockMvc.perform(post("/api/recurring-incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(
                objectMapper.readValue(createdJson, RecurringIncomeResponse.class).getId().toString());

        mockMvc.perform(patch("/api/recurring-incomes/{id}/toggle", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/api/recurring-incomes/{id}/toggle", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deleteRecurringIncome_removesRow() throws Exception {
        RecurringIncomeRequest request = baseMonthlyRequest().build();

        String createdJson = mockMvc.perform(post("/api/recurring-incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(
                objectMapper.readValue(createdJson, RecurringIncomeResponse.class).getId().toString());

        mockMvc.perform(delete("/api/recurring-incomes/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/recurring-incomes/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ─── Regression: accountId propagates to materialised Incomes ──

    @Test
    void createRecurringIncome_startingYesterday_materialisesIncomeWithSameAccountId() throws Exception {
        // Backdated startDate — first occurrence is yesterday — triggers the
        // immediate materialisation rule in RecurringIncomeService.create. The
        // generated Income row must carry the template's accountId so the
        // scheduled `processRecurringIncomes` job can also keep credits
        // flowing into the same account on every cycle.
        LocalDate yesterday = LocalDate.now().minusDays(1);
        RecurringIncomeRequest request = RecurringIncomeRequest.builder()
                .name("Yesterday paycheck")
                .type(IncomeType.TRANSFER)
                .category(IncomeCategory.PAYCHECK)
                .amount(new BigDecimal("2500.00"))
                .frequency(RecurrenceFrequency.WEEKLY)
                .dayOfWeek(yesterday.getDayOfWeek())
                .startDate(yesterday)
                .accountId(testAccount.getId())
                .build();

        mockMvc.perform(post("/api/recurring-incomes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(testAccount.getId().toString()));

        List<Income> generated = incomeRepository.findByUserIdOrderByTimestampDesc(testUser.getId())
                .stream()
                .filter(i -> "Yesterday paycheck".equals(i.getName()))
                .toList();
        assertThat(generated).as("backdated template should materialise ≥1 income").isNotEmpty();
        assertThat(generated).allSatisfy(i ->
                assertThat(i.getAccountId()).isEqualTo(testAccount.getId()));

        // And the account balance should have been bumped by the template amount.
        Account refreshed = accountRepository.findById(testAccount.getId()).orElseThrow();
        assertThat(refreshed.getBalance())
                .isEqualByComparingTo(new BigDecimal("1000.00").add(new BigDecimal("2500.00")));
    }

    // ─── helpers ────────────────────────────────────────────────────

    private RecurringIncomeRequest.RecurringIncomeRequestBuilder baseMonthlyRequest() {
        return RecurringIncomeRequest.builder()
                .name("Bi-weekly paycheck")
                .type(IncomeType.TRANSFER)
                .category(IncomeCategory.PAYCHECK)
                .amount(new BigDecimal("2500.00"))
                .frequency(RecurrenceFrequency.MONTHLY)
                .dayOfMonth(15)
                // Start one month back so the default-path tests do NOT
                // trigger immediate materialisation (they only care about
                // CRUD shape, not scheduled-job side effects).
                .startDate(LocalDate.now().minusMonths(1));
    }
}
