package com.kamal.lc.captainapp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A one-time sign-in code sent to a captain's mobile. Only the hash is kept.
 *
 * <p>Deliberately not tenant-aware: it is written and read before the request knows which
 * company the captain belongs to.
 */
@Entity
@Table(name = "captain_otp")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaptainOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "captain_id", nullable = false)
    private UUID captainId;

    /** The last nine digits of the mobile number — see {@code CaptainRepository#findByPhoneKey}. */
    @Column(name = "phone_key", nullable = false, length = 20)
    private String phoneKey;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean isUsable(OffsetDateTime now, int maxAttempts) {
        return consumedAt == null && expiresAt.isAfter(now) && attempts < maxAttempts;
    }
}
