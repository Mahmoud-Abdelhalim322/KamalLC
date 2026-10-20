package com.kamal.lc.captainapp.entity;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.entity.CaptainDocument;
import com.kamal.lc.common.security.TenantAware;
import com.kamal.lc.common.security.TenantEntityListener;
import com.kamal.lc.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A renewed document sent from the captain app. The photos are stored as ordinary
 * {@link CaptainDocument}s straight away; the new number and expiry only reach the captain's
 * file once a supervisor approves the request.
 */
@Entity
@Table(name = "captain_document_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class CaptainDocumentRequest implements TenantAware {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "captain_id", nullable = false)
    private Captain captain;

    /** A {@link com.kamal.lc.captain.expiry.DocumentKind} name. */
    @Column(name = "document_kind", nullable = false, length = 30)
    private String documentKind;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "new_expiry", nullable = false)
    private LocalDate newExpiry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "front_document_id")
    private CaptainDocument frontDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "back_document_id")
    private CaptainDocument backDocument;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = PENDING;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean isPending() {
        return PENDING.equals(status);
    }
}
