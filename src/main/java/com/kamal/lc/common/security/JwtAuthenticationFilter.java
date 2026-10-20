package com.kamal.lc.common.security;

import com.kamal.lc.captain.repository.CaptainRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * JWT filter — extracts token from Authorization header, validates it,
 * sets the SecurityContext, and populates the {@link TenantContext} for the request.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final CaptainRepository captainRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromToken(token);
                UUID captainId = JwtTokenProvider.captainIdOfSubject(username);
                if (captainId != null) {
                    authenticateCaptain(request, token, captainId);
                    filterChain.doFilter(request, response);
                    return;
                }
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    if (userDetails != null && userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        // Set tenant context from JWT claims
                        UUID tenantId = jwtTokenProvider.getTenantIdFromToken(token);
                        if (tenantId != null) {
                            TenantContext.setCurrentTenantId(tenantId);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("JWT token authentication rejected for user '" + username + "': " + e.getMessage());
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            // Always clear tenant context after request completes
            TenantContext.clear();
        }
    }

    /**
     * Captain-app tokens carry no {@code app_user}; the captain itself must still be active
     * (and its company not suspended), so a captain who leaves loses the app at once rather
     * than when the token runs out.
     */
    private void authenticateCaptain(HttpServletRequest request, String token, UUID captainId) {
        UUID tenantId = jwtTokenProvider.getTenantIdFromToken(token);
        if (tenantId == null) {
            return;
        }
        TenantContext.setCurrentTenantId(tenantId);
        if (!captainRepository.isActiveForApp(captainId)) {
            TenantContext.clear();
            logger.warn("Captain token rejected for inactive captain " + captainId);
            return;
        }
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                new CaptainPrincipal(captainId, tenantId), null,
                List.of(new SimpleGrantedAuthority(CaptainPrincipal.ROLE)));
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
