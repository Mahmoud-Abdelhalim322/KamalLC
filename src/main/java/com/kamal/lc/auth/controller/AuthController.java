package com.kamal.lc.auth.controller;

import com.kamal.lc.auth.dto.LoginRequest;
import com.kamal.lc.auth.dto.LoginResponse;
import com.kamal.lc.auth.service.AuthService;
import com.kamal.lc.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller — login endpoint.
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
}
