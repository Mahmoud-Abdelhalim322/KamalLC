package com.kamal.lc.loan.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.loan.dto.*;
import com.kamal.lc.loan.service.LoanService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    /** All members (captains + staff) with their loan summaries, filterable by type. */
    @GetMapping("/members")
    public ResponseEntity<ApiResponse<List<MemberLoanSummaryResponse>>> members(
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.getMembers(type)));
    }

    /** All loans for a specific member. */
    @GetMapping("/members/{memberId}")
    public ResponseEntity<ApiResponse<List<LoanResponse>>> memberLoans(
            @PathVariable UUID memberId,
            @RequestParam String memberType) {
        return ResponseEntity.ok(ApiResponse.ok(loanService.getMemberLoans(memberId, memberType)));
    }

    /** Create a new loan. */
    @PostMapping
    public ResponseEntity<ApiResponse<LoanResponse>> create(@Valid @RequestBody LoanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("تم تسجيل السلفة بنجاح", loanService.createLoan(request)));
    }

    /** Record a manual payment against a loan. */
    @PostMapping("/{loanId}/payments")
    public ResponseEntity<ApiResponse<LoanResponse>> addPayment(
            @PathVariable UUID loanId,
            @Valid @RequestBody LoanPaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("تم تسجيل الدفعة", loanService.addManualPayment(loanId, request)));
    }

    /** Update the monthly deduction amount. */
    @PatchMapping("/{loanId}/deduction")
    public ResponseEntity<ApiResponse<LoanResponse>> updateDeduction(
            @PathVariable UUID loanId,
            @RequestBody DeductionUpdate body) {
        return ResponseEntity.ok(ApiResponse.ok(
                "تم تحديث مبلغ الخصم الشهري", loanService.updateMonthlyDeduction(loanId, body.getMonthlyDeduction())));
    }

    /** Cancel an active loan. */
    @PatchMapping("/{loanId}/cancel")
    public ResponseEntity<ApiResponse<LoanResponse>> cancel(@PathVariable UUID loanId) {
        return ResponseEntity.ok(ApiResponse.ok("تم إلغاء السلفة", loanService.cancelLoan(loanId)));
    }

    @Data
    public static class DeductionUpdate {
        private BigDecimal monthlyDeduction;
    }
}
