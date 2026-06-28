package com.expensetracker.controller;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.RecurrenceFrequency;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.dto.RecurringExpenseRequest;
import com.expensetracker.dto.RecurringExpenseResponse;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.RecurringExpenseRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link RecurringExpenseController} covering the
 * {@code accountId} roundtrip regression found in QA t_09923bd9.
 *
 * <p>Before the fix, GET /api/recurring-expenses never returned {@code accountId},
 * so the edit modal always fell back to "— No account —" even when an account
 * was linked. After the fix, the field must roundtrip through POST/PUT and be
 * present on every GET response (list and single).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RecurringExpenseControllerIntegrationTest {

    private static final String TEST_EMAIL = "recurring-account-integration@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private RecurringExpenseRepository recurringExpenseRepository;

    @MockBean private DataMigrationRunner dataMigrationRunner;
    @MockBean private SchemaMigrationRunner schemaMigrationRunner;

    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> {
            // Order matters: child rows first to satisfy FK constraints.
            recurringExpenseRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
                    .forEach(r -> recurringExpenseRepository.deleteById(r.getId()));
            expenseRepository.deleteAll(
                    expenseRepository.findAllByUserIdOrderByTimestampDesc(user.getId()));
            accountRepository.deleteAll(
                    accountRepository.findByUserIdOrderByCreatedAtAsc(user.getId()));
            userRepository.delete(user);
        });

        testUser = userRepository.save(User.builder()
                .email(TEST_EMAIL)
                .password("$2a$10$dummy.bcrypt.password.that.does.not.matter")
                .displayName("Recurring Account Test User")
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
        SecurityContextHolder.clearContext();
    }

    // ─── POST returns accountId; GET roundtrips it ──────────────────

    @Test
    void createRecurringExpense_withAccount_returnsAccountIdAndRoundtrips() throws Exception {
        RecurringExpenseRequest request = baseMonthlyRequest()
                .accountId(testAccount.getId())
                .build();

        String body = mockMvc.perform(post("/api/recurring-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(testAccount.getId().toString()))
                .andExpect(jsonPath("$.merchant").value("Netflix"))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readValue(body, RecurringExpenseResponse.class).getId().toString());

        // GET single — must include accountId
        mockMvc.perform(get("/api/recurring-expenses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(testAccount.getId().toString()));

        // GET list — must include accountId on every entry
        mockMvc.perform(get("/api/recurring-expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(testAccount.getId().toString()));
    }

    @Test
    void createRecurringExpense_withoutAccount_returnsNullAccountId() throws Exception {
        RecurringExpenseRequest request = baseMonthlyRequest().build();

        mockMvc.perform(post("/api/recurring-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(org.hamcrest.Matchers.nullValue()));
        // ^ Pins the contract: when no account is linked, accountId is present
        //   in the JSON as null (so the edit modal can distinguish "no account
        //   selected" from "field missing"). If Jackson's global null-handling
        //   ever flips to NON_NULL, the edit modal would silently revert to
        //   the bug we just fixed — this test catches that.

        mockMvc.perform(get("/api/recurring-expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(org.hamcrest.Matchers.nullValue()));
    }

    // ─── PUT updates accountId ──────────────────────────────────────

    @Test
    void updateRecurringExpense_changesAccountId() throws Exception {
        // Second account so we can swap it in via PUT.
        Account secondAccount = accountRepository.save(Account.builder()
                .name("Secondary")
                .balance(new BigDecimal("500.00"))
                .type(AccountType.SAVINGS)
                .user(testUser)
                .build());

        RecurringExpenseRequest create = baseMonthlyRequest()
                .accountId(testAccount.getId())
                .build();

        String createdJson = mockMvc.perform(post("/api/recurring-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(
                objectMapper.readValue(createdJson, RecurringExpenseResponse.class).getId().toString());

        // Switch the template to the second account
        RecurringExpenseRequest update = baseMonthlyRequest()
                .accountId(secondAccount.getId())
                .build();

        mockMvc.perform(put("/api/recurring-expenses/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(secondAccount.getId().toString()));

        mockMvc.perform(get("/api/recurring-expenses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(secondAccount.getId().toString()));
    }

    // ─── Foreign-key cross-user guard ───────────────────────────────

    @Test
    void createRecurringExpense_withUnknownAccountId_returns404() throws Exception {
        RecurringExpenseRequest request = baseMonthlyRequest()
                .accountId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/api/recurring-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createRecurringExpense_withAnotherUsersAccount_returns404() throws Exception {
        // Build a second user + account so the test exercises the
        // user-scoped lookup (findByIdAndUserId), not just "id exists somewhere".
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

        RecurringExpenseRequest request = baseMonthlyRequest()
                .accountId(otherAccount.getId())
                .build();

        mockMvc.perform(post("/api/recurring-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        // Cleanup the second user
        accountRepository.delete(otherAccount);
        userRepository.delete(otherUser);
    }

    // ─── Regression: accountId propagates to generated Expenses ─────

    @Test
    void createRecurringExpense_startingYesterday_generatesExpenseWithSameAccountId() throws Exception {
        // A template whose startDate is yesterday causes an immediate expense
        // to be materialized (any past-or-today firstOccurrence triggers it).
        // The generated expense must carry the template's accountId so the
        // recurring template's account carries through to the actual expense
        // rows — the whole point of the field.
        LocalDate yesterday = LocalDate.now().minusDays(1);
        RecurringExpenseRequest request = RecurringExpenseRequest.builder()
                .amount(new BigDecimal("15.99"))
                .merchant("Netflix")
                .category("Streaming")
                .frequency(RecurrenceFrequency.WEEKLY)
                .dayOfWeek(yesterday.getDayOfWeek())
                .startDate(yesterday)
                .accountId(testAccount.getId())
                .build();

        String body = mockMvc.perform(post("/api/recurring-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(testAccount.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        UUID templateId = UUID.fromString(
                objectMapper.readValue(body, RecurringExpenseResponse.class).getId().toString());

        // Generated expenses link back via recurringExpenseId; their account
        // must be the same as the template's account.
        List<Expense> generated = expenseRepository.findAllByUserIdOrderByTimestampDesc(testUser.getId())
                .stream()
                .filter(e -> templateId.equals(e.getRecurringExpenseId()))
                .toList();
        assertThat(generated).as("template starting yesterday should materialize ≥1 expense")
                .isNotEmpty();
        assertThat(generated)
                .allSatisfy(e -> assertThat(e.getAccount()).isNotNull());
        assertThat(generated.get(0).getAccount().getId()).isEqualTo(testAccount.getId());
    }

    // ─── helpers ────────────────────────────────────────────────────

    private RecurringExpenseRequest.RecurringExpenseRequestBuilder baseMonthlyRequest() {
        return RecurringExpenseRequest.builder()
                .amount(new BigDecimal("15.99"))
                .merchant("Netflix")
                .category("Streaming")
                .frequency(RecurrenceFrequency.MONTHLY)
                .dayOfMonth(15)
                // Start in the past so the template is well-defined but no
                // immediate expense is generated for the default tests; the
                // "startingToday" test overrides this.
                .startDate(LocalDate.now().minusMonths(1));
    }
}
