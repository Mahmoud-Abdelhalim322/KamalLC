package com.kamal.lc.captainapp.controller;

import com.kamal.lc.captainapp.dto.CaptainAppDtos.CaptainSession;
import com.kamal.lc.captainapp.dto.CaptainAppDtos.OtpRequest;
import com.kamal.lc.captainapp.dto.CaptainAppDtos.OtpRequestResponse;
import com.kamal.lc.captainapp.dto.CaptainAppDtos.OtpVerifyRequest;
import com.kamal.lc.captainapp.service.CaptainAuthService;
import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.security.CaptainPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Captain app sign-in: a code by SMS, exchanged for a long-lived captain token. */
@RestController
@RequestMapping("/api/captain-app/auth")
@RequiredArgsConstructor
public class CaptainAppAuthController {

    private final CaptainAuthService authService;

    @PostMapping("/otp/request")
    public ResponseEntity<ApiResponse<OtpRequestResponse>> requestCode(@Valid @RequestBody OtpRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("تم إرسال رمز التحقق", authService.requestCode(request.phone())));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<CaptainSession>> verify(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.verifyCode(request.phone(), request.code())));
    }

    /** A fresh token for a still-active captain, so a daily user never sees the sign-in screen again. */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<CaptainSession>> refresh(@AuthenticationPrincipal CaptainPrincipal captain) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(captain.captainId(), captain.tenantId())));
    }
}
