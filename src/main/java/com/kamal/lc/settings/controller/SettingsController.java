package com.kamal.lc.settings.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.settings.dto.SettingResponse;
import com.kamal.lc.settings.dto.SettingUpdateRequest;
import com.kamal.lc.settings.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    /** Active settings grouped by section, as the settings screen renders them. */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<SettingResponse>>>> current() {
        return ResponseEntity.ok(ApiResponse.ok(settingsService.getCurrentGrouped()));
    }

    @GetMapping("/{key}/history")
    public ResponseEntity<ApiResponse<List<SettingResponse>>> history(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.ok(settingsService.getHistory(key)));
    }

    @PutMapping("/{key}")
    public ResponseEntity<ApiResponse<SettingResponse>> update(
            @PathVariable String key,
            @Valid @RequestBody SettingUpdateRequest request) {
        SettingResponse updated = settingsService.update(key, request.getValue(), request.getEffectiveFrom());
        return ResponseEntity.ok(ApiResponse.ok("تم تحديث الإعداد بنجاح", updated));
    }
}
