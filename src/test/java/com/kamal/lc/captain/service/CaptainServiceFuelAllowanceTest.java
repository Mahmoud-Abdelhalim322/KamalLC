package com.kamal.lc.captain.service;

import com.kamal.lc.captain.dto.CaptainRequest;
import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.expiry.DocumentAlertEvaluator;
import com.kamal.lc.captain.expiry.DocumentAlertPolicy;
import com.kamal.lc.captain.repository.CaptainDocumentRepository;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.settings.repository.AppSettingRepository;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the write path for {@code fuelAllowanceEligible}.
 *
 * <p>The flag decides whether {@link com.kamal.lc.shift.service.ShiftCalculator} pays the daily
 * fuel allowance, which payroll then sums. It was previously readable but never written, so a
 * captain marked as driving his own vehicle silently lost the allowance.
 */
@DisplayName("CaptainService — fuel allowance eligibility")
class CaptainServiceFuelAllowanceTest {

    private CaptainRepository captainRepo;
    private CaptainService service;

    @BeforeEach
    void setUp() {
        captainRepo = mock(CaptainRepository.class);
        CaptainDocumentRepository documentRepo = mock(CaptainDocumentRepository.class);
        SupplierIdAssignmentRepository assignmentRepo = mock(SupplierIdAssignmentRepository.class);
        CaptainCodeGenerator codeGenerator = mock(CaptainCodeGenerator.class);

        when(codeGenerator.generate(any())).thenReturn("KSCFL0001");
        when(documentRepo.findByCaptainId(any())).thenReturn(List.of());
        when(assignmentRepo.findByCaptainIdOrderByValidFromDesc(any())).thenReturn(List.of());

        // A real evaluator over an empty settings table: every document falls back to its
        // default window. Mocking the policy would hand the evaluator a null window map.
        DocumentAlertEvaluator documentAlerts = new DocumentAlertEvaluator(
                new DocumentAlertPolicy(mock(AppSettingRepository.class)));

        service = new CaptainService(captainRepo, documentRepo, assignmentRepo, codeGenerator,
                documentAlerts);
    }

    private Captain savedCaptain;

    private CaptainRequest request(boolean usesOwnVehicle, Boolean fuelEligible) {
        return CaptainRequest.builder()
                .captainType("FREELANCE")
                .fullNameAr("كابتن تجريبي")
                .usesOwnVehicle(usesOwnVehicle)
                .fuelAllowanceEligible(fuelEligible)
                .status("ACTIVE")
                .build();
    }

    /** Runs create() against the mocked repositories and returns the entity that was saved. */
    private Captain create(CaptainRequest request) {
        when(captainRepo.save(any(Captain.class))).thenAnswer(invocation -> {
            Captain saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            savedCaptain = saved;
            when(captainRepo.findById(saved.getId())).thenReturn(Optional.of(saved));
            return saved;
        });

        service.create(request);
        return savedCaptain;
    }

    @Test
    @DisplayName("own vehicle makes the captain eligible")
    void ownVehicleGrantsEligibility() {
        assertThat(create(request(true, null)).isFuelAllowanceEligible()).isTrue();
    }

    @Test
    @DisplayName("no own vehicle leaves the captain ineligible")
    void noOwnVehicleLeavesIneligible() {
        assertThat(create(request(false, null)).isFuelAllowanceEligible()).isFalse();
    }

    @Test
    @DisplayName("an explicit flag overrides the vehicle default in both directions")
    void explicitFlagWins() {
        assertThat(create(request(true, false)).isFuelAllowanceEligible()).isFalse();
        assertThat(create(request(false, true)).isFuelAllowanceEligible()).isTrue();
    }
}
