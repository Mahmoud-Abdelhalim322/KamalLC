package com.kamal.lc.imports.entity;

import com.kamal.lc.common.security.TenantAware;
import com.kamal.lc.common.security.TenantEntityListener;
import com.kamal.lc.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "import_row_issue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ImportRowIssue implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @Column(name = "row_number")
    private Integer rowNumber;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(name = "issue_code", nullable = false, length = 50)
    private String issueCode;

    @Column(name = "message_ar", length = 500)
    private String messageAr;

    /**
     * The column is JSONB. {@code columnDefinition} alone only shapes generated DDL — it does
     * not change how the value is bound, so Hibernate sent it as VARCHAR and PostgreSQL
     * rejected the insert ("column raw_row is of type jsonb but expression is of type
     * character varying"), even for a null. That aborted the whole import transaction.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_row")
    private String rawRow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
