package com.kamal.lc.captainapp.controller;

import com.kamal.lc.captainapp.dto.CaptainAppDtos.*;
import com.kamal.lc.captainapp.service.CaptainAppService;
import com.kamal.lc.captainapp.service.CaptainDocumentRequestService;
import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.security.CaptainPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** The captain mobile app's API. Every call reads the signed-in captain's own data only. */
@RestController
@RequestMapping("/api/captain-app")
@RequiredArgsConstructor
public class CaptainAppController {

    private final CaptainAppService appService;
    private final CaptainDocumentRequestService requestService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(@AuthenticationPrincipal CaptainPrincipal captain) {
        return ResponseEntity.ok(ApiResponse.ok(appService.me(captain.captainId())));
    }

    @GetMapping("/home")
    public ResponseEntity<ApiResponse<HomeResponse>> home(@AuthenticationPrincipal CaptainPrincipal captain) {
        return ResponseEntity.ok(ApiResponse.ok(appService.home(captain.captainId())));
    }

    @GetMapping("/shifts")
    public ResponseEntity<ApiResponse<ShiftWeek>> shifts(
            @AuthenticationPrincipal CaptainPrincipal captain,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(appService.shifts(captain.captainId(), from, to)));
    }

    @GetMapping("/shifts/{id}")
    public ResponseEntity<ApiResponse<ShiftDetail>> shift(@AuthenticationPrincipal CaptainPrincipal captain,
                                                          @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(appService.shiftDetail(captain.captainId(), id)));
    }

    /** One calendar month, {@code YYYY-MM}; the current month when omitted. */
    @GetMapping("/earnings")
    public ResponseEntity<ApiResponse<EarningsPeriod>> earnings(@AuthenticationPrincipal CaptainPrincipal captain,
                                                                @RequestParam(required = false) YearMonth month) {
        YearMonth m = month != null ? month : YearMonth.now(java.time.ZoneId.of("Asia/Riyadh"));
        return ResponseEntity.ok(ApiResponse.ok(appService.earnings(captain.captainId(), m)));
    }

    @GetMapping("/payslips")
    public ResponseEntity<ApiResponse<List<PayslipSummary>>> payslips(@AuthenticationPrincipal CaptainPrincipal captain) {
        return ResponseEntity.ok(ApiResponse.ok(appService.payslips(captain.captainId())));
    }

    @GetMapping("/payslips/{id}")
    public ResponseEntity<ApiResponse<PayslipDetail>> payslip(@AuthenticationPrincipal CaptainPrincipal captain,
                                                              @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(appService.payslipDetail(captain.captainId(), id)));
    }

    @GetMapping("/loans")
    public ResponseEntity<ApiResponse<List<LoanItem>>> loans(@AuthenticationPrincipal CaptainPrincipal captain) {
        return ResponseEntity.ok(ApiResponse.ok(appService.loans(captain.captainId())));
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<DocumentsResponse>> documents(@AuthenticationPrincipal CaptainPrincipal captain) {
        return ResponseEntity.ok(ApiResponse.ok(appService.documents(captain.captainId())));
    }

    @PostMapping(value = "/documents/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentRequestItem>> submitDocument(
            @AuthenticationPrincipal CaptainPrincipal captain,
            @RequestParam String kind,
            @RequestParam(required = false) String number,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate newExpiry,
            @RequestParam MultipartFile front,
            @RequestParam(required = false) MultipartFile back) {
        return ResponseEntity.ok(ApiResponse.ok("تم إرسال المستند للمراجعة",
                requestService.submit(captain.captainId(), kind, number, newExpiry, front, back)));
    }

    @GetMapping("/documents/requests")
    public ResponseEntity<ApiResponse<List<DocumentRequestItem>>> documentRequests(
            @AuthenticationPrincipal CaptainPrincipal captain) {
        return ResponseEntity.ok(ApiResponse.ok(appService.documents(captain.captainId()).requests()));
    }

    @GetMapping("/custody")
    public ResponseEntity<ApiResponse<List<CustodyItem>>> custody(@AuthenticationPrincipal CaptainPrincipal captain) {
        return ResponseEntity.ok(ApiResponse.ok(appService.custody(captain.captainId())));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<NotificationItem>>> notifications(
            @AuthenticationPrincipal CaptainPrincipal captain) {
        return ResponseEntity.ok(ApiResponse.ok(appService.notifications(captain.captainId())));
    }

    @PostMapping("/devices")
    public ResponseEntity<ApiResponse<Void>> registerDevice(@AuthenticationPrincipal CaptainPrincipal captain,
                                                            @Valid @RequestBody DeviceRequest request) {
        appService.registerDevice(captain.captainId(), request);
        return ResponseEntity.ok(ApiResponse.ok("تم تسجيل الجهاز", null));
    }

    @DeleteMapping("/devices/{token}")
    public ResponseEntity<ApiResponse<Void>> unregisterDevice(@AuthenticationPrincipal CaptainPrincipal captain,
                                                              @PathVariable String token) {
        appService.unregisterDevice(captain.captainId(), token);
        return ResponseEntity.ok(ApiResponse.ok("تم إلغاء تسجيل الجهاز", null));
    }
}
