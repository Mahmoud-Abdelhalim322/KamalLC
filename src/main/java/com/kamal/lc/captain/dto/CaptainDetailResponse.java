package com.kamal.lc.captain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainDetailResponse {
    private UUID id;
    private String kamalCode;
    private String captainType;
    private String fullNameAr;
    private String fullNameEn;
    private String nationality;
    private String dateOfBirth;
    private String phone;
    private String email;
    private String iqamaNumber;
    private String iqamaExpiry;
    private String passportNumber;
    private String passportExpiry;
    private String drivingLicenceNo;
    private String licenceExpiry;
    private String workPermitExpiry;
    private String insuranceNumber;
    private String insuranceCompany;
    private String insuranceExpiry;
    private boolean usesOwnVehicle;
    private boolean fuelAllowanceEligible;
    private String status;
    private String joinDate;
    private String leaveDate;
    private String notes;

    /** Every tracked document with its verdict — what the الوثائق الرسمية card renders. */
    private List<DocumentStatusInfo> documentStatuses;

    /** The worst of the above, for the warning banner. */
    private String worstDocumentStatus;

    private List<SupplierIdInfo> supplierIds;
    private List<DocumentInfo> documents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierIdInfo {
        private UUID slotId;
        private String externalId;
        private String supplierName;
        private String supplierCode;
        private String validFrom;
        private String validTo;
    }

    /** One tracked document as the detail screen shows it: what it is, and where it stands. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentStatusInfo {
        private String kind;
        private String labelAr;
        private String number;
        private String expiry;
        private String status;
        private String statusLabelAr;
        /** Negative once the date has passed; null when no date is recorded. */
        private Long daysLeft;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentInfo {
        private UUID id;
        private String documentType;
        private String fileName;
        private String contentType;
        private Long fileSize;
        private String uploadedAt;
        private String note;
    }
}
