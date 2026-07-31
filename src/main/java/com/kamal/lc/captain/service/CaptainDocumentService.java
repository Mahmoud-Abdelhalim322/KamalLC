package com.kamal.lc.captain.service;

import com.kamal.lc.captain.dto.CaptainDetailResponse;
import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.entity.CaptainDocument;
import com.kamal.lc.captain.repository.CaptainDocumentRepository;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaptainDocumentService {

    /** Mirrors the document_type comment in V3__create_captain_tables.sql. */
    public static final List<String> DOCUMENT_TYPES = List.of(
            "PERSONAL_PHOTO", "ID_CARD", "IQAMA", "PASSPORT",
            "DRIVING_LICENCE", "VEHICLE_REGISTRATION", "CONTRACT", "OTHER");

    private final CaptainRepository captainRepo;
    private final CaptainDocumentRepository documentRepo;
    private final FileStorageService fileStorage;

    public List<CaptainDetailResponse.DocumentInfo> listByCaptain(UUID captainId) {
        requireCaptain(captainId);
        return documentRepo.findByCaptainId(captainId).stream().map(this::toInfo).toList();
    }

    @Transactional
    public CaptainDetailResponse.DocumentInfo upload(UUID captainId, MultipartFile file,
                                                     String documentType, String note) {
        Captain captain = requireCaptain(captainId);

        String type = documentType == null ? "OTHER" : documentType.trim().toUpperCase();
        if (!DOCUMENT_TYPES.contains(type)) {
            throw new BusinessException("INVALID_DOCUMENT_TYPE", "نوع المستند غير صالح");
        }

        String storedPath = fileStorage.store(file, "captains/" + captainId);

        CaptainDocument document = CaptainDocument.builder()
                .captain(captain)
                .documentType(type)
                .fileName(originalNameOf(file))
                .storedPath(storedPath)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .note(note != null && !note.isBlank() ? note.trim() : null)
                .build();

        return toInfo(documentRepo.save(document));
    }

    public DocumentDownload download(UUID documentId) {
        CaptainDocument document = requireDocument(documentId);
        return new DocumentDownload(
                fileStorage.load(document.getStoredPath()),
                document.getFileName(),
                document.getContentType());
    }

    @Transactional
    public void delete(UUID documentId) {
        CaptainDocument document = requireDocument(documentId);
        String storedPath = document.getStoredPath();
        documentRepo.delete(document);
        fileStorage.delete(storedPath);
    }

    private Captain requireCaptain(UUID captainId) {
        return captainRepo.findById(captainId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الكابتن غير موجود"));
    }

    private CaptainDocument requireDocument(UUID documentId) {
        return documentRepo.findById(documentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المستند غير موجود"));
    }

    private String originalNameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "document";
        }
        // Keep the name for display only — strip any directory components.
        return name.replaceAll(".*[/\\\\]", "");
    }

    private CaptainDetailResponse.DocumentInfo toInfo(CaptainDocument d) {
        return CaptainDetailResponse.DocumentInfo.builder()
                .id(d.getId())
                .documentType(d.getDocumentType())
                .fileName(d.getFileName())
                .contentType(d.getContentType())
                .fileSize(d.getFileSize())
                .uploadedAt(d.getUploadedAt().toString())
                .note(d.getNote())
                .build();
    }

    public record DocumentDownload(Resource resource, String fileName, String contentType) {}
}
