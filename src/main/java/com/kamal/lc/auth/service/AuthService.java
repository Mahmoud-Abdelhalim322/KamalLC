package com.kamal.lc.auth.service;

import com.kamal.lc.auth.dto.LoginRequest;
import com.kamal.lc.auth.dto.LoginResponse;
import com.kamal.lc.auth.entity.AppUser;

import com.kamal.lc.auth.repository.UserRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.security.JwtTokenProvider;
import com.kamal.lc.common.security.LoginRateLimiter;
import com.kamal.lc.tenant.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;


import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Authentication service — validates credentials, refreshes tokens, and generates JWT token.
 * Now includes tenant information in the response for multi-tenant support.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;
    private final LoginRateLimiter rateLimiter;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public LoginResponse login(LoginRequest request) {
        // Rate-limit check
        if (rateLimiter.isBlocked(request.getUsername())) {
            long waitSeconds = rateLimiter.getSecondsUntilUnblocked(request.getUsername());
            throw new BusinessException("RATE_LIMITED",
                    "تم تجاوز عدد المحاولات المسموحة. يرجى الانتظار " + (waitSeconds / 60 + 1) + " دقائق");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            rateLimiter.recordFailedAttempt(request.getUsername());
            throw e;
        }

        // Successful login — clear any previous failed attempts
        rateLimiter.clearAttempts(request.getUsername());

        // Load the full user to get tenant info
        AppUser user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        UUID tenantId = user.getTenant() != null ? user.getTenant().getId() : null;

        String token = jwtTokenProvider.generateToken(request.getUsername(), tenantId);
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .orElse("ROLE_MANAGER")
                .replace("ROLE_", "");

        // Load permissions from authorities (already resolved by UserDetailsServiceImpl)
        Set<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toSet());

        return buildLoginResponse(token, role, request.getUsername(), user, permissions);
    }

    public LoginResponse getCurrentUser(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        AppUser user = userRepository.findByUsername(username).orElseThrow();
        UUID tenantId = user.getTenant() != null ? user.getTenant().getId() : null;

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String token = jwtTokenProvider.generateToken(username, tenantId);
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .orElse("ROLE_MANAGER")
                .replace("ROLE_", "");

        Set<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .collect(Collectors.toSet());

        return buildLoginResponse(token, role, username, user, permissions);
    }

    private LoginResponse buildLoginResponse(String token, String role, String username,
                                              AppUser user, Set<String> permissions) {
        Tenant tenant = user.getTenant();

        return LoginResponse.builder()
                .token(token)
                .role(role)
                .username(username)
                .displayName(user.getDisplayName())
                .expiresIn(expirationMs)
                .permissions(permissions)
                .tenantId(tenant != null ? tenant.getId().toString() : null)
                .tenantName(tenant != null ? tenant.getName() : null)
                .tenantLogo(tenant != null ? tenant.getLogo() : null)
                .tenantPrimaryColor(tenant != null ? tenant.getPrimaryColor() : null)
                .tenantSecondaryColor(tenant != null ? tenant.getSecondaryColor() : null)
                .build();
    }
}
