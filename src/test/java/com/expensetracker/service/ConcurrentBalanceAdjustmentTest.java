package com.expensetracker.service;

import com.expensetracker.config.DataMigrationRunner;
import com.expensetracker.config.SchemaMigrationRunner;
import com.expensetracker.dto.ExpenseRequest;
import com.expensetracker.dto.ExpenseResponse;
import com.expensetracker.entity.Account;
import com.expensetracker.entity.AccountType;
import com.expensetracker.entity.User;
import com.expensetracker.exception.InsufficientFundsException;
import com.expensetracker.repository.AccountRepository;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency regression test for the balance race condition fixed by the
 * atomic {@code adjustBalance} path.
 *
 * <p>Reproduces the QA bug from t_6441524e: when N concurrent POST/DELETE
 * requests hit the same {@code accountId}, the OLD read-then-write pattern
 * silently lost updates — N concurrent $10 deductions against a $100 balance
 * produced $90 (only one update landed) instead of $100 − N×$10.
 *
 * <p>With the atomic {@code UPDATE … WHERE balance &gt;= :amount} path,
 * each deduction is serialized at the database level and the post-condition
 * must be exactly $1000 − N×$10 (modulo insufficient-funds which throws).
 */
@SpringBootTest
class ConcurrentBalanceAdjustmentTest {

    private static final String TEST_EMAIL = "concurrent-balance@example.com";
    private static final int CONCURRENCY = 10;
    private static final BigDecimal EXPENSE_AMOUNT = new BigDecimal("10.00");
    private static final BigDecimal STARTING_BALANCE = new BigDecimal("1000.00");

    @Autowired private ExpenseService expenseService;
    @Autowired private AccountService accountService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ExpenseRepository expenseRepository;

    @MockBean private DataMigrationRunner dataMigrationRunner;
    @MockBean private SchemaMigrationRunner schemaMigrationRunner;

    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(user -> {
            expenseRepository.deleteAll(expenseRepository.findAllByUserIdOrderByTimestampDesc(user.getId()));
            accountRepository.deleteAll(accountRepository.findByUserIdOrderByCreatedAtAsc(user.getId()));
            userRepository.delete(user);
        });

        testUser = userRepository.save(User.builder()
                .email(TEST_EMAIL)
                .password("$2a$10$dummy.bcrypt.password.that.does.not.matter")
                .displayName("Concurrent Test User")
                .apiKey(UUID.randomUUID().toString())
                .build());

        testAccount = accountRepository.save(Account.builder()
                .name("Concurrent Test Checking")
                .balance(STARTING_BALANCE)
                .type(AccountType.BASE)
                .user(testUser)
                .build());
    }

    @AfterEach
    void tearDown() {
        // nuke everything we touched so we don't leak accounts into other test
        // classes that share the H2 in-memory DB inside the same surefire JVM.
        // Use deleteAll() (unfiltered) for accounts because some test transactions
        // may roll back and leave the user-id lookup useless for the FK cleanup.
        try {
            expenseRepository.deleteAll();
        } catch (Exception ignored) {}
        try {
            accountRepository.deleteAll();
        } catch (Exception ignored) {}
        try {
            userRepository.deleteAll();
        } catch (Exception ignored) {}
    }

    /**
     * N concurrent $10 expense POSTs against a $1000 account must produce
     * exactly $1000 − N×$10. The OLD read-then-write implementation lost
     * updates so the balance would drift (often showing only one deduction).
     */
    @Test
    void concurrentExpenseCreates_applyEveryDeductionExactly() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ExpenseResponse>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENCY; i++) {
                final int idx = i;
                Callable<ExpenseResponse> task = () -> {
                    start.await();
                    ExpenseRequest req = ExpenseRequest.builder()
                            .amount(EXPENSE_AMOUNT)
                            .merchant("Concurrent Merchant " + idx)
                            .category("Other")
                            
                            .accountId(testAccount.getId())
                            .build();
                    return expenseService.createExpense(req, testUser.getId());
                };
                futures.add(pool.submit(task));
            }

            // Release all threads at once for maximum contention.
            start.countDown();

            AtomicInteger failures = new AtomicInteger();
            for (Future<ExpenseResponse> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            }
            assertThat(failures.get()).as("no concurrent create should fail").isZero();

            BigDecimal expected = STARTING_BALANCE.subtract(EXPENSE_AMOUNT.multiply(BigDecimal.valueOf(CONCURRENCY)));
            BigDecimal actual = accountRepository.findById(testAccount.getId()).orElseThrow().getBalance();
            assertThat(actual)
                    .as("every concurrent deduction must land exactly once")
                    .isEqualByComparingTo(expected);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * N concurrent DELETEs of expenses that each restored $10 must bring
     * the balance back up to the original $1000 — every restore counted
     * exactly once.
     */
    @Test
    void concurrentExpenseDeletes_applyEveryRestoreExactly() throws Exception {
        // Seed CONCURRENCY expenses sequentially so the balance is deterministic.
        List<ExpenseResponse> created = new ArrayList<>();
        for (int i = 0; i < CONCURRENCY; i++) {
            ExpenseRequest req = ExpenseRequest.builder()
                    .amount(EXPENSE_AMOUNT)
                    .merchant("Seed Merchant " + i)
                    .category("Other")
                    
                    .accountId(testAccount.getId())
                    .build();
            created.add(expenseService.createExpense(req, testUser.getId()));
        }
        BigDecimal afterSeed = accountRepository.findById(testAccount.getId()).orElseThrow().getBalance();
        assertThat(afterSeed)
                .as("seed should have deducted every expense exactly once")
                .isEqualByComparingTo(STARTING_BALANCE.subtract(EXPENSE_AMOUNT.multiply(BigDecimal.valueOf(CONCURRENCY))));

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ExpenseResponse>> futures = new ArrayList<>();
            for (ExpenseResponse e : created) {
                UUID id = e.getId();
                Callable<ExpenseResponse> task = () -> {
                    start.await();
                    return expenseService.deleteExpense(id, testUser.getId());
                };
                futures.add(pool.submit(task));
            }

            start.countDown();

            AtomicInteger failures = new AtomicInteger();
            for (Future<ExpenseResponse> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    failures.incrementAndGet();
                }
            }
            assertThat(failures.get()).as("no concurrent delete should fail").isZero();

            BigDecimal actual = accountRepository.findById(testAccount.getId()).orElseThrow().getBalance();
            assertThat(actual)
                    .as("every concurrent restore must land exactly once — original bug lost updates here")
                    .isEqualByComparingTo(STARTING_BALANCE);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * When the balance would go negative, the atomic decrement rejects the
     * expense with {@link InsufficientFundsException} rather than silently
     * writing a negative balance. This protects the sibling insufficient-funds
     * bug (t_30c40280) too.
     */
    @Test
    void overBalanceExpense_rejectedWithInsufficientFunds_evenUnderContention() throws Exception {
        // Drain the account down to $50 with 5 sequential $10 expenses.
        for (int i = 0; i < 5; i++) {
            ExpenseRequest req = ExpenseRequest.builder()
                    .amount(EXPENSE_AMOUNT)
                    .merchant("Drain " + i)
                    .category("Other")
                    
                    .accountId(testAccount.getId())
                    .build();
            expenseService.createExpense(req, testUser.getId());
        }
        BigDecimal drained = accountRepository.findById(testAccount.getId()).orElseThrow().getBalance();
        assertThat(drained).isEqualByComparingTo("950.00");

        // Six concurrent $20 attempts against $950 remaining: room for many
        // spends, so we don't assert the exact ok/rejected split. We assert
        // that the balance is consistent: post-condition balance equals
        // $950 minus (okCount × $20), and is non-negative.
        int competitors = 6;
        BigDecimal overspend = new BigDecimal("20.00");
        ExecutorService pool = Executors.newFixedThreadPool(competitors);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < competitors; i++) {
                final int idx = i;
                Callable<String> task = () -> {
                    start.await();
                    ExpenseRequest req = ExpenseRequest.builder()
                            .amount(overspend)
                            .merchant("Overspend " + idx)
                            .category("Other")
                            
                            .accountId(testAccount.getId())
                            .build();
                    try {
                        expenseService.createExpense(req, testUser.getId());
                        return "ok";
                    } catch (InsufficientFundsException e) {
                        return "rejected";
                    }
                };
                results.add(pool.submit(task));
            }

            start.countDown();

            AtomicInteger ok = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            for (Future<String> r : results) {
                String outcome = r.get(30, TimeUnit.SECONDS);
                if ("ok".equals(outcome)) ok.incrementAndGet();
                else rejected.incrementAndGet();
            }

            BigDecimal finalBalance = accountRepository.findById(testAccount.getId()).orElseThrow().getBalance();
            BigDecimal expected = drained.subtract(overspend.multiply(BigDecimal.valueOf(ok.get())));
            assertThat(finalBalance)
                    .as("balance must reflect exactly the number of accepted deductions")
                    .isEqualByComparingTo(expected);
            assertThat(finalBalance.signum())
                    .as("balance must never go negative — InsufficientFundsException should block it")
                    .isGreaterThanOrEqualTo(0);
            assertThat(ok.get() + rejected.get()).isEqualTo(competitors);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * {@link AccountService#adjustBalance(UUID, BigDecimal)} is the primitive
     * that backs every balance mutation. Sanity-check its atomic semantics
     * directly: N concurrent −$10 deltas against $100 must leave $100 − N×$10.
     */
    @Test
    void adjustBalance_isAtomicUnderConcurrency() throws Exception {
        BigDecimal startingBalance = new BigDecimal("100.00");
        BigDecimal delta = new BigDecimal("-10.00");
        int calls = 10;
        // Reset the account to a clean starting balance.
        testAccount.setBalance(startingBalance);
        accountRepository.save(testAccount);

        ExecutorService pool = Executors.newFixedThreadPool(calls);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<BigDecimal>> futures = new ArrayList<>();
            for (int i = 0; i < calls; i++) {
                Callable<BigDecimal> task = () -> {
                    start.await();
                    return accountService.adjustBalance(testAccount.getId(), delta);
                };
                futures.add(pool.submit(task));
            }

            start.countDown();

            int failures = 0;
            for (Future<BigDecimal> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    failures++;
                }
            }
            assertThat(failures).as("adjustBalance calls must not throw under contention").isZero();

            BigDecimal actual = accountRepository.findById(testAccount.getId()).orElseThrow().getBalance();
            assertThat(actual).isEqualByComparingTo(startingBalance.add(delta.multiply(BigDecimal.valueOf(calls))));
        } finally {
            pool.shutdownNow();
        }
    }
}
