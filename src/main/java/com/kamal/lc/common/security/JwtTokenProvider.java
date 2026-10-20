package com.kamal.lc.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * JWT token provider — generates, validates, and extracts claims from JWT tokens.
 * Now includes {@code tenantId} as a custom claim for multi-tenant support.
 */
@Component
public class JwtTokenProvider {

    public static final String CAPTAIN_SUBJECT_PREFIX = "captain:";

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // Ensure the key is at least 256 bits for HS256
        byte[] keyBytes = secret.getBytes();
        this.key = Keys.hmacShaKeyFor(keyBytes.length >= 32 ? keyBytes : 
            java.util.Arrays.copyOf(keyBytes, 32));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return generateToken(userDetails.getUsername(), null);
    }

    public String generateToken(String username) {
        return generateToken(username, null);
    }

    /**
     * Generates a JWT with an optional tenant ID claim.
     * Platform admins have a null tenantId.
     */
    public String generateToken(String username, UUID tenantId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        var builder = Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry);

        if (tenantId != null) {
            builder.claim("tenantId", tenantId.toString());
        }

        return builder.signWith(key).compact();
    }

    /**
     * Captain-app token. The subject carries the captain's id behind {@link #CAPTAIN_SUBJECT_PREFIX}
     * so it can never be mistaken for an {@code app_user} username, and the lifetime is the
     * app's own — a captain stays signed in on their phone far longer than a web session.
     */
    public String generateCaptainToken(UUID captainId, UUID tenantId, long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(CAPTAIN_SUBJECT_PREFIX + captainId)
                .claim("tenantId", tenantId.toString())
                .claim("role", "CAPTAIN")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    /** The captain id of a captain-app token, or {@code null} for a staff token. */
    public static UUID captainIdOfSubject(String subject) {
        return subject != null && subject.startsWith(CAPTAIN_SUBJECT_PREFIX)
                ? UUID.fromString(subject.substring(CAPTAIN_SUBJECT_PREFIX.length()))
                : null;
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Extracts the tenant ID from the JWT claims. Returns null for platform admins.
     */
    public UUID getTenantIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String tenantId = claims.get("tenantId", String.class);
        return tenantId != null ? UUID.fromString(tenantId) : null;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
