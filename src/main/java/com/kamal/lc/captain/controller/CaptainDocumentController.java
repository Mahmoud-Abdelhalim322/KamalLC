package com.kamal.lc.captain.controller;

import com.kamal.lc.captain.dto.CaptainDetailResponse;
import com.kamal.lc.captain.service.CaptainDocumentService;
import com.kamal.lc.common.dto.ApiResponse;
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
@RequestMapping("/api/captains")
@RequiredArgsConstructor
public class CaptainDocumentController {

    private final CaptainDocumentService documentService;

    @GetMapping("/document-types")
    public ResponseEntity<ApiResponse<List<String>>> documentTypes() {
        return ResponseEntity.ok(ApiResponse.ok(CaptainDocumentService.DOCUMENT_TYPES));
    }

    @GetMapping("/{captainId}/documents")
    public ResponseEntity<ApiResponse<List<CaptainDetailResponse.DocumentInfo>>> list(
            @PathVariable UUID captainId) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.listByCaptain(captainId)));
    }

    @PostMapping(value = "/{captainId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CaptainDetailResponse.DocumentInfo>> upload(
            @PathVariable UUID captainId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam(value = "note", required = false) String note) {

        return ResponseEntity.ok(ApiResponse.ok("تم رفع المستند بنجاح",
                documentService.upload(captainId, file, documentType, note)));
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable UUID documentId) {
        CaptainDocumentService.DocumentDownload download = documentService.download(documentId);

        String contentType = download.contentType() != null
                ? download.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        // File names are Arabic more often than not — RFC 5987 encoding keeps them intact.
        String encodedName = java.net.URLEncoder.encode(download.fileName(), StandardCharsets.UTF_8)
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
