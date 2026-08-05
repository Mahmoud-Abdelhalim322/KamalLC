package com.kamal.lc.common.config;

import com.kamal.lc.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration:
 * - Stateless (JWT-based, no sessions)
 * - /api/auth/login is public
 * - Specific /api/** endpoints require respective permissions or SUPER_ADMIN role
 * - Static resources (Angular) are public
 * - CORS configured for Angular dev server
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/inventory/**").hasAnyAuthority("INVENTORY_VIEW", "INVENTORY_MANAGE")
                .requestMatchers("/api/fleet/**").hasAnyAuthority("FLEET_VIEW", "FLEET_MANAGE")
                .requestMatchers("/api/shifts/**").hasAnyAuthority("SHIFTS_VIEW", "SHIFTS_MANAGE")
                .requestMatchers("/api/payroll/**").hasAnyAuthority("PAYROLL_VIEW", "PAYROLL_MANAGE")
                .requestMatchers("/api/reports/**").hasAuthority("REPORTS_VIEW")
                .requestMatchers("/api/supplier-revenue/**").hasAuthority("REPORTS_VIEW")
                .requestMatchers("/api/custody/**").hasAuthority("CUSTODY_VIEW")
                .requestMatchers("/api/loans/**").hasAnyAuthority("LOANS_VIEW", "LOANS_MANAGE")
                .requestMatchers("/api/settings/**").hasAuthority("SETTINGS_MANAGE")
                .requestMatchers("/api/imports/**").hasAuthority("IMPORTS_MANAGE")
                .requestMatchers("/api/captains/**").hasAnyAuthority("MEMBERS_VIEW", "MEMBERS_MANAGE")
                .requestMatchers("/api/staff/**").hasAnyAuthority("MEMBERS_VIEW", "MEMBERS_MANAGE")
                .requestMatchers("/api/supplier-ids/**").hasAnyAuthority("MEMBERS_VIEW", "MEMBERS_MANAGE")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()   // Angular static files
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200", "http://localhost:8082"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
