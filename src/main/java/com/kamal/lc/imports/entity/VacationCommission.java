package com.kamal.lc.imports.entity;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.common.security.TenantAware;
import com.kamal.lc.common.security.TenantEntityListener;
import com.kamal.lc.supplier.entity.Supplier;
import com.kamal.lc.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Imported holiday/vacation bonus commission record sent by a supplier.
 * Records the raw shift day identifier (Days ksa start), supplier, captain, amount,
 * and optional zone/flag/notes.
 */
@Entity
@Table(name = "vacation_commission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class VacationCommission implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Raw days code from Excel sheet, e.g., "46203" (Days ksa start). */
    @Column(name = "days_code", length = 50)
    private String daysCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "supplier_name", length = 255)
    private String supplierName;

    @Column(name = "external_captain_id", nullable = false, length = 50)
    private String externalCaptainId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "captain_id")
    private Captain captain;

    @Column(name = "zone_name", length = 255)
    private String zoneName;

    @Column(name = "flag", length = 100)
    private String flag;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "vacation_date", nullable = false)
    private LocalDate vacationDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportBatch importBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
