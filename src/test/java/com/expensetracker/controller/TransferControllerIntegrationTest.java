package com.expensetracker.controller;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.dto.TransferRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link TransferController}. Covers the four transfer
 * cases (non-CREDIT ↔ non-CREDIT, non-CREDIT → CREDIT, CREDIT → non-CREDIT,
 * CREDIT → CREDIT) plus the error contract: same-account → 409,
 * non-existent source → 404, insufficient funds → 422, validation → 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TransferControllerIntegrationTest {

    private static final String TEST_EMAIL = "transfer-integration@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;

    @MockBean private DataMigrationRunner dataMigrationRunner;
    @MockBean private SchemaMigrationRunner schemaMigrationRunner;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> {
            accountRepository.deleteAll(accountRepository.findByUserIdOrderByCreatedAtAsc(user.getId()));
            userRepository.delete(user);
        });

        testUser = userRepository.save(User.builder()
                .email(TEST_EMAIL)
                .password("$2a$10$dummy.bcrypt.password.that.does.not.matter")
                .displayName("Transfer Test User")
                .apiKey(UUID.randomUUID().toString())
                .build());

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

    private Account createAccount(String name, BigDecimal balance, AccountType type) {
        return accountRepository.save(Account.builder()
                .name(name)
                .balance(balance)
                .type(type)
                .user(testUser)
                .build());
    }

    // ─── Case 1: non-CREDIT → non-CREDIT ────────────────────────────

    @Test
    void transfer_betweenNonCredit_movesBalanceAtomically() throws Exception {
        Account checking = createAccount("Checking", new BigDecimal("1000.00"), AccountType.BASE);
        Account savings = createAccount("Savings", new BigDecimal("0.00"), AccountType.SAVINGS);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(checking.getId())
                .toAccountId(savings.getId())
                .amount(new BigDecimal("250.00"))
                .description("Move to savings")
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(250.00))
                .andExpect(jsonPath("$.fromAccount.id").value(checking.getId().toString()))
                .andExpect(jsonPath("$.fromAccount.name").value("Checking"))
                .andExpect(jsonPath("$.fromAccount.type").value("BASE"))
                .andExpect(jsonPath("$.fromAccount.balance").value(750.00))
                .andExpect(jsonPath("$.toAccount.id").value(savings.getId().toString()))
                .andExpect(jsonPath("$.toAccount.name").value("Savings"))
                .andExpect(jsonPath("$.toAccount.type").value("SAVINGS"))
                .andExpect(jsonPath("$.toAccount.balance").value(250.00))
                .andExpect(jsonPath("$.description").value("Move to savings"));

        // Confirm the database committed the right balances.
        assertThat(accountRepository.findById(checking.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("750.00");
        assertThat(accountRepository.findById(savings.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("250.00");
    }

    // ─── Case 2: non-CREDIT → CREDIT (credit card payment) ───────────

    @Test
    void transfer_nonCreditToCredit_paysCreditCard() throws Exception {
        Account checking = createAccount("Checking", new BigDecimal("2000.00"), AccountType.BASE);
        // CREDIT balance is signed: a POSITIVE value represents debt owed.
        // Visa starts at +$500 (the user owes $500). Paying $200 should
        // reduce the debt to $300 — not add to it. See TransferService for
        // the full four-case matrix.
        Account visa = createAccount("Visa", new BigDecimal("500.00"), AccountType.CREDIT);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(checking.getId())
                .toAccountId(visa.getId())
                .amount(new BigDecimal("200.00"))
                .description("Credit card payment")
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromAccount.balance").value(1800.00))
                // Paying the card moves the debt balance toward zero — visa
                // goes from +500 (owe $500) to +300 (owe $300).
                .andExpect(jsonPath("$.toAccount.balance").value(300.00));

        assertThat(accountRepository.findById(checking.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("1800.00");
        assertThat(accountRepository.findById(visa.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("300.00");
    }

    // ─── Case 3: CREDIT → non-CREDIT (cash advance) ──────────────────

    @Test
    void transfer_creditToNonCredit_addsToDestinationEvenWhenSourceDebtGrows() throws Exception {
        // Source CREDIT starts at +$100 (the user owes $100). A cash advance
        // of $50 makes the debt worse (more positive) while adding to the
        // destination — no balance guard fires because CREDIT sources can
        // grow debt freely.
        Account visa = createAccount("Visa", new BigDecimal("100.00"), AccountType.CREDIT);
        Account checking = createAccount("Checking", new BigDecimal("0.00"), AccountType.BASE);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(visa.getId())
                .toAccountId(checking.getId())
                .amount(new BigDecimal("50.00"))
                .description("Cash advance")
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromAccount.balance").value(150.00))
                .andExpect(jsonPath("$.toAccount.balance").value(50.00));

        assertThat(accountRepository.findById(visa.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("150.00");
        assertThat(accountRepository.findById(checking.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("50.00");
    }

    // ─── Case 4: CREDIT → CREDIT (balance transfer) ──────────────────

    @Test
    void transfer_creditToCredit_movesDebt() throws Exception {
        // Both cards carry debt (positive balances = amount owed). Moving
        // $75 from visa to mastercard makes visa's debt grow by $75 and
        // mastercard's debt shrink by $75 — the standard balance-transfer
        // semantics.
        Account visa = createAccount("Visa", new BigDecimal("300.00"), AccountType.CREDIT);
        Account mastercard = createAccount("Mastercard", new BigDecimal("100.00"), AccountType.CREDIT);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(visa.getId())
                .toAccountId(mastercard.getId())
                .amount(new BigDecimal("75.00"))
                .description("Balance transfer")
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromAccount.balance").value(375.00))
                .andExpect(jsonPath("$.toAccount.balance").value(25.00));

        assertThat(accountRepository.findById(visa.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("375.00");
        assertThat(accountRepository.findById(mastercard.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("25.00");
    }

    // ─── Error: same account ─────────────────────────────────────────

    @Test
    void transfer_sameAccount_returnsConflict() throws Exception {
        Account checking = createAccount("Checking", new BigDecimal("100.00"), AccountType.BASE);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(checking.getId())
                .toAccountId(checking.getId())
                .amount(new BigDecimal("10.00"))
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));

        // Balance must NOT have changed — same-account rejection is rolled back.
        assertThat(accountRepository.findById(checking.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
    }

    // ─── Error: insufficient funds on non-CREDIT source ──────────────

    @Test
    void transfer_insufficientFundsOnNonCreditSource_returns422() throws Exception {
        Account checking = createAccount("Checking", new BigDecimal("50.00"), AccountType.BASE);
        Account savings = createAccount("Savings", new BigDecimal("0.00"), AccountType.SAVINGS);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(checking.getId())
                .toAccountId(savings.getId())
                .amount(new BigDecimal("500.00"))
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Insufficient Funds"));

        // Source and destination unchanged after rollback.
        assertThat(accountRepository.findById(checking.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("50.00");
        assertThat(accountRepository.findById(savings.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("0.00");
    }

    // ─── Error: account not found ────────────────────────────────────

    @Test
    void transfer_unknownFromAccount_returns404() throws Exception {
        Account savings = createAccount("Savings", new BigDecimal("0.00"), AccountType.SAVINGS);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(UUID.randomUUID())
                .toAccountId(savings.getId())
                .amount(new BigDecimal("10.00"))
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void transfer_unknownToAccount_returns404() throws Exception {
        Account checking = createAccount("Checking", new BigDecimal("1000.00"), AccountType.BASE);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(checking.getId())
                .toAccountId(UUID.randomUUID())
                .amount(new BigDecimal("10.00"))
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ─── Error: account belongs to another user ───────────────────────

    @Test
    void transfer_accountOwnedByAnotherUser_returns404() throws Exception {
        // Create another user + account that testUser does NOT own.
        User other = userRepository.save(User.builder()
                .email("transfer-other@example.com")
                .password("$2a$10$dummy.bcrypt.password.that.does.not.matter")
                .displayName("Other")
                .apiKey(UUID.randomUUID().toString())
                .build());
        Account othersChecking = accountRepository.save(Account.builder()
                .name("Other Checking")
                .balance(new BigDecimal("100.00"))
                .type(AccountType.BASE)
                .user(other)
                .build());

        Account mine = createAccount("Mine", new BigDecimal("0.00"), AccountType.BASE);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(mine.getId())
                .toAccountId(othersChecking.getId())
                .amount(new BigDecimal("10.00"))
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ─── Validation ──────────────────────────────────────────────────

    @Test
    void transfer_missingFromAccountId_returns400() throws Exception {
        Account savings = createAccount("Savings", new BigDecimal("0.00"), AccountType.SAVINGS);

        String body = "{\"toAccountId\":\"" + savings.getId() + "\",\"amount\":10.00}";

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_missingAmount_returns400() throws Exception {
        Account checking = createAccount("Checking", new BigDecimal("100.00"), AccountType.BASE);
        Account savings = createAccount("Savings", new BigDecimal("0.00"), AccountType.SAVINGS);

        String body = "{\"fromAccountId\":\"" + checking.getId() +
                "\",\"toAccountId\":\"" + savings.getId() + "\"}";

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_zeroAmount_returns400() throws Exception {
        Account checking = createAccount("Checking", new BigDecimal("100.00"), AccountType.BASE);
        Account savings = createAccount("Savings", new BigDecimal("0.00"), AccountType.SAVINGS);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(checking.getId())
                .toAccountId(savings.getId())
                .amount(BigDecimal.ZERO)
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_negativeAmount_returns400() throws Exception {
        Account checking = createAccount("Checking", new BigDecimal("100.00"), AccountType.BASE);
        Account savings = createAccount("Savings", new BigDecimal("0.00"), AccountType.SAVINGS);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(checking.getId())
                .toAccountId(savings.getId())
                .amount(new BigDecimal("-10.00"))
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─── Atomicity: full rollback on failure ─────────────────────────

    @Test
    void transfer_rollbackLeavesBothBalancesUnchanged_whenSourceInsufficient() throws Exception {
        // Regression guard: a partial commit would be a serious bug. If the
        // source check fails after the destination add, the destination
        // must NOT be credited. With @Transactional + the atomic SQL guard
        // both updates roll back together.
        Account checking = createAccount("Checking", new BigDecimal("50.00"), AccountType.BASE);
        Account savings = createAccount("Savings", new BigDecimal("25.00"), AccountType.SAVINGS);

        TransferRequest request = TransferRequest.builder()
                .fromAccountId(checking.getId())
                .toAccountId(savings.getId())
                .amount(new BigDecimal("100.00"))  // > checking balance → must fail
                .build();

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(accountRepository.findById(checking.getId()).orElseThrow().getBalance())
                .as("source must NOT have been debited on rollback")
                .isEqualByComparingTo("50.00");
        assertThat(accountRepository.findById(savings.getId()).orElseThrow().getBalance())
                .as("destination must NOT have been credited on rollback")
                .isEqualByComparingTo("25.00");
    }
}