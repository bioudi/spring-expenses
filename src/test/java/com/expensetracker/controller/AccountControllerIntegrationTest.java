package com.expensetracker.controller;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.dto.AccountRequest;
import com.expensetracker.dto.AccountResponse;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.User;
import com.expensetracker.repository.AccountRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link AccountController}, focused on the 404 / 400
 * contract from the QA bug:
 *   - PUT/DELETE/GET /api/accounts/&lt;non-existent-id&gt; should return 404
 *   - PUT/POST with an invalid account type should return 400 with a clear message
 *
 * Auth context is set manually in {@link #setUp()} (after the user is persisted)
 * because {@code @WithUserDetails} runs in Spring's beforeTestMethod phase,
 * BEFORE JUnit's {@code @BeforeEach}, so it cannot see a user created there.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTest {

    private static final String TEST_EMAIL = "account-integration@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;

    @MockBean private DataMigrationRunner dataMigrationRunner;
    @MockBean private SchemaMigrationRunner schemaMigrationRunner;

    private User testUser;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email(TEST_EMAIL)
                .password("$2a$10$dummy.bcrypt.password.that.does.not.matter")
                .displayName("Account Test User")
                .apiKey(UUID.randomUUID().toString())
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

    // ─── 404 handling — malformed and missing IDs ─────────────────────

    @Test
    void getAccountById_malformedId_returns404() throws Exception {
        // QA bug: PUT/DELETE/GET /api/accounts/<garbage> previously returned 500.
        // Non-UUID path var should be treated as "not found", not as a server error.
        mockMvc.perform(get("/api/accounts/nonexistent-id-12345"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Account not found with id: nonexistent-id-12345")));
    }

    @Test
    void putAccount_malformedId_returns404() throws Exception {
        AccountRequest body = AccountRequest.builder()
                .name("Doesn't matter")
                .type(AccountType.BASE)
                .balance(BigDecimal.ZERO)
                .build();

        mockMvc.perform(put("/api/accounts/not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteAccount_malformedId_returns404() throws Exception {
        mockMvc.perform(delete("/api/accounts/garbage-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getAccountById_unknownUuid_returns404() throws Exception {
        // Valid UUID format, but no account with that id exists for this user.
        mockMvc.perform(get("/api/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void putAccount_unknownUuid_returns404() throws Exception {
        AccountRequest body = AccountRequest.builder()
                .name("Doesn't matter")
                .type(AccountType.BASE)
                .balance(BigDecimal.ZERO)
                .build();

        mockMvc.perform(put("/api/accounts/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAccount_unknownUuid_returns404() throws Exception {
        mockMvc.perform(delete("/api/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ─── 400 handling — invalid enum value ─────────────────────────────

    @Test
    void createAccount_invalidType_returnsClearMessage() throws Exception {
        // QA secondary bug: invalid enum previously returned generic "Malformed JSON".
        String body = "{\"name\":\"Bad\",\"balance\":0,\"type\":\"Savings\"}";

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid AccountType"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("Savings"),
                                org.hamcrest.Matchers.containsString("BASE"),
                                org.hamcrest.Matchers.containsString("SAVINGS"),
                                org.hamcrest.Matchers.containsString("CREDIT"))));
    }

    @Test
    void putAccount_invalidType_returnsClearMessage() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .name("Test")
                .balance(BigDecimal.ZERO)
                .type(AccountType.BASE)
                .user(testUser)
                .build());

        String body = "{\"name\":\"Updated\",\"balance\":0,\"type\":\"Savings\"}";

        mockMvc.perform(put("/api/accounts/{id}", account.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid AccountType"));
    }

    // ─── Happy-path sanity check (existing controller still works) ──────

    @Test
    void createAndGetAccount_roundtrip() throws Exception {
        AccountRequest req = AccountRequest.builder()
                .name("Main Checking")
                .type(AccountType.BASE)
                .balance(new BigDecimal("1234.56"))
                .build();

        String created = mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Main Checking"))
                .andExpect(jsonPath("$.type").value("BASE"))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(objectMapper.readValue(created, AccountResponse.class).getId().toString());

        mockMvc.perform(get("/api/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Main Checking"));

        mockMvc.perform(delete("/api/accounts/{id}", id))
                .andExpect(status().isOk());
    }
}