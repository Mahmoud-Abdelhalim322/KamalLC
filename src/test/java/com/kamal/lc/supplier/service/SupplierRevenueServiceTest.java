package com.kamal.lc.supplier.service;

import com.kamal.lc.shift.entity.Shift;
import com.kamal.lc.shift.repository.ShiftRepository;
import com.kamal.lc.supplier.dto.SupplierRevenueDtos.*;
import com.kamal.lc.supplier.entity.Supplier;
import com.kamal.lc.supplier.entity.SupplierAdjustment;
import com.kamal.lc.supplier.repository.SupplierAdjustmentRepository;
import com.kamal.lc.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierRevenueServiceTest {

    @Mock
    private SupplierRepository supplierRepo;

    @Mock
    private ShiftRepository shiftRepo;

    @Mock
    private SupplierAdjustmentRepository adjustmentRepo;

    @InjectMocks
    private SupplierRevenueService service;

    private Supplier supplier;
    private UUID supplierId;

    @BeforeEach
    void setUp() {
        supplierId = UUID.randomUUID();
        supplier = Supplier.builder()
                .id(supplierId)
                .name("Ninja")
                .code("NINJA")
                .color("#4CAF50")
                .isActive(true)
                .build();
    }

    @Test
    void getRevenueSummary_calculatesCorrectTotals() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);

        Shift shift1 = Shift.builder()
                .id(UUID.randomUUID())
                .supplier(supplier)
                .totalDelivered(20)
                .workingHours(new BigDecimal("8.00"))
                .supplierRevenue(new BigDecimal("200.00"))
                .supplierExtraRevenue(new BigDecimal("50.00"))
                .excluded(false)
                .build();

        SupplierAdjustment addition = SupplierAdjustment.builder()
                .id(UUID.randomUUID())
                .supplier(supplier)
                .type("ADDITION")
                .category("تعويض شيفتات")
                .amount(new BigDecimal("100.00"))
                .build();

        SupplierAdjustment deduction = SupplierAdjustment.builder()
                .id(UUID.randomUUID())
                .supplier(supplier)
                .type("DEDUCTION")
                .category("خطأ في حساب الطلبات")
                .amount(new BigDecimal("30.00"))
                .build();

        when(supplierRepo.findAll()).thenReturn(List.of(supplier));
        when(shiftRepo.findByShiftDateBetween(from, to)).thenReturn(List.of(shift1));
        when(adjustmentRepo.findByAdjustmentDateBetweenOrderByAdjustmentDateDesc(from, to))
                .thenReturn(List.of(addition, deduction));

        SupplierRevenueSummaryResponse response = service.getRevenueSummary(from, to);

        assertThat(response).isNotNull();
        assertThat(response.getTotalSuppliers()).isEqualTo(1);
        assertThat(response.getTotalShifts()).isEqualTo(1);
        assertThat(response.getTotalOrders()).isEqualTo(20);
        assertThat(response.getGrossRevenue()).isEqualByComparingTo(new BigDecimal("250.00"));
        assertThat(response.getNetAdjustments()).isEqualByComparingTo(new BigDecimal("70.00")); // +100 -30
        assertThat(response.getNetRevenue()).isEqualByComparingTo(new BigDecimal("320.00")); // 250 + 70
    }
}
