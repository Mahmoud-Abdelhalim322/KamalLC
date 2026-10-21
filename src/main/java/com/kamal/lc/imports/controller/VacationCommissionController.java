package com.kamal.lc.imports.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.imports.dto.VacationCommissionResponse;
import com.kamal.lc.imports.entity.VacationCommission;
import com.kamal.lc.imports.repository.VacationCommissionRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vacation-commissions")
@RequiredArgsConstructor
public class VacationCommissionController {

    private final VacationCommissionRepository vacationCommissionRepo;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<VacationCommissionResponse>>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {

        Specification<VacationCommission> spec = (root, query, cb) -> {
            // Eagerly fetch supplier and captain to avoid LazyInitializationException
            root.fetch("supplier", JoinType.LEFT);
            root.fetch("captain", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();

            if (from != null && to != null) {
                predicates.add(cb.between(root.get("vacationDate"), from, to));
            } else if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("vacationDate"), from));
            } else if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("vacationDate"), to));
            }

            if (supplierId != null) {
                predicates.add(cb.equal(root.get("supplier").get("id"), supplierId));
            }

            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                Predicate searchDays = cb.like(cb.lower(root.get("daysCode")), term);
                Predicate searchZone = cb.like(cb.lower(root.get("zoneName")), term);
                Predicate searchExtCapt = cb.like(cb.lower(root.get("externalCaptainId")), term);
                var captainJoin = root.join("captain", JoinType.LEFT);
                Predicate searchCaptName = cb.like(cb.lower(captainJoin.get("fullNameAr")), term);
                Predicate searchCaptCode = cb.like(cb.lower(captainJoin.get("kamalCode")), term);

                predicates.add(cb.or(searchDays, searchZone, searchExtCapt, searchCaptName, searchCaptCode));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<VacationCommission> resultPage = vacationCommissionRepo.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "vacationDate", "createdAt")));

        Page<VacationCommissionResponse> dtoPage = resultPage.map(this::toResponse);

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<VacationCommissionResponse>builder()
                .content(dtoPage.getContent())
                .page(dtoPage.getNumber())
                .size(dtoPage.getSize())
                .totalElements(dtoPage.getTotalElements())
                .totalPages(dtoPage.getTotalPages())
                .last(dtoPage.isLast())
                .build()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SHIFTS_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        if (!vacationCommissionRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        vacationCommissionRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok("تم حذف سجل عمولة الإجازة بنجاح", null));
    }

    private VacationCommissionResponse toResponse(VacationCommission vc) {
        return VacationCommissionResponse.builder()
                .id(vc.getId())
                .daysCode(vc.getDaysCode())
                .supplierId(vc.getSupplier() != null ? vc.getSupplier().getId() : null)
                .supplierName(vc.getSupplier() != null ? vc.getSupplier().getName() : vc.getSupplierName())
                .externalCaptainId(vc.getExternalCaptainId())
                .captainId(vc.getCaptain() != null ? vc.getCaptain().getId() : null)
                .captainCode(vc.getCaptain() != null ? vc.getCaptain().getKamalCode() : null)
                .captainName(vc.getCaptain() != null ? vc.getCaptain().getFullNameAr() : null)
                .zoneName(vc.getZoneName())
                .flag(vc.getFlag())
                .note(vc.getNote())
                .amount(vc.getAmount())
                .vacationDate(vc.getVacationDate())
                .importBatchId(vc.getImportBatch() != null ? vc.getImportBatch().getId() : null)
                .createdAt(vc.getCreatedAt())
                .build();
    }
}
