package com.kamal.lc.auth.controller;

import com.kamal.lc.auth.dto.LoginRequest;
import com.kamal.lc.auth.dto.LoginResponse;
import com.kamal.lc.auth.service.AuthService;
import com.kamal.lc.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller — login and profile refresh endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("تم تسجيل الدخول بنجاح", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginResponse>> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "غير مصرح"));
        }
        LoginResponse response = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("تم تحديث البيانات والصلاحيات بنجاح", response));
    }
}
