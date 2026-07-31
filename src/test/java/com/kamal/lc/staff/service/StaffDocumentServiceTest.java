package com.kamal.lc.staff.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.service.FileStorageService;
import com.kamal.lc.staff.dto.StaffDetailResponse;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.entity.StaffDocument;
import com.kamal.lc.staff.repository.StaffDocumentRepository;
import com.kamal.lc.staff.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffDocumentServiceTest {

    @Mock
    private StaffRepository staffRepo;

    @Mock
    private StaffDocumentRepository documentRepo;

    @Mock
    private FileStorageService fileStorage;

    @InjectMocks
    private StaffDocumentService documentService;

    private UUID staffId;
    private Staff staff;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID();
        staff = Staff.builder()
                .id(staffId)
                .staffCode("W-1001")
                .staffType("WORKER")
                .fullNameAr("علي أحمد")
                .build();
    }

    @Test
    void upload_ValidDocument_ShouldSaveAndReturnInfo() {
        when(staffRepo.findById(staffId)).thenReturn(Optional.of(staff));
        when(fileStorage.store(any(), eq("staff/" + staffId))).thenReturn("staff/" + staffId + "/passport.pdf");

        StaffDocument savedDoc = StaffDocument.builder()
                .id(UUID.randomUUID())
                .staff(staff)
                .documentType("PASSPORT")
                .fileName("passport.pdf")
                .storedPath("staff/" + staffId + "/passport.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .uploadedAt(OffsetDateTime.now())
                .note("جواز رسمي")
                .build();

        when(documentRepo.save(any())).thenReturn(savedDoc);

        MockMultipartFile file = new MockMultipartFile("file", "passport.pdf", "application/pdf", new byte[1024]);
        StaffDetailResponse.DocumentInfo result = documentService.upload(staffId, file, "PASSPORT", "جواز رسمي");

        assertNotNull(result);
        assertEquals("PASSPORT", result.getDocumentType());
        assertEquals("passport.pdf", result.getFileName());
        assertEquals("جواز رسمي", result.getNote());
        verify(documentRepo).save(any());
    }

    @Test
    void upload_InvalidDocumentType_ShouldThrowException() {
        when(staffRepo.findById(staffId)).thenReturn(Optional.of(staff));

        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", new byte[10]);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                documentService.upload(staffId, file, "INVALID_TYPE", null));

        assertEquals("INVALID_DOCUMENT_TYPE", ex.getErrorCode());
    }

    @Test
    void download_ExistingDocument_ShouldReturnResource() {
        UUID docId = UUID.randomUUID();
        StaffDocument doc = StaffDocument.builder()
                .id(docId)
                .staff(staff)
                .documentType("ID_CARD")
                .fileName("id.png")
                .storedPath("staff/" + staffId + "/id.png")
                .contentType("image/png")
                .build();

        when(documentRepo.findById(docId)).thenReturn(Optional.of(doc));
        Resource mockResource = new ByteArrayResource(new byte[]{1, 2, 3});
        when(fileStorage.load("staff/" + staffId + "/id.png")).thenReturn(mockResource);

        StaffDocumentService.DocumentDownload download = documentService.download(docId);

        assertNotNull(download);
        assertEquals("id.png", download.fileName());
        assertEquals("image/png", download.contentType());
        assertEquals(mockResource, download.resource());
    }

    @Test
    void delete_ExistingDocument_ShouldDeleteFromRepoAndStorage() {
        UUID docId = UUID.randomUUID();
        StaffDocument doc = StaffDocument.builder()
                .id(docId)
                .staff(staff)
                .storedPath("staff/" + staffId + "/id.png")
                .build();

        when(documentRepo.findById(docId)).thenReturn(Optional.of(doc));

        documentService.delete(docId);

        verify(documentRepo).delete(doc);
        verify(fileStorage).delete("staff/" + staffId + "/id.png");
    }
}
