package com.kamal.lc.auth.service;

import com.kamal.lc.auth.entity.AppUser;
import com.kamal.lc.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds or updates the super admin account on application startup.
 * Uses environment variables ADMIN_USERNAME and ADMIN_PASSWORD,
 * with defaults of admin / Admin@12345.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        // Update the seeded admin with the properly hashed password from config
        userRepository.findByUsername("admin").ifPresent(user -> {
            user.setUsername(adminUsername);
            user.setPasswordHash(passwordEncoder.encode(adminPassword));
            user.setActive(true);
            userRepository.save(user);
            log.info("✅ Super admin account updated: {}", adminUsername);
        });

        // If admin doesn't exist yet (e.g., migration was skipped), create it
        if (!userRepository.existsByUsername(adminUsername)) {
            AppUser admin = AppUser.builder()
                    .username(adminUsername)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role("SUPER_ADMIN")
                    .active(true)
                    .build();
            userRepository.save(admin);
            log.info("✅ Super admin account created: {}", adminUsername);
        }
    }
}
