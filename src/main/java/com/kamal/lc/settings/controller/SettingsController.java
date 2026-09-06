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

    @PostMapping(value = "/logo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SettingResponse>> uploadLogo(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        SettingResponse updated = settingsService.uploadLogo(file);
        return ResponseEntity.ok(ApiResponse.ok("تم رفع وتحديث شعار الشركة بنجاح", updated));
    }

    @GetMapping("/logo/file/{filename:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getLogoFile(@PathVariable String filename) {
        org.springframework.core.io.Resource resource = settingsService.loadLogo(filename);
        String contentType = "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (filename.endsWith(".webp")) {
            contentType = "image/webp";
        } else if (filename.endsWith(".gif")) {
            contentType = "image/gif";
        } else if (filename.endsWith(".svg")) {
            contentType = "image/svg+xml";
        }
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(resource);
    }
}
