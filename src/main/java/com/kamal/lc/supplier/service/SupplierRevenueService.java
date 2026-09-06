package com.kamal.lc.supplier.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.shift.entity.Shift;
import com.kamal.lc.shift.repository.ShiftRepository;
import com.kamal.lc.supplier.dto.SupplierRevenueDtos.*;
import com.kamal.lc.supplier.entity.Supplier;
import com.kamal.lc.supplier.entity.SupplierAdjustment;
import com.kamal.lc.supplier.repository.SupplierAdjustmentRepository;
import com.kamal.lc.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierRevenueService {

    private final SupplierRepository supplierRepo;
    private final ShiftRepository shiftRepo;
    private final SupplierAdjustmentRepository adjustmentRepo;
    private final com.kamal.lc.auth.service.CaptainAllocationService allocationService;

    public SupplierRevenueSummaryResponse getRevenueSummary(LocalDate from, LocalDate to) {
        List<Supplier> suppliers = supplierRepo.findAll();
        List<Shift> shifts = shiftRepo.findByShiftDateBetween(from, to);
        List<UUID> accessibleIds = allocationService.getCurrentUserAccessibleCaptainIds();
        if (accessibleIds != null) {
            shifts = shifts.stream()
                    .filter(s -> s.getCaptain() != null && accessibleIds.contains(s.getCaptain().getId()))
                    .toList();
        }
        List<SupplierAdjustment> adjustments = adjustmentRepo.findByAdjustmentDateBetweenOrderByAdjustmentDateDesc(from, to);

        Map<UUID, List<Shift>> shiftsBySupplier = groupShiftsBySupplier(shifts, suppliers);
        Map<UUID, List<SupplierAdjustment>> adjustmentsBySupplier = groupAdjustmentsBySupplier(adjustments);

        List<SupplierRowSummary> rows = new ArrayList<>();

        for (Supplier supplier : suppliers) {
            List<Shift> supplierShifts = shiftsBySupplier.getOrDefault(supplier.getId(), Collections.emptyList());
            List<SupplierAdjustment> supplierAdjustments = adjustmentsBySupplier.getOrDefault(supplier.getId(), Collections.emptyList());

            List<Shift> countedShifts = supplierShifts.stream().filter(s -> !s.isExcluded()).toList();

            int totalShifts = countedShifts.size();
            int totalDelivered = countedShifts.stream().mapToInt(s -> s.getTotalDelivered() != null ? s.getTotalDelivered() : 0).sum();
            BigDecimal workingHours = sum(countedShifts, Shift::getWorkingHours);
            BigDecimal baseRevenue = sum(countedShifts, Shift::getSupplierRevenue);
            BigDecimal extraRevenue = sum(countedShifts, Shift::getSupplierExtraRevenue);

            BigDecimal additionsTotal = sumAdjustments(supplierAdjustments, "ADDITION");
            BigDecimal deductionsTotal = sumAdjustments(supplierAdjustments, "DEDUCTION");

            BigDecimal gross = baseRevenue.add(extraRevenue);
            BigDecimal net = gross.add(additionsTotal).subtract(deductionsTotal);

            rows.add(SupplierRowSummary.builder()
                    .supplierId(supplier.getId())
                    .supplierCode(supplier.getCode())
                    .supplierName(supplier.getName())
                    .color(supplier.getColor())
                    .totalShifts(totalShifts)
                    .totalDelivered(totalDelivered)
                    .workingHours(scale(workingHours, 2))
                    .baseRevenue(scale(baseRevenue, 2))
                    .extraRevenue(scale(extraRevenue, 2))
                    .additionsTotal(scale(additionsTotal, 2))
                    .deductionsTotal(scale(deductionsTotal, 2))
                    .netRevenue(scale(net, 2))
                    .build());
        }

        // Handle shifts for suppliers not in DB (if any)
        UUID unassignedId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        List<Shift> unassignedShifts = shiftsBySupplier.getOrDefault(unassignedId, Collections.emptyList());
        if (!unassignedShifts.isEmpty()) {
            List<Shift> countedShifts = unassignedShifts.stream().filter(s -> !s.isExcluded()).toList();
            int totalShifts = countedShifts.size();
            int totalDelivered = countedShifts.stream().mapToInt(s -> s.getTotalDelivered() != null ? s.getTotalDelivered() : 0).sum();
            BigDecimal workingHours = sum(countedShifts, Shift::getWorkingHours);
            BigDecimal baseRevenue = sum(countedShifts, Shift::getSupplierRevenue);
            BigDecimal extraRevenue = sum(countedShifts, Shift::getSupplierExtraRevenue);
            BigDecimal gross = baseRevenue.add(extraRevenue);

            rows.add(SupplierRowSummary.builder()
                    .supplierId(null)
                    .supplierCode("UNASSIGNED")
                    .supplierName("غير محدد")
                    .color("#9E9E9E")
                    .totalShifts(totalShifts)
                    .totalDelivered(totalDelivered)
                    .workingHours(scale(workingHours, 2))
                    .baseRevenue(scale(baseRevenue, 2))
                    .extraRevenue(scale(extraRevenue, 2))
                    .additionsTotal(BigDecimal.ZERO)
                    .deductionsTotal(BigDecimal.ZERO)
                    .netRevenue(scale(gross, 2))
                    .build());
        }

        int globalShifts = rows.stream().mapToInt(SupplierRowSummary::getTotalShifts).sum();
        int globalOrders = rows.stream().mapToInt(SupplierRowSummary::getTotalDelivered).sum();
        BigDecimal globalGross = rows.stream().map(r -> r.getBaseRevenue().add(r.getExtraRevenue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal globalAdditions = rows.stream().map(SupplierRowSummary::getAdditionsTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal globalDeductions = rows.stream().map(SupplierRowSummary::getDeductionsTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal globalNetAdjustments = globalAdditions.subtract(globalDeductions);
        BigDecimal globalNet = globalGross.add(globalNetAdjustments);

        return SupplierRevenueSummaryResponse.builder()
                .periodFrom(from.toString())
                .periodTo(to.toString())
                .totalSuppliers(rows.size())
                .totalShifts(globalShifts)
                .totalOrders(globalOrders)
                .grossRevenue(scale(globalGross, 2))
                .netAdjustments(scale(globalNetAdjustments, 2))
                .netRevenue(scale(globalNet, 2))
                .rows(rows)
                .build();
    }

    public SupplierStatementResponse getSupplierStatement(UUID supplierId, LocalDate from, LocalDate to) {
        Supplier supplier = supplierRepo.findById(supplierId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المورد غير موجود"));

        List<Shift> allShifts = shiftRepo.findByShiftDateBetween(from, to);
        List<SupplierAdjustment> adjustments = adjustmentRepo.findBySupplierIdAndAdjustmentDateBetweenOrderByAdjustmentDateDesc(supplierId, from, to);

        List<Shift> supplierShifts = allShifts.stream()
                .filter(s -> (s.getSupplier() != null && supplierId.equals(s.getSupplier().getId()))
                        || (s.getSupplier() == null && s.getSupplierExternalId() != null && s.getSupplierExternalId().equalsIgnoreCase(supplier.getCode())))
                .sorted(Comparator.comparing(Shift::getShiftDate))
                .toList();

        List<Shift> countedShifts = supplierShifts.stream().filter(s -> !s.isExcluded()).toList();

        int totalShifts = countedShifts.size();
        int totalDelivered = countedShifts.stream().mapToInt(s -> s.getTotalDelivered() != null ? s.getTotalDelivered() : 0).sum();
        BigDecimal workingHours = sum(countedShifts, Shift::getWorkingHours);
        BigDecimal baseRevenue = sum(countedShifts, Shift::getSupplierRevenue);
        BigDecimal extraRevenue = sum(countedShifts, Shift::getSupplierExtraRevenue);

        BigDecimal additionsTotal = sumAdjustments(adjustments, "ADDITION");
        BigDecimal deductionsTotal = sumAdjustments(adjustments, "DEDUCTION");
        BigDecimal gross = baseRevenue.add(extraRevenue);
        BigDecimal net = gross.add(additionsTotal).subtract(deductionsTotal);

        List<SupplierShiftDetail> shiftDetails = supplierShifts.stream().map(s -> SupplierShiftDetail.builder()
                .shiftId(s.getId())
                .shiftDate(s.getShiftDate())
                .externalShiftId(s.getExternalShiftId())
                .ninjaCaptainId(s.getNinjaCaptainId())
                .captainName(s.getCaptain() != null ? s.getCaptain().getFullNameAr() : s.getNinjaCaptainName())
                .zoneName(s.getZoneName())
                .totalDelivered(s.getTotalDelivered() != null ? s.getTotalDelivered() : 0)
                .workingHours(scale(s.getWorkingHours(), 2))
                .supplierRevenue(scale(s.getSupplierRevenue(), 2))
                .supplierExtraRevenue(scale(s.getSupplierExtraRevenue(), 2))
                .excluded(s.isExcluded())
                .build()).toList();

        List<SupplierAdjustmentResponse> adjustmentResponses = adjustments.stream().map(this::toAdjustmentResponse).toList();

        return SupplierStatementResponse.builder()
                .supplierId(supplier.getId())
                .supplierName(supplier.getName())
                .supplierCode(supplier.getCode())
                .color(supplier.getColor())
                .periodFrom(from.toString())
                .periodTo(to.toString())
                .totalShifts(totalShifts)
                .totalOrders(totalDelivered)
                .workingHours(scale(workingHours, 2))
                .baseRevenue(scale(baseRevenue, 2))
                .extraRevenue(scale(extraRevenue, 2))
                .additionsTotal(scale(additionsTotal, 2))
                .deductionsTotal(scale(deductionsTotal, 2))
                .netRevenue(scale(net, 2))
                .shifts(shiftDetails)
                .adjustments(adjustmentResponses)
                .build();
    }

    @Transactional
    public SupplierAdjustmentResponse addAdjustment(SupplierAdjustmentRequest request, String username) {
        if (request.getSupplierId() == null) {
            throw new BusinessException("INVALID_INPUT", "يجب تحديد المورد");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_INPUT", "مبلغ التسوية يجب أن يكون أكبر من الصفر");
        }
        if (request.getCategory() == null || request.getCategory().isBlank()) {
            throw new BusinessException("INVALID_INPUT", "يجب اختيار أو كتابة بند التسوية");
        }
        if (request.getType() == null || (!request.getType().equals("ADDITION") && !request.getType().equals("DEDUCTION"))) {
            throw new BusinessException("INVALID_INPUT", "نوع العملية يجب أن يكون إضافة (ADDITION) أو خصم (DEDUCTION)");
        }

        Supplier supplier = supplierRepo.findById(request.getSupplierId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المورد غير موجود"));

        SupplierAdjustment adjustment = SupplierAdjustment.builder()
                .supplier(supplier)
                .type(request.getType())
                .category(request.getCategory().trim())
                .amount(request.getAmount())
                .adjustmentDate(request.getAdjustmentDate() != null ? request.getAdjustmentDate() : LocalDate.now())
                .notes(request.getNotes())
                .createdBy(username != null ? username : "نظام")
                .build();

        SupplierAdjustment saved = adjustmentRepo.save(adjustment);
        return toAdjustmentResponse(saved);
    }

    @Transactional
    public void deleteAdjustment(UUID adjustmentId) {
        SupplierAdjustment adjustment = adjustmentRepo.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "حركة التسوية غير موجودة"));
        adjustmentRepo.delete(adjustment);
    }

    // ---- Helpers ----

    private Map<UUID, List<Shift>> groupShiftsBySupplier(List<Shift> shifts, List<Supplier> suppliers) {
        Map<String, UUID> codeToId = new HashMap<>();
        suppliers.forEach(s -> codeToId.put(s.getCode().toUpperCase(), s.getId()));

        Map<UUID, List<Shift>> map = new HashMap<>();
        UUID unassignedId = UUID.fromString("00000000-0000-0000-0000-000000000000");

        for (Shift s : shifts) {
            UUID targetId;
            if (s.getSupplier() != null) {
                targetId = s.getSupplier().getId();
            } else if (s.getSupplierExternalId() != null && codeToId.containsKey(s.getSupplierExternalId().toUpperCase())) {
                targetId = codeToId.get(s.getSupplierExternalId().toUpperCase());
            } else {
                targetId = unassignedId;
            }
            map.computeIfAbsent(targetId, k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    private Map<UUID, List<SupplierAdjustment>> groupAdjustmentsBySupplier(List<SupplierAdjustment> adjustments) {
        Map<UUID, List<SupplierAdjustment>> map = new HashMap<>();
        for (SupplierAdjustment a : adjustments) {
            if (a.getSupplier() != null) {
                map.computeIfAbsent(a.getSupplier().getId(), k -> new ArrayList<>()).add(a);
            }
        }
        return map;
    }

    private BigDecimal sumAdjustments(List<SupplierAdjustment> list, String type) {
        return list.stream()
                .filter(a -> type.equalsIgnoreCase(a.getType()))
                .map(SupplierAdjustment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SupplierAdjustmentResponse toAdjustmentResponse(SupplierAdjustment a) {
        return SupplierAdjustmentResponse.builder()
                .id(a.getId())
                .supplierId(a.getSupplier().getId())
                .supplierName(a.getSupplier().getName())
                .type(a.getType())
                .category(a.getCategory())
                .amount(scale(a.getAmount(), 2))
                .adjustmentDate(a.getAdjustmentDate())
                .notes(a.getNotes())
                .createdBy(a.getCreatedBy())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private static BigDecimal sum(List<Shift> shifts, java.util.function.Function<Shift, BigDecimal> field) {
        return shifts.stream().map(field).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value != null ? value.setScale(scale, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }
}
