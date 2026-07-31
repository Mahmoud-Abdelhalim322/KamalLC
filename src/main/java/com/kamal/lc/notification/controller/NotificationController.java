package com.kamal.lc.notification.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.notification.dto.NotificationItemDto;
import com.kamal.lc.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationItemDto>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getActiveNotifications(page, limit)));
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count() {
        long count = notificationService.getActiveNotificationCount();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", count)));
    }
}
