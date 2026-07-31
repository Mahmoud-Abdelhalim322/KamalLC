package com.kamal.lc.imports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single parsed row from the captain import Excel file.
 * Mirrors the sheet layout: ID-KSLSC, ID - NINJA, اسم الكابتن, الهاتف, الجنسية,
 * تاريخ الميلاد, البريد الالكتروني, رقم الإقامة, انتهاء الإقامة, رقم الجواز,
 * انتهاء الجواز, رقم الرخصة, انتهاء الرخصة, انتهاء رخصة العمل, رقم التأمين,
 * شركة التأمين, انتهاء التأمين.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelCaptainRow {
    private int rowNumber;
    private String kamalCode;
    private String ninjaId;
    private String captainName;
    private String phone;
    private String nationality;
    private LocalDate dateOfBirth;
    private String email;
    private String iqamaNumber;
    private LocalDate iqamaExpiry;
    private String passportNumber;
    private LocalDate passportExpiry;
    private String drivingLicenceNo;
    private LocalDate licenceExpiry;
    private LocalDate workPermitExpiry;
    private String insuranceNumber;
    private String insuranceCompany;
    private LocalDate insuranceExpiry;

    /** Cell-level problems found while parsing (unreadable dates, etc.) — reported as warnings. */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
