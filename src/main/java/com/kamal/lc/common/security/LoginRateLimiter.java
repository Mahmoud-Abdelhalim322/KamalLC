package com.kamal.lc.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter for login attempts. Uses a sliding-window approach:
 * tracks attempts per username within a configurable time window, and blocks
 * further attempts once the threshold is reached.
 *
 * <p>Entries are lazily cleaned up on each check to prevent unbounded memory growth.
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final int windowSeconds;

    /** Tracks login attempts: username → list of attempt timestamps. */
    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.rate-limit.login.max-attempts:5}") int maxAttempts,
            @Value("${app.rate-limit.login.window-seconds:300}") int windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Checks whether the given username is rate-limited.
     *
     * @return true if the user has exceeded the max login attempts within the window
     */
    public boolean isBlocked(String username) {
        if (username == null || username.isBlank()) return false;

        String key = username.toLowerCase().trim();
        AttemptRecord record = attempts.get(key);

        if (record == null) return false;

        // Remove expired attempts
        record.pruneExpired(windowSeconds);

        return record.count >= maxAttempts;
    }

    /**
     * Records a failed login attempt for the given username.
     */
    public void recordFailedAttempt(String username) {
        if (username == null || username.isBlank()) return;

        String key = username.toLowerCase().trim();
        attempts.compute(key, (k, existing) -> {
            if (existing == null) {
                return new AttemptRecord(1, Instant.now());
            }
            existing.pruneExpired(windowSeconds);
            existing.count++;
            existing.lastAttempt = Instant.now();
            return existing;
        });

        AttemptRecord record = attempts.get(key);
        if (record != null && record.count >= maxAttempts) {
            log.warn("🚫 Rate limit reached for user '{}': {} attempts in {} seconds",
                    key, record.count, windowSeconds);
        }
    }

    /**
     * Clears attempt records for the given username (e.g., on successful login).
     */
    public void clearAttempts(String username) {
        if (username == null || username.isBlank()) return;
        attempts.remove(username.toLowerCase().trim());
    }

    /**
     * Returns the number of seconds until the user can try again, or 0 if not blocked.
     */
    public long getSecondsUntilUnblocked(String username) {
        if (username == null || username.isBlank()) return 0;

        String key = username.toLowerCase().trim();
        AttemptRecord record = attempts.get(key);
        if (record == null || record.count < maxAttempts) return 0;

        long elapsed = Instant.now().getEpochSecond() - record.firstAttemptInWindow.getEpochSecond();
        long remaining = windowSeconds - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Mutable record tracking a user's login attempts within a window.
     */
    private static class AttemptRecord {
        int count;
        Instant firstAttemptInWindow;
        Instant lastAttempt;

        AttemptRecord(int count, Instant now) {
            this.count = count;
            this.firstAttemptInWindow = now;
            this.lastAttempt = now;
        }

        /** Removes attempts outside the sliding window. */
        void pruneExpired(int windowSeconds) {
            if (firstAttemptInWindow != null &&
                Instant.now().getEpochSecond() - firstAttemptInWindow.getEpochSecond() > windowSeconds) {
                // Window expired — reset
                this.count = 0;
                this.firstAttemptInWindow = Instant.now();
            }
        }
    }
}
