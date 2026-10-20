package com.kamal.lc.captainapp.service;

import com.kamal.lc.auth.entity.AppUser;
import com.kamal.lc.auth.repository.UserRepository;
import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.entity.CaptainDocument;
import com.kamal.lc.captain.expiry.DocumentKind;
import com.kamal.lc.captain.repository.CaptainDocumentRepository;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.captain.service.CaptainDocumentService;
import com.kamal.lc.captainapp.dto.CaptainAppDtos.DocumentRequestItem;
import com.kamal.lc.captainapp.dto.CaptainAppDtos.DocumentRequestReview;
import com.kamal.lc.captainapp.entity.CaptainDocumentRequest;
import com.kamal.lc.captainapp.repository.CaptainDocumentRequestRepository;
import com.kamal.lc.changelog.service.ChangeLogWriter;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Renewed documents sent from the captain app. A request only records what the captain
 * claims; the captain's own number and expiry change when a supervisor approves it.
 */
@Service
@RequiredArgsConstructor
public class CaptainDocumentRequestService {

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("iqamaNumber", "رقم الإقامة"),
            Map.entry("iqamaExpiry", "انتهاء الإقامة"),
            Map.entry("workPermitExpiry", "انتهاء تصريح العمل"),
            Map.entry("insuranceNumber", "رقم التأمين"),
            Map.entry("insuranceExpiry", "انتهاء التأمين"),
            Map.entry("passportNumber", "رقم الجواز"),
            Map.entry("passportExpiry", "انتهاء الجواز"),
            Map.entry("drivingLicenceNo", "رقم رخصة القيادة"),
            Map.entry("licenceExpiry", "انتهاء رخصة القيادة"),
            Map.entry("hasLicence", "لديه رخصة"));

    private final CaptainDocumentRequestRepository requestRepo;
    private final CaptainRepository captainRepo;
    private final CaptainDocumentRepository documentRepo;
    private final CaptainDocumentService documentService;
    private final ChangeLogWriter changeLogWriter;
    private final UserRepository userRepo;

    // ---- Captain side ----

    @Transactional
    public DocumentRequestItem submit(UUID captainId, String kindName, String number, LocalDate newExpiry,
                                      MultipartFile front, MultipartFile back) {
        DocumentKind kind = kindOf(kindName);
        if (newExpiry == null || !newExpiry.isAfter(CaptainAppService.today())) {
            throw new BusinessException("INVALID_EXPIRY", "تاريخ الانتهاء الجديد يجب أن يكون في المستقبل");
        }
        if (front == null || front.isEmpty()) {
            throw new BusinessException("FRONT_REQUIRED", "صورة الوجه الأمامي للمستند مطلوبة");
        }
        if (requestRepo.existsByCaptainIdAndDocumentKindAndStatus(captainId, kind.name(), CaptainDocumentRequest.PENDING)) {
            throw new BusinessException("REQUEST_PENDING", "يوجد طلب تحديث قيد المراجعة لهذا المستند");
        }
        Captain captain = captainRepo.findById(captainId)
                .orElseThrow(() -> new BusinessException("CAPTAIN_NOT_FOUND", "الكابتن غير موجود"));

        String type = storedTypeOf(kind);
        String note = "تحديث من تطبيق الكابتن: " + kind.getLabelAr();
        CaptainDocument frontDoc = documentRepo.getReferenceById(
                documentService.upload(captainId, front, type, note).getId());
        CaptainDocument backDoc = back == null || back.isEmpty() ? null
                : documentRepo.getReferenceById(documentService.upload(captainId, back, type, note).getId());

        CaptainDocumentRequest request = requestRepo.save(CaptainDocumentRequest.builder()
                .captain(captain)
                .documentKind(kind.name())
                .documentNumber(number != null && !number.isBlank() ? number.trim() : null)
                .newExpiry(newExpiry)
                .frontDocument(frontDoc)
                .backDocument(backDoc)
                .status(CaptainDocumentRequest.PENDING)
                .createdAt(OffsetDateTime.now())
                .build());
        return CaptainAppService.requestItem(request);
    }

    // ---- Supervisor side ----

    @Transactional(readOnly = true)
    public PageResponse<DocumentRequestReview> list(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        Page<CaptainDocumentRequest> result = status == null || status.isBlank()
                ? requestRepo.findAllByOrderByCreatedAtDesc(pageable)
                : requestRepo.findByStatusOrderByCreatedAtAsc(status.trim().toUpperCase(), pageable);
        return PageResponse.<DocumentRequestReview>builder()
                .content(result.getContent().stream().map(this::review).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return requestRepo.countByStatus(CaptainDocumentRequest.PENDING);
    }

    @Transactional
    public DocumentRequestReview approve(UUID requestId, String note) {
        CaptainDocumentRequest request = pending(requestId);
        // The snapshot reads fields reflectively, which a lazy proxy leaves empty.
        Captain captain = (Captain) Hibernate.unproxy(request.getCaptain());

        Map<String, Object> before = ChangeLogWriter.snapshot(captain, FIELD_LABELS);
        apply(captain, DocumentKind.valueOf(request.getDocumentKind()), request.getDocumentNumber(), request.getNewExpiry());
        captainRepo.save(captain);
        Map<String, Object> after = ChangeLogWriter.snapshot(captain, FIELD_LABELS);

        String reviewer = reviewer();
        changeLogWriter.logFieldChanges("CAPTAIN", captain.getId(),
                captain.getFullNameAr() + " (" + captain.getKamalCode() + ")",
                before, after, FIELD_LABELS, reviewer);

        return close(request, CaptainDocumentRequest.APPROVED, note, reviewer);
    }

    @Transactional
    public DocumentRequestReview reject(UUID requestId, String note) {
        if (note == null || note.isBlank()) {
            throw new BusinessException("NOTE_REQUIRED", "يرجى كتابة سبب الرفض ليظهر للكابتن");
        }
        return close(pending(requestId), CaptainDocumentRequest.REJECTED, note, reviewer());
    }

    private DocumentRequestReview close(CaptainDocumentRequest request, String status, String note, String reviewer) {
        request.setStatus(status);
        request.setReviewNote(note != null && !note.isBlank() ? note.trim() : null);
        request.setReviewedBy(reviewer);
        request.setReviewedAt(OffsetDateTime.now());
        return review(requestRepo.save(request));
    }

    private CaptainDocumentRequest pending(UUID requestId) {
        CaptainDocumentRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new BusinessException("REQUEST_NOT_FOUND", "الطلب غير موجود"));
        if (!request.isPending()) {
            throw new BusinessException("REQUEST_CLOSED", "تمت مراجعة هذا الطلب مسبقاً");
        }
        return request;
    }

    /** Writes the renewed number and expiry where {@link DocumentKind} reads them from. */
    private static void apply(Captain c, DocumentKind kind, String number, LocalDate expiry) {
        switch (kind) {
            case IQAMA -> {
                c.setIqamaExpiry(expiry);
                if (number != null) c.setIqamaNumber(number);
            }
            case WORK_PERMIT -> c.setWorkPermitExpiry(expiry);
            case INSURANCE -> {
                c.setInsuranceExpiry(expiry);
                if (number != null) c.setInsuranceNumber(number);
            }
            case PASSPORT -> {
                c.setPassportExpiry(expiry);
                if (number != null) c.setPassportNumber(number);
            }
            case DRIVING_LICENCE -> {
                c.setLicenceExpiry(expiry);
                c.setHasLicence(true);
                if (number != null) c.setDrivingLicenceNo(number);
            }
        }
    }

    private DocumentRequestReview review(CaptainDocumentRequest r) {
        Captain c = r.getCaptain();
        DocumentKind kind = DocumentKind.valueOf(r.getDocumentKind());
        return new DocumentRequestReview(r.getId(), c.getId(), c.getKamalCode(), c.getFullNameAr(),
                kind.name(), kind.getLabelAr(), kind.numberOf(c), kind.expiryOf(c),
                r.getDocumentNumber(), r.getNewExpiry(), r.getStatus(),
                r.getFrontDocument() != null ? r.getFrontDocument().getId() : null,
                r.getBackDocument() != null ? r.getBackDocument().getId() : null,
                r.getCreatedAt(), r.getReviewedBy(), r.getReviewedAt(), r.getReviewNote());
    }

    private static DocumentKind kindOf(String name) {
        try {
            return DocumentKind.valueOf(name == null ? "" : name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_DOCUMENT_KIND", "نوع المستند غير صالح");
        }
    }

    /** The captain file's document type the photos are filed under. */
    private static String storedTypeOf(DocumentKind kind) {
        return switch (kind) {
            case IQAMA -> "IQAMA";
            case PASSPORT -> "PASSPORT";
            case DRIVING_LICENCE -> "DRIVING_LICENCE";
            case INSURANCE, WORK_PERMIT -> "OTHER";
        };
    }

    private String reviewer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "SYSTEM";
        return userRepo.findByUsername(auth.getName()).map(AppUser::getDisplayName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(auth.getName());
    }
}
