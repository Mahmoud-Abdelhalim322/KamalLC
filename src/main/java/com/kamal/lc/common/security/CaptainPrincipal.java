package com.kamal.lc.common.security;

import java.security.Principal;
import java.util.UUID;

/**
 * The authenticated principal of a captain-app request. Captains do not have an
 * {@code app_user} row, so the JWT filter builds this straight from the token.
 */
public record CaptainPrincipal(UUID captainId, UUID tenantId) implements Principal {

    public static final String ROLE = "ROLE_CAPTAIN";

    @Override
    public String getName() {
        return JwtTokenProvider.CAPTAIN_SUBJECT_PREFIX + captainId;
    }
}
