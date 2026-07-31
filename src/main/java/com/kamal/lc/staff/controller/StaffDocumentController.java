package com.kamal.lc.staff.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.staff.dto.StaffDetailResponse;
import com.kamal.lc.staff.service.StaffDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffDocumentController {

    private final StaffDocumentService documentService;

    @GetMapping("/document-types")
    public ResponseEntity<ApiResponse<List<String>>> documentTypes() {
        return ResponseEntity.ok(ApiResponse.ok(StaffDocumentService.DOCUMENT_TYPES));
    }

    @GetMapping("/{staffId}/documents")
    public ResponseEntity<ApiResponse<List<StaffDetailResponse.DocumentInfo>>> list(
            @PathVariable UUID staffId) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.listByStaff(staffId)));
    }

    @PostMapping(value = "/{staffId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StaffDetailResponse.DocumentInfo>> upload(
            @PathVariable UUID staffId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam(value = "note", required = false) String note) {

        return ResponseEntity.ok(ApiResponse.ok("تم رفع المستند بنجاح",
                documentService.upload(staffId, file, documentType, note)));
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable UUID documentId) {
        StaffDocumentService.DocumentDownload download = documentService.download(documentId);

        String contentType = download.contentType() != null
                ? download.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        String safeFileName = download.fileName() != null ? download.fileName() : "document";
        String encodedName = java.net.URLEncoder.encode(safeFileName, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .body(download.resource());
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID documentId) {
        documentService.delete(documentId);
        return ResponseEntity.ok(ApiResponse.ok("تم حذف المستند"));
    }
}
