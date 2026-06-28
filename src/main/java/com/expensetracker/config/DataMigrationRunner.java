package com.expensetracker.config;

import com.expensetracker.entity.User;
import com.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataMigrationRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Users already exist — skipping seed user creation and data migration.");
            return;
        }

        log.info("No users found — creating seed user and migrating existing data...");

        // Create seed user
        User seedUser = User.builder()
                .email("admin@expensetracker.local")
                .password(passwordEncoder.encode("changeme"))
                .displayName("Admin")
                .build();
        seedUser = userRepository.save(seedUser);

        log.info("Created seed user: email='{}', id={}", seedUser.getEmail(), seedUser.getId());

        // Migrate orphaned expenses to seed user
        int expensesMigrated = jdbcTemplate.update(
                "UPDATE expenses SET user_id = ? WHERE user_id IS NULL",
                seedUser.getId()
        );
        log.info("Migrated {} orphaned expenses to seed user", expensesMigrated);

        // Migrate orphaned merchant categories to seed user
        int merchantsMigrated = jdbcTemplate.update(
                "UPDATE merchant_categories SET user_id = ? WHERE user_id IS NULL",
                seedUser.getId()
        );
        log.info("Migrated {} orphaned merchant categories to seed user", merchantsMigrated);

        log.info("Data migration complete. Seed user created: email='{}'", seedUser.getEmail());
    }
}
