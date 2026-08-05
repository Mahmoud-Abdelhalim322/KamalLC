package com.kamal.lc.auth.service;

import com.kamal.lc.auth.dto.LoginRequest;
import com.kamal.lc.auth.dto.LoginResponse;
import com.kamal.lc.auth.entity.AppUser;

import com.kamal.lc.auth.repository.UserRepository;
import com.kamal.lc.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;


import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Authentication service — validates credentials, refreshes tokens, and generates JWT token.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        String token = jwtTokenProvider.generateToken(authentication);
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

        // Load display name from DB
        String displayName = userRepository.findByUsername(request.getUsername())
                .map(AppUser::getDisplayName)
                .orElse(null);

        return LoginResponse.builder()
                .token(token)
                .role(role)
                .username(request.getUsername())
                .displayName(displayName)
                .expiresIn(expirationMs)
                .permissions(permissions)
                .build();
    }

    public LoginResponse getCurrentUser(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String token = jwtTokenProvider.generateToken(authentication);
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

        String displayName = userRepository.findByUsername(username)
                .map(AppUser::getDisplayName)
                .orElse(null);

        return LoginResponse.builder()
                .token(token)
                .role(role)
                .username(username)
                .displayName(displayName)
                .expiresIn(expirationMs)
                .permissions(permissions)
                .build();
    }
}

