package com.expensetracker.service;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ExpenseNotFoundException;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that deleting an expense with an accountId properly restores
 * the linked account's balance by the expense amount.
 *
 * <p>Covers: debit asset accounts, credit accounts, no-account expenses,
 * non-existent expense, and atomicity (via @Transactional).</p>
 */
@SpringBootTest
class ExpenseServiceBalanceRestorationTest {

    private static final String TEST_EMAIL = "balance-restore@example.com";

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private DataMigrationRunner dataMigrationRunner;

    @MockBean
    private SchemaMigrationRunner schemaMigrationRunner;

    private User testUser;
    private BigDecimal initialBalance;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> {
            expenseRepository.deleteAll(
                    expenseRepository.findByUserIdAndCategory(user.getId(), "Restaurants"));
            accountRepository.deleteAll(
                    accountRepository.findByUserIdOrderByCreatedAtAsc(user.getId()));
            userRepository.delete(user);
        });

        testUser = userRepository.save(User.builder()
                .email(TEST_EMAIL)
                .password("$2a$10$dummy.bcrypt.password.that.does.not.matter")
                .displayName("Balance Restore Test User")
                .apiKey(UUID.randomUUID().toString())
                .build());

        initialBalance = new BigDecimal("1000.00");

        // Set up security context
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

    // ─── Helper: create an account ──────────────────────────────

    private Account createAccount(String name, AccountType type) {
        return accountRepository.save(Account.builder()
                .name(name)
                .balance(initialBalance)
                .type(type)
                .user(testUser)
                .build());
    }

    // ─── Helper: create an expense linked to an account ──────────

    private UUID createLinkedExpense(BigDecimal amount, Account account) {
        ExpenseRequest request = ExpenseRequest.builder()
                .merchant("Test Merchant")
                .amount(amount)
                .category("Restaurants")
                .accountId(account.getId())
                .build();
        return expenseService.createExpense(request, testUser.getId()).getId();
    }

    // ─── Helper: create an expense WITHOUT an account link ──────

    private UUID createUnlinkedExpense(BigDecimal amount) {
        ExpenseRequest request = ExpenseRequest.builder()
                .merchant("No Account Merchant")
                .amount(amount)
                .category("Restaurants")
                .build();
        return expenseService.createExpense(request, testUser.getId()).getId();
    }

    // ==============================================================
    //  Tests
    // ==============================================================

    /**
     * Given a debit asset account (BASE) with an initial balance,
     * creating an expense linked to it should decrease the balance,
     * and deleting that expense should restore the balance back.
     */
    @Test
    void deleteExpense_withDebitAccount_restoresBalance() {
        Account debitAccount = createAccount("Checking", AccountType.BASE);

        // 1. Create expense — balance should decrease
        UUID expenseId = createLinkedExpense(new BigDecimal("50.00"), debitAccount);
        assertThat(accountRepository.findById(debitAccount.getId()).orElseThrow().getBalance())
                .as("Balance after creating expense should decrease")
                .isEqualByComparingTo(initialBalance.subtract(new BigDecimal("50.00")));

        // 2. Delete expense — balance should increase back
        expenseService.deleteExpense(expenseId, testUser.getId());
        assertThat(accountRepository.findById(debitAccount.getId()).orElseThrow().getBalance())
                .as("Balance after deleting expense should be restored to initial")
                .isEqualByComparingTo(initialBalance);
    }

    /**
     * SAVINGS accounts (another debit type) should also have
     * their balance restored on expense deletion.
     */
    @Test
    void deleteExpense_withSavingsAccount_restoresBalance() {
        Account savingsAccount = createAccount("Savings", AccountType.SAVINGS);

        UUID expenseId = createLinkedExpense(new BigDecimal("100.00"), savingsAccount);

        assertThat(accountRepository.findById(savingsAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("900.00"));

        expenseService.deleteExpense(expenseId, testUser.getId());
        assertThat(accountRepository.findById(savingsAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(initialBalance);
    }

    /**
     * EMERGENCY accounts (another debit type) — same restoration behaviour.
     */
    @Test
    void deleteExpense_withEmergencyAccount_restoresBalance() {
        Account emergencyAccount = createAccount("Emergency Fund", AccountType.EMERGENCY);

        UUID expenseId = createLinkedExpense(new BigDecimal("200.00"), emergencyAccount);

        assertThat(accountRepository.findById(emergencyAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("800.00"));

        expenseService.deleteExpense(expenseId, testUser.getId());
        assertThat(accountRepository.findById(emergencyAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(initialBalance);
    }

    /**
     * Given a CREDIT account, deleting an expense linked to it should
     * restore the balance back to its original value.
     *
     * <p>For credit accounts the balance represents the outstanding debt.
     * When the expense is created the balance decreases (debt is reduced),
     * and when the expense is deleted the balance increases back
     * (the debt reduction is reversed).</p>
     */
    @Test
    void deleteExpense_withCreditAccount_restoresBalance() {
        Account creditAccount = createAccount("Visa", AccountType.CREDIT);

        UUID expenseId = createLinkedExpense(new BigDecimal("300.00"), creditAccount);
        assertThat(accountRepository.findById(creditAccount.getId()).orElseThrow().getBalance())
                .as("Balance after creating expense on CREDIT account should decrease")
                .isEqualByComparingTo(initialBalance.subtract(new BigDecimal("300.00")));

        expenseService.deleteExpense(expenseId, testUser.getId());
        assertThat(accountRepository.findById(creditAccount.getId()).orElseThrow().getBalance())
                .as("Balance after deleting expense on CREDIT account should be restored")
                .isEqualByComparingTo(initialBalance);
    }

    /**
     * Deleting an expense that has no linked accountId should not
     * affect any account balance.
     */
    @Test
    void deleteExpense_withoutAccountId_doesNotAffectBalance() {
        Account account = createAccount("Unused Account", AccountType.BASE);
        BigDecimal balanceBefore = accountRepository.findById(account.getId()).orElseThrow().getBalance();

        UUID expenseId = createUnlinkedExpense(new BigDecimal("75.00"));

        expenseService.deleteExpense(expenseId, testUser.getId());

        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
                .as("Balance should be unchanged when deleting an expense without an accountId")
                .isEqualByComparingTo(balanceBefore);
    }

    /**
     * Deleting a non-existent expense should throw ExpenseNotFoundException
     * and not affect any account balances.
     */
    @Test
    void deleteExpense_nonExistent_throwsException() {
        Account account = createAccount("Safe Account", AccountType.BASE);
        BigDecimal balanceBefore = accountRepository.findById(account.getId()).orElseThrow().getBalance();

        UUID nonExistentId = UUID.randomUUID();

        assertThatThrownBy(() -> expenseService.deleteExpense(nonExistentId, testUser.getId()))
                .as("Deleting a non-existent expense should throw ExpenseNotFoundException")
                .isInstanceOf(ExpenseNotFoundException.class);

        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
                .as("Balance should be unchanged when deleting a non-existent expense")
                .isEqualByComparingTo(balanceBefore);
    }

    /**
     * Balance update is atomic: the balance restore and expense deletion
     * happen within a single @Transactional method. If deletion fails,
     * the balance is rolled back.
     */
    @Test
    void deleteExpense_balanceUpdateIsAtomic() {
        Account account = createAccount("Atomic Account", AccountType.BASE);
        BigDecimal balanceBefore = accountRepository.findById(account.getId()).orElseThrow().getBalance();

        UUID expenseId = createLinkedExpense(new BigDecimal("25.00"), account);

        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(balanceBefore.subtract(new BigDecimal("25.00")));

        expenseService.deleteExpense(expenseId, testUser.getId());

        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(balanceBefore);

        assertThatThrownBy(() -> expenseService.getExpenseById(expenseId, testUser.getId()))
                .as("Expense should be removed after atomic delete")
                .isInstanceOf(ExpenseNotFoundException.class);
    }

    /**
     * Multiple expenses on the same account: each deletion should
     * incrementally restore the balance.
     */
    @Test
    void deleteExpense_multipleExpensesOnSameAccount_incrementallyRestores() {
        Account account = createAccount("Multi-expense Account", AccountType.BASE);

        UUID expense1 = createLinkedExpense(new BigDecimal("50.00"), account);
        UUID expense2 = createLinkedExpense(new BigDecimal("30.00"), account);
        UUID expense3 = createLinkedExpense(new BigDecimal("20.00"), account);

        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("900.00"));

        expenseService.deleteExpense(expense2, testUser.getId());
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("930.00"));

        expenseService.deleteExpense(expense1, testUser.getId());
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("980.00"));

        expenseService.deleteExpense(expense3, testUser.getId());
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(initialBalance);
    }
}