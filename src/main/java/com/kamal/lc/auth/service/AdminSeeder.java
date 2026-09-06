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
 * Seeds admin accounts on first startup only. Existing accounts are never
 * overwritten — this prevents user-changed passwords from being reset on restart.
 *
 * <p><b>Platform Admin</b> (kslsc) — manages the entire SaaS platform.
 * <p><b>Company Admin</b> — per-tenant admin with full permissions within their tenant.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username}")
    private String companyAdminUsername;

    @Value("${app.admin.password}")
    private String companyAdminPassword;

    @Value("${app.platform-admin.username:kslsc}")
    private String platformAdminUsername;

    @Value("${app.platform-admin.password:admin@kslsc}")
    private String platformAdminPassword;

    @Override
    public void run(String... args) {
        // ---- Platform Admin (SaaS Super Admin) ----
        userRepository.findByUsername(platformAdminUsername).ifPresentOrElse(
            user -> {
                // Only fix role/active if needed — NEVER overwrite the password
                boolean changed = false;
                if (!"PLATFORM_ADMIN".equals(user.getRole())) {
                    user.setRole("PLATFORM_ADMIN");
                    changed = true;
                }
                if (!user.isActive()) {
                    user.setActive(true);
                    changed = true;
                }
                if (user.getTenant() != null) {
                    user.setTenant(null);
                    changed = true;
                }
                if (changed) {
                    userRepository.save(user);
                    log.info("✅ Platform admin account role/status corrected: {}", platformAdminUsername);
                } else {
                    log.info("✅ Platform admin account exists (unchanged): {}", platformAdminUsername);
                }
            },
            () -> {
                AppUser admin = AppUser.builder()
                        .username(platformAdminUsername)
                        .passwordHash(passwordEncoder.encode(platformAdminPassword))
                        .role("PLATFORM_ADMIN")
                        .displayName("مدير المنصة")
                        .active(true)
                        .build();
                userRepository.save(admin);
                log.info("✅ Platform admin account created: {}", platformAdminUsername);
            }
        );

        // ---- Legacy SUPER_ADMIN → ADMIN migration (one-time) ----
        userRepository.findByUsername("admin").ifPresent(user -> {
            if ("SUPER_ADMIN".equals(user.getRole())) {
                user.setRole("ADMIN");
                userRepository.save(user);
                log.info("✅ Migrated admin '{}' from SUPER_ADMIN to ADMIN", user.getUsername());
            }
        });

        // ---- Company Admin: create only if not exists ----
        if (userRepository.findByUsername(companyAdminUsername).isEmpty()) {
            // Only relevant if the company admin was never seeded via Flyway
            log.info("ℹ️ Company admin '{}' does not exist — skipping (create via Flyway or UI)", companyAdminUsername);
        } else {
            AppUser existing = userRepository.findByUsername(companyAdminUsername).get();
            // Only migrate role, never touch password
            if ("SUPER_ADMIN".equals(existing.getRole())) {
                existing.setRole("ADMIN");
                userRepository.save(existing);
                log.info("✅ Migrated company admin '{}' from SUPER_ADMIN to ADMIN", companyAdminUsername);
            } else {
                log.info("✅ Company admin account exists (unchanged): {}", companyAdminUsername);
            }
        }
    }
}

