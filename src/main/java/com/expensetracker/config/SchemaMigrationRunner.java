package com.expensetracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        dropBudgetsCategoryColumn();
    }

    private void dropBudgetsCategoryColumn() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'budgets' AND column_name = 'category')",
                Boolean.class
        );
        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute("ALTER TABLE budgets DROP COLUMN category");
            log.info("Dropped legacy 'category' column from budgets table");
        }
    }
}
