package com.kamal.lc.imports.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.imports.dto.ExcelCaptainRow;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.repository.StaffRepository;
import com.kamal.lc.supplier.entity.Supplier;
import com.kamal.lc.supplier.entity.SupplierIdAssignment;
import com.kamal.lc.supplier.entity.SupplierIdSlot;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import com.kamal.lc.supplier.repository.SupplierIdSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImportRowWriterTest {

    private CaptainRepository captainRepo;
    private StaffRepository staffRepo;
    private SupplierIdSlotRepository slotRepo;
    private SupplierIdAssignmentRepository assignmentRepo;
    private ImportRowWriter writer;

    @BeforeEach
    void setUp() {
        captainRepo = mock(CaptainRepository.class);
        staffRepo = mock(StaffRepository.class);
        slotRepo = mock(SupplierIdSlotRepository.class);
        assignmentRepo = mock(SupplierIdAssignmentRepository.class);
        writer = new ImportRowWriter(captainRepo, staffRepo, slotRepo, assignmentRepo);
    }

    @Test
    @DisplayName("KSC creates EMPLOYEE Captain and defaults name to code when name is absent")
    void routesKscToEmployeeCaptain() {
        when(captainRepo.findByKamalCode("KSC1001")).thenReturn(Optional.empty());
        when(captainRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExcelCaptainRow row = ExcelCaptainRow.builder()
                .rowNumber(1)
                .kamalCode("KSC1001")
                .appId("APP-101")
                .build();

        writer.write(row, null);

        ArgumentCaptor<Captain> captor = ArgumentCaptor.forClass(Captain.class);
        verify(captainRepo).save(captor.capture());
        Captain saved = captor.getValue();
        assertThat(saved.getKamalCode()).isEqualTo("KSC1001");
        assertThat(saved.getCaptainType()).isEqualTo("EMPLOYEE");
        assertThat(saved.getFullNameAr()).isEqualTo("KSC1001");
        assertThat(saved.getAppId()).isEqualTo("APP-101");
        verifyNoInteractions(staffRepo);
    }

    @Test
    @DisplayName("KSCFL creates FREELANCE Captain")
    void routesKscflToFreelanceCaptain() {
        when(captainRepo.findByKamalCode("KSCFL2001")).thenReturn(Optional.empty());
        when(captainRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExcelCaptainRow row = ExcelCaptainRow.builder()
                .rowNumber(2)
                .kamalCode("KSCFL2001")
                .captainName("فهد الحربي")
                .build();

        writer.write(row, null);

        ArgumentCaptor<Captain> captor = ArgumentCaptor.forClass(Captain.class);
        verify(captainRepo).save(captor.capture());
        Captain saved = captor.getValue();
        assertThat(saved.getKamalCode()).isEqualTo("KSCFL2001");
        assertThat(saved.getCaptainType()).isEqualTo("FREELANCE");
        assertThat(saved.getFullNameAr()).isEqualTo("فهد الحربي");
        verifyNoInteractions(staffRepo);
    }

    @Test
    @DisplayName("KSE creates WORKER Staff")
    void routesKseToWorkerStaff() {
        when(staffRepo.findByStaffCode("KSE3001")).thenReturn(Optional.empty());
        when(staffRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExcelCaptainRow row = ExcelCaptainRow.builder()
                .rowNumber(3)
                .kamalCode("KSE3001")
                .captainName("أحمد عبد الله")
                .build();

        writer.write(row, null);

        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepo).save(captor.capture());
        Staff saved = captor.getValue();
        assertThat(saved.getStaffCode()).isEqualTo("KSE3001");
        assertThat(saved.getStaffType()).isEqualTo("WORKER");
        assertThat(saved.getFullNameAr()).isEqualTo("أحمد عبد الله");
        verifyNoInteractions(captainRepo);
    }

    @Test
    @DisplayName("KSM creates MANAGER Staff")
    void routesKsmToManagerStaff() {
        when(staffRepo.findByStaffCode("KSM4001")).thenReturn(Optional.empty());
        when(staffRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExcelCaptainRow row = ExcelCaptainRow.builder()
                .rowNumber(4)
                .kamalCode("KSM4001")
                .build();

        writer.write(row, null);

        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepo).save(captor.capture());
        Staff saved = captor.getValue();
        assertThat(saved.getStaffCode()).isEqualTo("KSM4001");
        assertThat(saved.getStaffType()).isEqualTo("MANAGER");
        assertThat(saved.getFullNameAr()).isEqualTo("KSM4001");
        verifyNoInteractions(captainRepo);
    }

    @Test
    @DisplayName("KSC with appId creates Captain, SupplierIdSlot, and SupplierIdAssignment for supplier")
    void routesKscWithAppIdAndSavesSupplierSlot() {
        Supplier supplier = Supplier.builder()
                .id(UUID.randomUUID())
                .code("NINJA")
                .name("نينجا")
                .build();

        when(captainRepo.findByKamalCode("KSC1001")).thenReturn(Optional.empty());
        when(captainRepo.save(any())).thenAnswer(inv -> {
            Captain c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        when(slotRepo.findByExternalIdAndSupplierId("1001", supplier.getId())).thenReturn(Optional.empty());
        when(slotRepo.save(any())).thenAnswer(inv -> {
            SupplierIdSlot s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(assignmentRepo.existsBySupplierIdSlotIdAndCaptainIdAndValidToIsNull(any(), any())).thenReturn(false);
        when(assignmentRepo.findBySupplierIdSlotIdAndValidToIsNull(any())).thenReturn(Optional.empty());

        ExcelCaptainRow row = ExcelCaptainRow.builder()
                .rowNumber(1)
                .kamalCode("KSC1001")
                .captainName("محمد علي")
                .appId("1001")
                .build();

        writer.write(row, supplier);

        ArgumentCaptor<SupplierIdSlot> slotCaptor = ArgumentCaptor.forClass(SupplierIdSlot.class);
        verify(slotRepo).save(slotCaptor.capture());
        SupplierIdSlot savedSlot = slotCaptor.getValue();
        assertThat(savedSlot.getExternalId()).isEqualTo("1001");
        assertThat(savedSlot.getSupplier()).isEqualTo(supplier);
        assertThat(savedSlot.isActive()).isTrue();

        ArgumentCaptor<SupplierIdAssignment> assignCaptor = ArgumentCaptor.forClass(SupplierIdAssignment.class);
        verify(assignmentRepo).save(assignCaptor.capture());
        SupplierIdAssignment savedAssign = assignCaptor.getValue();
        assertThat(savedAssign.getSupplierIdSlot()).isEqualTo(savedSlot);
        assertThat(savedAssign.getSupplierSideName()).isEqualTo("محمد علي");
        assertThat(savedAssign.getValidFrom()).isNotNull();
    }
}
