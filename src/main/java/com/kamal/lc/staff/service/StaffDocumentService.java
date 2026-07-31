package com.kamal.lc.staff.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.service.FileStorageService;
import com.kamal.lc.staff.dto.StaffDetailResponse;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.entity.StaffDocument;
import com.kamal.lc.staff.repository.StaffDocumentRepository;
import com.kamal.lc.staff.repository.StaffRepository;
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
public class StaffDocumentService {

    public static final List<String> DOCUMENT_TYPES = List.of(
            "PERSONAL_PHOTO", "ID_CARD", "IQAMA", "PASSPORT",
            "DRIVING_LICENCE", "CONTRACT", "WORK_PERMIT", "INSURANCE", "OTHER");

    private final StaffRepository staffRepo;
    private final StaffDocumentRepository documentRepo;
    private final FileStorageService fileStorage;

    public List<StaffDetailResponse.DocumentInfo> listByStaff(UUID staffId) {
        requireStaff(staffId);
        return documentRepo.findByStaffId(staffId).stream().map(this::toInfo).toList();
    }

    @Transactional
    public StaffDetailResponse.DocumentInfo upload(UUID staffId, MultipartFile file,
                                                     String documentType, String note) {
        Staff staff = requireStaff(staffId);

        String type = documentType == null ? "OTHER" : documentType.trim().toUpperCase();
        if (!DOCUMENT_TYPES.contains(type)) {
            throw new BusinessException("INVALID_DOCUMENT_TYPE", "نوع المستند غير صالح");
        }

        String storedPath = fileStorage.store(file, "staff/" + staffId);

        StaffDocument document = StaffDocument.builder()
                .staff(staff)
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
        StaffDocument document = requireDocument(documentId);
        return new DocumentDownload(
                fileStorage.load(document.getStoredPath()),
                document.getFileName(),
                document.getContentType());
    }

    @Transactional
    public void delete(UUID documentId) {
        StaffDocument document = requireDocument(documentId);
        String storedPath = document.getStoredPath();
        documentRepo.delete(document);
        fileStorage.delete(storedPath);
    }

    private Staff requireStaff(UUID staffId) {
        return staffRepo.findById(staffId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الموظف غير موجود"));
    }

    private StaffDocument requireDocument(UUID documentId) {
        return documentRepo.findById(documentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المستند غير موجود"));
    }

    private String originalNameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "document";
        }
        return name.replaceAll(".*[/\\\\]", "");
    }

    private StaffDetailResponse.DocumentInfo toInfo(StaffDocument d) {
        return StaffDetailResponse.DocumentInfo.builder()
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
