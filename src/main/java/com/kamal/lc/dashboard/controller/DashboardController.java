package com.kamal.lc.dashboard.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.dashboard.dto.DashboardResponse;
import com.kamal.lc.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;

/**
 * Dashboard controller — summary cards data.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DashboardResponse dashboard = dashboardService.getDashboardData(from, to);
        return ResponseEntity.ok(ApiResponse.ok(dashboard));
    }

    @org.springframework.web.bind.annotation.PutMapping("/alert-threshold")
    public ResponseEntity<ApiResponse<DashboardResponse>> updateAlertThreshold(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Integer> request) {
        Integer days = request.get("alertDays");
        if (days == null || days <= 0) {
            days = 30;
        }
        dashboardService.updateAlertThreshold(days);
        return ResponseEntity.ok(ApiResponse.ok("تم تحديث مهلة إنذار الوثائق بنجاح", dashboardService.getDashboardData()));
    }
}
