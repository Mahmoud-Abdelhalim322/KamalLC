package com.kamal.lc.imports.service;

import com.kamal.lc.imports.dto.ExcelCaptainRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Parses captain Excel files using Apache POI.
 * Flexibly matches column headers (Arabic/English, with/without spaces).
 */
@Slf4j
@Service
public class ExcelParserService {

    /** Date layouts seen in the sheets, tried in order against text cells. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"));

    /** Arabic labels of the date columns, used in the "unreadable date" warning. */
    private static final Map<String, String> FIELD_LABELS_AR;
    static {
        Map<String, String> labels = new HashMap<>();
        labels.put("dateOfBirth", "تاريخ الميلاد");
        labels.put("iqamaExpiry", "انتهاء الإقامة");
        labels.put("iqamaExpiryRenewed", "تاريخ تجديد الإقامة");
        labels.put("idExpiryDate", "انتهاء الهوية");
        labels.put("passportIssueDate", "تاريخ إصدار الجواز");
        labels.put("passportExpiry", "انتهاء الجواز");
        labels.put("licenceExpiry", "انتهاء الرخصة");
        labels.put("workPermitExpiry", "انتهاء رخصة العمل");
        labels.put("insuranceIssueDate", "تاريخ إصدار التأمين");
        labels.put("insuranceExpiry", "انتهاء التأمين");
        labels.put("qiwaContractStart", "بداية عقد قوى");
        labels.put("qiwaContractEnd", "نهاية عقد قوى");
        labels.put("joinDate", "تاريخ الالتحاق");
        labels.put("leaveDate", "تاريخ المغادرة");
        FIELD_LABELS_AR = Collections.unmodifiableMap(labels);
    }

    // Column name → field mapping (normalized for case/spelling-insensitive matching)
    private static final Map<String, String> COLUMN_MAP;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        // kamalCode
        m.put("id-kslsc", "kamalCode");
        m.put("id - kslsc", "kamalCode");
        m.put("id-ksc", "kamalCode");
        m.put("id - ksc", "kamalCode");
        m.put("kslsc", "kamalCode");
        m.put("ksc", "kamalCode");
        m.put("كود كمال", "kamalCode");
        m.put("الرقم الوظيفي", "kamalCode");
        m.put("كود الموظف", "kamalCode");
        m.put("رقم الموظف", "kamalCode");
        m.put("كود الكابتن", "kamalCode");
        m.put("رقم الكابتن", "kamalCode");
        m.put("job id", "kamalCode");
        m.put("employee id", "kamalCode");
        m.put("emp id", "kamalCode");
        m.put("الرقم الوظيفي / job id", "kamalCode");
        m.put("الرقم الوظيفي job id", "kamalCode");
        m.put("الرقم الوظيفي\njob id", "kamalCode");

        // ninjaId / supplierId
        m.put("id - ninja", "ninjaId");
        m.put("id-ninja", "ninjaId");
        m.put("ninja", "ninjaId");
        m.put("معرف نينجا", "ninjaId");
        m.put("معرف المورد", "ninjaId");
        m.put("id - supplier", "ninjaId");
        m.put("id-supplier", "ninjaId");
        m.put("supplier id", "ninjaId");
        m.put("supplier_id", "ninjaId");

        // captainName (Arabic)
        m.put("اسم الكابتن", "captainName");
        m.put("الاسم", "captainName");
        m.put("اسم", "captainName");
        m.put("captain name", "captainName");
        m.put("الاسم بالعربي", "captainName");

        // fullNameEn (English)
        m.put("name", "fullNameEn");
        m.put("english name", "fullNameEn");
        m.put("الاسم بالإنجليزي", "fullNameEn");
        m.put("الاسم بالانجليزي", "fullNameEn");
        m.put("الاسم بالانجليزية", "fullNameEn");
        m.put("full name en", "fullNameEn");

        // gender
        m.put("الجنس", "gender");
        m.put("gender", "gender");
        m.put("الجنس / gender", "gender");
        m.put("الجنس gender", "gender");
        m.put("الجنس\ngender", "gender");

        // nationality
        m.put("الجنسية", "nationality");
        m.put("nationality", "nationality");
        m.put("الجنسية / nationality", "nationality");
        m.put("الجنسية nationality", "nationality");
        m.put("الجنسية\nnationality", "nationality");

        // dateOfBirth
        m.put("تاريخ الميلاد", "dateOfBirth");
        m.put("الميلاد", "dateOfBirth");
        m.put("date of birth", "dateOfBirth");
        m.put("dob", "dateOfBirth");
        m.put("تاريخ الميلاد / date of birth", "dateOfBirth");
        m.put("تاريخ الميلاد date of birth", "dateOfBirth");
        m.put("تاريخ الميلاد\ndate of birth", "dateOfBirth");

        // phone
        m.put("رقم الهاتف", "phone");
        m.put("الهاتف", "phone");
        m.put("الجوال", "phone");
        m.put("رقم الجوال", "phone");
        m.put("جوال", "phone");
        m.put("phone", "phone");
        m.put("phone no", "phone");
        m.put("mobile", "phone");
        m.put("جوال / phone no", "phone");
        m.put("جوال phone no", "phone");
        m.put("جوال\nphone no", "phone");

        // placeOfResidence (City / مكان الإقامة)
        m.put("مكان الإقامة", "placeOfResidence");
        m.put("مكان الاقامة", "placeOfResidence");
        m.put("مدينة", "placeOfResidence");
        m.put("المدينة", "placeOfResidence");
        m.put("city", "placeOfResidence");
        m.put("مدينة / city", "placeOfResidence");
        m.put("مدينة city", "placeOfResidence");
        m.put("مدينة\ncity", "placeOfResidence");

        // homeAddress
        m.put("العنوان", "homeAddress");
        m.put("عنوان السكن", "homeAddress");
        m.put("home address", "homeAddress");
        m.put("address", "homeAddress");
        m.put("عنوان السكن / home address", "homeAddress");
        m.put("عنوان السكن home address", "homeAddress");
        m.put("عنوان السكن\nhome address", "homeAddress");

        // email
        m.put("البريد الالكتروني", "email");
        m.put("البريد الإلكتروني", "email");
        m.put("البريد", "email");
        m.put("الايميل", "email");
        m.put("email", "email");
        m.put("e-mail", "email");
        m.put("البريد الالكتروني / e-mail", "email");
        m.put("البريد الالكتروني / email", "email");
        m.put("البريد الالكتروني e-mail", "email");
        m.put("البريد الالكتروني email", "email");
        m.put("البريد الالكتروني\ne-mail", "email");

        // passportNumber
        m.put("رقم الجواز", "passportNumber");
        m.put("الجواز", "passportNumber");
        m.put("جواز السفر", "passportNumber");
        m.put("passport", "passportNumber");
        m.put("passport no", "passportNumber");
        m.put("passport number", "passportNumber");
        m.put("رقم الجواز / passport no", "passportNumber");
        m.put("رقم الجواز passport no", "passportNumber");
        m.put("رقم الجواز\npassport no", "passportNumber");

        // passportIssueDate
        m.put("تاريخ إصدار الجواز", "passportIssueDate");
        m.put("تاريخ اصدار الجواز", "passportIssueDate");
        m.put("تاريخ الاصدار", "passportIssueDate");
        m.put("تاريخ الإصدار", "passportIssueDate");
        m.put("passport issue date", "passportIssueDate");
        m.put("تاريخ الاصدار / issue date", "passportIssueDate");
        m.put("تاريخ الاصدار issue date", "passportIssueDate");
        m.put("تاريخ الاصدار\nissue date", "passportIssueDate");

        // passportExpiry
        m.put("انتهاء الجواز", "passportExpiry");
        m.put("تاريخ انتهاء الجواز", "passportExpiry");
        m.put("تاريخ الانتهاء", "passportExpiry");
        m.put("تاريخ الانتهاء / expiry date", "passportExpiry");
        m.put("تاريخ الانتهاء expiry date", "passportExpiry");
        m.put("تاريخ الانتهاء\nexpiry date", "passportExpiry");
        m.put("passport expiry", "passportExpiry");
        m.put("passport expiry date", "passportExpiry");

        // passportStatus
        m.put("حالة الجواز", "passportStatus");
        m.put("passport status", "passportStatus");
        m.put("حالة الجواز / passport status", "passportStatus");
        m.put("حالة الجواز passport status", "passportStatus");
        m.put("حالة الجواز\npassport status", "passportStatus");

        // iqamaNumber
        m.put("رقم الاقامة", "iqamaNumber");
        m.put("الاقامة", "iqamaNumber");
        m.put("iqama", "iqamaNumber");
        m.put("iqama no", "iqamaNumber");
        m.put("iqama number", "iqamaNumber");
        m.put("رقم الهوية", "iqamaNumber");
        m.put("الهوية", "iqamaNumber");
        m.put("رقم الهوية / الاقامة", "iqamaNumber");
        m.put("identity / residence no", "iqamaNumber");
        m.put("identity residence no", "iqamaNumber");
        m.put("رقم الهوية / الاقامة / identity / residence no", "iqamaNumber");
        m.put("رقم الهوية / الاقامة identity / residence no", "iqamaNumber");
        m.put("رقم الهوية / الاقامة\nidentity / residence no", "iqamaNumber");

        // iqamaOccupation
        m.put("المهنة بالإقامة", "iqamaOccupation");
        m.put("المهنة بالاقامة", "iqamaOccupation");
        m.put("iqama occupation", "iqamaOccupation");
        m.put("iqama occu", "iqamaOccupation");
        m.put("المهنة بالاقامة / iqama occu", "iqamaOccupation");
        m.put("المهنة بالاقامة iqama occu", "iqamaOccupation");
        m.put("المهنة بالاقامة\niqama occu", "iqamaOccupation");

        // idExpiryDate (NEW)
        m.put("تاريخ انتهاء الهوية", "idExpiryDate");
        m.put("انتهاء الهوية", "idExpiryDate");
        m.put("id expiry date", "idExpiryDate");
        m.put("id expiry", "idExpiryDate");
        m.put("تاريخ انتهاء الهوية / id expiry date", "idExpiryDate");
        m.put("تاريخ انتهاء الهوية id expiry date", "idExpiryDate");
        m.put("تاريخ انتهاء الهوية\nid expiry date", "idExpiryDate");

        // iqamaExpiry
        m.put("انتهاء الاقامة", "iqamaExpiry");
        m.put("تاريخ انتهاء الاقامة", "iqamaExpiry");
        m.put("iqama expiry", "iqamaExpiry");
        m.put("expiry iqama", "iqamaExpiry");
        m.put("تاريخ انتهاء الاقامة / expiry iqama", "iqamaExpiry");
        m.put("تاريخ انتهاء الاقامة expiry iqama", "iqamaExpiry");
        m.put("تاريخ انتهاء الاقامة\nexpiry iqama", "iqamaExpiry");

        // iqamaExpiryRenewed
        m.put("تاريخ تجديد الإقامة", "iqamaExpiryRenewed");
        m.put("تاريخ تجديد الاقامة", "iqamaExpiryRenewed");
        m.put("تجديد الاقامة", "iqamaExpiryRenewed");
        m.put("renew iqama", "iqamaExpiryRenewed");
        m.put("iqama expiry renewed", "iqamaExpiryRenewed");
        m.put("تاريخ تجديد الاقامة / renew iqama", "iqamaExpiryRenewed");
        m.put("تاريخ تجديد الاقامة renew iqama", "iqamaExpiryRenewed");
        m.put("تاريخ تجديد الاقامة\nrenew iqama", "iqamaExpiryRenewed");

        // borderNumber (NEW)
        m.put("رقم الحدود", "borderNumber");
        m.put("الحدود", "borderNumber");
        m.put("border no", "borderNumber");
        m.put("border number", "borderNumber");
        m.put("border id", "borderNumber");
        m.put("رقم الحدود / border no", "borderNumber");
        m.put("رقم الحدود border no", "borderNumber");
        m.put("رقم الحدود\nborder no", "borderNumber");

        // iqamaStatus
        m.put("حالة الإقامة", "iqamaStatus");
        m.put("حالة الاقامة", "iqamaStatus");
        m.put("residence status", "iqamaStatus");
        m.put("iqama status", "iqamaStatus");
        m.put("حالة الاقامة / residence status", "iqamaStatus");
        m.put("حالة الاقامة residence status", "iqamaStatus");
        m.put("حالة الاقامة\nresidence status", "iqamaStatus");

        // establishmentNumber
        m.put("رقم المنشأة", "establishmentNumber");
        m.put("رقم المنشاة", "establishmentNumber");
        m.put("المنشأة", "establishmentNumber");
        m.put("establishment number", "establishmentNumber");
        m.put("establish no", "establishmentNumber");
        m.put("رقم المنشأة / establish no", "establishmentNumber");
        m.put("رقم المنشاة / establish no", "establishmentNumber");
        m.put("رقم المنشأة establish no", "establishmentNumber");
        m.put("رقم المنشأة\nestablish no", "establishmentNumber");

        // qiwaContractNumber
        m.put("رقم عقد قوى", "qiwaContractNumber");
        m.put("عقد قوى", "qiwaContractNumber");
        m.put("qiwa contract number", "qiwaContractNumber");
        m.put("qiwa cont no", "qiwaContractNumber");
        m.put("رقم عقد قوى / qiwa cont no", "qiwaContractNumber");
        m.put("رقم عقد قوى qiwa cont no", "qiwaContractNumber");
        m.put("رقم عقد قوى\nqiwa cont no", "qiwaContractNumber");

        // qiwaContractStart
        m.put("بداية عقد قوى", "qiwaContractStart");
        m.put("تاريخ بداية عقد قوى", "qiwaContractStart");
        m.put("qiwa contract start", "qiwaContractStart");
        m.put("qiwa start date", "qiwaContractStart");
        m.put("تاريخ بداية عقد قوى / qiwa start date", "qiwaContractStart");
        m.put("تاريخ بداية عقد قوى qiwa start date", "qiwaContractStart");
        m.put("تاريخ بداية عقد قوى\nqiwa start date", "qiwaContractStart");

        // qiwaContractEnd
        m.put("نهاية عقد قوى", "qiwaContractEnd");
        m.put("تاريخ انتهاء عقد قوى", "qiwaContractEnd");
        m.put("qiwa contract end", "qiwaContractEnd");
        m.put("qiwa expiry date", "qiwaContractEnd");
        m.put("تاريخ انتهاء عقد قوى / qiwa expiry date", "qiwaContractEnd");
        m.put("تاريخ انتهاء عقد قوى qiwa expiry date", "qiwaContractEnd");
        m.put("تاريخ انتهاء عقد قوى\nqiwa expiry date", "qiwaContractEnd");

        // qiwaContractStatus
        m.put("حالة عقد قوى", "qiwaContractStatus");
        m.put("qiwa contract status", "qiwaContractStatus");
        m.put("qiwa status", "qiwaContractStatus");
        m.put("حالة عقد قوى / qiwa status", "qiwaContractStatus");
        m.put("حالة عقد قوى qiwa status", "qiwaContractStatus");
        m.put("حالة عقد قوى\nqiwa status", "qiwaContractStatus");

        // workPermitExpiry
        m.put("تاريخ انتهاء رخصة العمل", "workPermitExpiry");
        m.put("انتهاء رخصة العمل", "workPermitExpiry");
        m.put("رخصة العمل", "workPermitExpiry");
        m.put("work permit expiry", "workPermitExpiry");
        m.put("work expire date", "workPermitExpiry");
        m.put("work permit expiry date", "workPermitExpiry");
        m.put("تاريخ انتهاء رخصة العمل / work expire date", "workPermitExpiry");
        m.put("تاريخ انتهاء رخصة العمل work expire date", "workPermitExpiry");
        m.put("تاريخ انتهاء رخصة العمل\nwork expire date", "workPermitExpiry");

        // workPermitStatus
        m.put("حالة رخصة العمل", "workPermitStatus");
        m.put("work permit status", "workPermitStatus");
        m.put("حالة رخصة العمل / work permit status", "workPermitStatus");
        m.put("حالة رخصة العمل work permit status", "workPermitStatus");
        m.put("حالة رخصة العمل\nwork permit status", "workPermitStatus");

        // insuranceCompany
        m.put("اسم شركة التأمين الطبي", "insuranceCompany");
        m.put("اسم شركة التأمين", "insuranceCompany");
        m.put("شركة التأمين", "insuranceCompany");
        m.put("insurance company", "insuranceCompany");
        m.put("insur company name", "insuranceCompany");
        m.put("اسم شركة التأمين / insur company name", "insuranceCompany");
        m.put("اسم شركة التأمين insur company name", "insuranceCompany");
        m.put("اسم شركة التأمين\ninsur company name", "insuranceCompany");

        // insuranceNumber
        m.put("رقم التأمين", "insuranceNumber");
        m.put("رقم وثيقة التأمين", "insuranceNumber");
        m.put("وثيقة التأمين", "insuranceNumber");
        m.put("insurance no", "insuranceNumber");
        m.put("insurance number", "insuranceNumber");
        m.put("insur polcy no", "insuranceNumber");
        m.put("insur policy no", "insuranceNumber");
        m.put("رقم وثيقة التأمين / insur polcy no", "insuranceNumber");
        m.put("رقم وثيقة التأمين insur polcy no", "insuranceNumber");
        m.put("رقم وثيقة التأمين\ninsur polcy no", "insuranceNumber");

        // insuranceCategory
        m.put("فئة التأمين", "insuranceCategory");
        m.put("insurance category", "insuranceCategory");
        m.put("insur categ", "insuranceCategory");
        m.put("فئة التأمين / insur categ", "insuranceCategory");
        m.put("فئة التأمين insur categ", "insuranceCategory");
        m.put("فئة التأمين\ninsur categ", "insuranceCategory");

        // insuranceIssueDate
        m.put("تاريخ إصدار التأمين", "insuranceIssueDate");
        m.put("تاريخ اصدار التأمين", "insuranceIssueDate");
        m.put("insurance issue date", "insuranceIssueDate");
        m.put("تاريخ إصدار التأمين / issue date", "insuranceIssueDate");
        m.put("تاريخ إصدار التأمين issue date", "insuranceIssueDate");
        m.put("تاريخ إصدار التأمين\nissue date", "insuranceIssueDate");

        // insuranceExpiry
        m.put("انتهاء التأمين", "insuranceExpiry");
        m.put("تاريخ انتهاء التأمين", "insuranceExpiry");
        m.put("انتهاء التأمين الطبي", "insuranceExpiry");
        m.put("insurance expiry", "insuranceExpiry");
        m.put("تاريخ انتهاء التأمين / expiry date", "insuranceExpiry");
        m.put("تاريخ انتهاء التأمين expiry date", "insuranceExpiry");
        m.put("تاريخ انتهاء التأمين\nexpiry date", "insuranceExpiry");

        // insuranceStatus
        m.put("حالة التأمين", "insuranceStatus");
        m.put("insurance status", "insuranceStatus");
        m.put("insur status", "insuranceStatus");
        m.put("حالة التأمين / insur status", "insuranceStatus");
        m.put("حالة التأمين insur status", "insuranceStatus");
        m.put("حالة التأمين\ninsur status", "insuranceStatus");

        // crNumber
        m.put("رقم السجل التجاري", "crNumber");
        m.put("السجل التجاري", "crNumber");
        m.put("cr number", "crNumber");
        m.put("cr no", "crNumber");
        m.put("رقم السجل التجاري / cr no", "crNumber");
        m.put("رقم السجل التجاري cr no", "crNumber");
        m.put("رقم السجل التجاري\ncr no", "crNumber");

        // affiliatedCompany
        m.put("الشركة المنتسب إليها", "affiliatedCompany");
        m.put("الشركة المنتسب اليها", "affiliatedCompany");
        m.put("الشركة التابع لها", "affiliatedCompany");
        m.put("اسم الشركة", "affiliatedCompany");
        m.put("affiliated company", "affiliatedCompany");
        m.put("الشركة التابع لها / affiliated company", "affiliatedCompany");
        m.put("الشركة التابع لها affiliated company", "affiliatedCompany");
        m.put("الشركة التابع لها\naffiliated company", "affiliatedCompany");

        // drivingLicenceNo
        m.put("رقم الرخصة", "drivingLicenceNo");
        m.put("الرخصة", "drivingLicenceNo");
        m.put("رخصة القيادة", "drivingLicenceNo");
        m.put("licence", "drivingLicenceNo");
        m.put("license", "drivingLicenceNo");
        m.put("license id", "drivingLicenceNo");
        m.put("driving licence no", "drivingLicenceNo");
        m.put("رقم الرخصة / license id", "drivingLicenceNo");
        m.put("رقم الرخصة license id", "drivingLicenceNo");
        m.put("رقم الرخصة\nlicense id", "drivingLicenceNo");

        // licenceType (NEW)
        m.put("نوع الرخصة", "licenceType");
        m.put("نوع الرخصه", "licenceType");
        m.put("license type", "licenceType");
        m.put("licence type", "licenceType");
        m.put("نوع الرخصة / license type", "licenceType");
        m.put("نوع الرخصة license type", "licenceType");
        m.put("نوع الرخصة\nlicense type", "licenceType");

        // licenceExpiry
        m.put("انتهاء الرخصة", "licenceExpiry");
        m.put("تاريخ انتهاء الرخصة", "licenceExpiry");
        m.put("تاريخ انتهاء الرخصه", "licenceExpiry");
        m.put("licence expiry", "licenceExpiry");
        m.put("license expiry", "licenceExpiry");
        m.put("تاريخ انتهاء الرخصة / license expiry", "licenceExpiry");
        m.put("تاريخ انتهاء الرخصة license expiry", "licenceExpiry");
        m.put("تاريخ انتهاء الرخصة\nlicense expiry", "licenceExpiry");

        // Licence flags
        m.put("لديه رخصة", "hasLicence");
        m.put("حالة الرخصة", "licenceStatus");

        // bloodType
        m.put("فصيلة الدم", "bloodType");
        m.put("blood type", "bloodType");
        m.put("فصيلة الدم / blood type", "bloodType");
        m.put("فصيلة الدم blood type", "bloodType");
        m.put("فصيلة الدم\nblood type", "bloodType");

        // trialPeriodDays
        m.put("فترة التجربة (يوم)", "trialPeriodDays");
        m.put("فترة التجربة", "trialPeriodDays");

        // bankName
        m.put("اسم البنك", "bankName");
        m.put("bank name", "bankName");
        m.put("اسم البنك / bank name", "bankName");
        m.put("اسم البنك bank name", "bankName");
        m.put("اسم البنك\nbank name", "bankName");

        // bankAccountNumber
        m.put("رقم الحساب البنكي", "bankAccountNumber");
        m.put("حساب بنكي", "bankAccountNumber");
        m.put("الايبان", "bankAccountNumber");
        m.put("iban", "bankAccountNumber");
        m.put("bank account number", "bankAccountNumber");
        m.put("bank iban", "bankAccountNumber");
        m.put("رقم الحساب البنكي / bank iban", "bankAccountNumber");
        m.put("رقم الحساب البنكي bank iban", "bankAccountNumber");
        m.put("رقم الحساب البنكي\nbank iban", "bankAccountNumber");

        // department / section
        m.put("القسم", "department");
        m.put("department", "department");
        m.put("section", "department");
        m.put("القسم / section", "department");
        m.put("القسم section", "department");
        m.put("القسم\nsection", "department");
        m.put("الشعبة", "section");

        // jobTitle
        m.put("المسمى الوظيفي", "jobTitle");
        m.put("الوظيفة", "jobTitle");
        m.put("job title", "jobTitle");
        m.put("المسمى الوظيفي / job title", "jobTitle");
        m.put("المسمى الوظيفي job title", "jobTitle");
        m.put("المسمى الوظيفي\njob title", "jobTitle");

        // appId / ID-APP & supplier identifier aliases
        m.put("id-app", "appId");
        m.put("id - app", "appId");
        m.put("id app", "appId");
        m.put("id_app", "appId");
        m.put("app-id", "appId");
        m.put("app id", "appId");
        m.put("appid", "appId");
        m.put("معرف التطبيق", "appId");
        m.put("كود التطبيق", "appId");
        m.put("معرف", "appId");
        m.put("المعرف", "appId");
        m.put("معرف الكابتن", "appId");
        m.put("معرف السائق", "appId");
        m.put("كود المعرف", "appId");
        m.put("رقم المعرف", "appId");
        m.put("معرف الكابتن لدى التطبيق", "appId");
        m.put("معرف الكابتن في التطبيق", "appId");
        m.put("معرف الكابتن بالتطبيق", "appId");
        m.put("معرف الكابتن لدى المورد", "appId");
        m.put("معرف الكابتن في المورد", "appId");
        m.put("معرف الكابتن بالمورد", "appId");
        m.put("كود الكابتن لدى التطبيق", "appId");
        m.put("كود الكابتن بالتطبيق", "appId");
        m.put("كود الكابتن في التطبيق", "appId");
        m.put("كود الكابتن لدى المورد", "appId");
        m.put("كود المورد", "ninjaId");
        m.put("كود نينجا", "ninjaId");
        m.put("external id", "appId");
        m.put("external_id", "appId");
        m.put("external-id", "appId");
        m.put("id external", "appId");
        m.put("معرف خارجي", "appId");
        m.put("المعرف الخارجي", "appId");
        m.put("application id", "appId");
        m.put("معرف التطبيق (id-app)", "appId");
        m.put("معرف التطبيق (id app)", "appId");
        m.put("معرف المورد (id-app)", "appId");
        m.put("معرف المورد (id app)", "appId");
        m.put("معرف التطبيق / id-app", "appId");
        m.put("معرف التطبيق id-app", "appId");
        m.put("معرف التطبيق/id-app", "appId");
        m.put("معرف التطبيق\nid-app", "appId");
        m.put("id-app / معرف التطبيق", "appId");
        m.put("id-app معرف التطبيق", "appId");
        m.put("id-app/معرف التطبيق", "appId");
        m.put("id-app\nمعرف التطبيق", "appId");
        m.put("معرف التطبيق / app id", "appId");
        m.put("معرف التطبيق app id", "appId");
        m.put("معرف التطبيق\napp id", "appId");
        m.put("app id / معرف التطبيق", "appId");
        m.put("app id معرف التطبيق", "appId");
        m.put("app id\nمعرف التطبيق", "appId");

        // joinDate (Work start date / تاريخ المباشرة)
        m.put("تاريخ المباشرة", "joinDate");
        m.put("تاريخ الالتحاق", "joinDate");
        m.put("work start date", "joinDate");
        m.put("join date", "joinDate");
        m.put("تاريخ المباشرة / work start date", "joinDate");
        m.put("تاريخ المباشرة work start date", "joinDate");
        m.put("تاريخ المباشرة\nwork start date", "joinDate");

        // basicSalary
        m.put("الراتب الأساسي", "basicSalary");
        m.put("الراتب الاساسي", "basicSalary");
        m.put("basic salary", "basicSalary");
        m.put("basic salry", "basicSalary");
        m.put("الراتب الأساسي / basic salry", "basicSalary");
        m.put("الراتب الأساسي basic salry", "basicSalary");
        m.put("الراتب الأساسي\nbasic salry", "basicSalary");

        // housingAllowance
        m.put("بدل السكن", "housingAllowance");
        m.put("housing allowance", "housingAllowance");
        m.put("housing allow", "housingAllowance");
        m.put("بدل السكن / housing allow", "housingAllowance");
        m.put("بدل السكن housing allow", "housingAllowance");
        m.put("بدل السكن\nhousing allow", "housingAllowance");

        // transportationAllowance
        m.put("بدل المواصلات", "transportationAllowance");
        m.put("بدل مواصلات", "transportationAllowance");
        m.put("transportation allowance", "transportationAllowance");
        m.put("trans allow", "transportationAllowance");
        m.put("بدل مواصلات / trans allow", "transportationAllowance");
        m.put("بدل مواصلات trans allow", "transportationAllowance");
        m.put("بدل مواصلات\ntrans allow", "transportationAllowance");

        // other allowances
        m.put("بدل البنزين", "fuelAllowanceAmount");
        m.put("بدل الاتصالات والإنترنت", "callInternetAllowance");
        m.put("بدل الاتصالات والانترنت", "callInternetAllowance");
        m.put("بدل المكافأة", "rewardAllowance");
        m.put("بدل المكافاة", "rewardAllowance");
        m.put("يستخدم سيارته الخاصة", "usesOwnVehicle");
        m.put("مستحق بدل بنزين", "fuelAllowanceEligible");

        // salaryDeductions (NEW)
        m.put("خصومات", "salaryDeductions");
        m.put("الخصومات", "salaryDeductions");
        m.put("خصم", "salaryDeductions");
        m.put("deduction", "salaryDeductions");
        m.put("deductions", "salaryDeductions");
        m.put("salary deductions", "salaryDeductions");
        m.put("خصومات / deduction", "salaryDeductions");
        m.put("خصومات deduction", "salaryDeductions");
        m.put("خصومات\ndeduction", "salaryDeductions");

        // netSalary (NEW)
        m.put("صافي الراتب", "netSalary");
        m.put("الصافي", "netSalary");
        m.put("net salary", "netSalary");
        m.put("net salry", "netSalary");
        m.put("صافي الراتب / net salry", "netSalary");
        m.put("صافي الراتب net salry", "netSalary");
        m.put("صافي الراتب\nnet salry", "netSalary");

        // Work Info & Status
        m.put("هاتف العمل", "workPhone");
        m.put("المشرف", "supervisor");
        m.put("موقع العمل", "workLocation");
        m.put("يعمل مع", "workWith");
        m.put("الحالة", "status");
        m.put("تاريخ المغادرة", "leaveDate");
        m.put("الملاحظات", "notes");
        m.put("ملاحظات", "notes");

        // Keys are stored normalized so lookups only ever normalize the sheet header.
        Map<String, String> normalized = new LinkedHashMap<>();
        m.forEach((key, field) -> normalized.put(normalizeHeader(key), field));
        COLUMN_MAP = Collections.unmodifiableMap(normalized);
    }

    /** Normalised header → field, exposed so {@link ImportFileDetector} can score a sheet. */
    static Map<String, String> columnMap() {
        return COLUMN_MAP;
    }

    /**
     * Parse an Excel file and return the list of captain rows.
     *
     * @param file the uploaded Excel file
     * @return list of parsed rows (may contain rows with null fields for optional columns)
     * @throws ExcelParseException if the file is unreadable or missing required columns
     */
    public List<ExcelCaptainRow> parse(MultipartFile file) {
        // WorkbookFactory handles both .xlsx (XSSF) and legacy .xls (HSSF)
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new ExcelParseException("الملف لا يحتوي على أي أوراق عمل");
            }

            // 1. Find header row and map columns
            Map<String, Integer> fieldToCol = detectHeaders(sheet);

            // 2. Validate required columns — only Job ID (kamalCode) is required; all others are optional
            if (!fieldToCol.containsKey("kamalCode")) {
                throw new ExcelParseException("عمود 'الرقم الوظيفي' أو 'ID-KSLSC' غير موجود في الملف");
            }
            if (!fieldToCol.containsKey("captainName") && fieldToCol.containsKey("fullNameEn")) {
                fieldToCol.put("captainName", fieldToCol.get("fullNameEn"));
            }

            // 3. Parse data rows
            int headerRowIdx = findHeaderRowIndex(sheet);
            List<ExcelCaptainRow> rows = new ArrayList<>();

            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) continue;

                List<String> warnings = new ArrayList<>();

                String kamalCode = text(row, fieldToCol, "kamalCode");
                String ninjaId = text(row, fieldToCol, "ninjaId");
                String captainName = text(row, fieldToCol, "captainName");
                String appId = text(row, fieldToCol, "appId");

                // Unify external supplier/app identifier so ID-APP or ninjaId are both recognized
                String effectiveId = (appId != null && !appId.trim().isEmpty())
                        ? appId.trim()
                        : (ninjaId != null && !ninjaId.trim().isEmpty() ? ninjaId.trim() : null);

                if (effectiveId != null) {
                    if (effectiveId.endsWith(".0")) {
                        effectiveId = effectiveId.substring(0, effectiveId.length() - 2);
                    }
                    appId = effectiveId;
                    ninjaId = effectiveId;
                }

                rows.add(ExcelCaptainRow.builder()
                        .rowNumber(i + 1) // 1-based for user display
                        .kamalCode(kamalCode != null ? kamalCode.trim().toUpperCase() : null)
                        .ninjaId(ninjaId)
                        .captainName(captainName)
                        .fullNameEn(text(row, fieldToCol, "fullNameEn"))
                        .gender(text(row, fieldToCol, "gender"))
                        .phone(text(row, fieldToCol, "phone"))
                        .nationality(text(row, fieldToCol, "nationality"))
                        .dateOfBirth(date(row, fieldToCol, "dateOfBirth", warnings))
                        .email(text(row, fieldToCol, "email"))
                        .placeOfResidence(text(row, fieldToCol, "placeOfResidence"))
                        .homeAddress(text(row, fieldToCol, "homeAddress"))
                        .jobTitle(text(row, fieldToCol, "jobTitle"))
                        .appId(appId)
                        // Passport
                        .passportNumber(text(row, fieldToCol, "passportNumber"))
                        .passportIssueDate(date(row, fieldToCol, "passportIssueDate", warnings))
                        .passportExpiry(date(row, fieldToCol, "passportExpiry", warnings))
                        .passportStatus(text(row, fieldToCol, "passportStatus"))
                        // Iqama
                        .iqamaNumber(text(row, fieldToCol, "iqamaNumber"))
                        .iqamaOccupation(text(row, fieldToCol, "iqamaOccupation"))
                        .idExpiryDate(date(row, fieldToCol, "idExpiryDate", warnings))
                        .iqamaExpiry(date(row, fieldToCol, "iqamaExpiry", warnings))
                        .iqamaExpiryRenewed(date(row, fieldToCol, "iqamaExpiryRenewed", warnings))
                        .borderNumber(text(row, fieldToCol, "borderNumber"))
                        .iqamaStatus(text(row, fieldToCol, "iqamaStatus"))
                        // Qiwa
                        .qiwaContractNumber(text(row, fieldToCol, "qiwaContractNumber"))
                        .establishmentNumber(text(row, fieldToCol, "establishmentNumber"))
                        .qiwaContractStart(date(row, fieldToCol, "qiwaContractStart", warnings))
                        .qiwaContractEnd(date(row, fieldToCol, "qiwaContractEnd", warnings))
                        .qiwaContractStatus(text(row, fieldToCol, "qiwaContractStatus"))
                        // Work Permit
                        .workPermitExpiry(date(row, fieldToCol, "workPermitExpiry", warnings))
                        .workPermitStatus(text(row, fieldToCol, "workPermitStatus"))
                        // Company
                        .affiliatedCompany(text(row, fieldToCol, "affiliatedCompany"))
                        .crNumber(text(row, fieldToCol, "crNumber"))
                        // Driving Licence
                        .drivingLicenceNo(text(row, fieldToCol, "drivingLicenceNo"))
                        .licenceType(text(row, fieldToCol, "licenceType"))
                        .licenceExpiry(date(row, fieldToCol, "licenceExpiry", warnings))
                        .hasLicence(parseBool(text(row, fieldToCol, "hasLicence")))
                        .licenceStatus(text(row, fieldToCol, "licenceStatus"))
                        // Insurance
                        .insuranceNumber(text(row, fieldToCol, "insuranceNumber"))
                        .insuranceCategory(text(row, fieldToCol, "insuranceCategory"))
                        .insuranceIssueDate(date(row, fieldToCol, "insuranceIssueDate", warnings))
                        .insuranceCompany(text(row, fieldToCol, "insuranceCompany"))
                        .insuranceExpiry(date(row, fieldToCol, "insuranceExpiry", warnings))
                        .insuranceStatus(text(row, fieldToCol, "insuranceStatus"))
                        // Personal
                        .bloodType(text(row, fieldToCol, "bloodType"))
                        .trialPeriodDays(parseInt(text(row, fieldToCol, "trialPeriodDays")))
                        // Bank
                        .bankAccountNumber(text(row, fieldToCol, "bankAccountNumber"))
                        .bankName(text(row, fieldToCol, "bankName"))
                        // Salary
                        .basicSalary(parseMoney(text(row, fieldToCol, "basicSalary")))
                        .housingAllowance(parseMoney(text(row, fieldToCol, "housingAllowance")))
                        .transportationAllowance(parseMoney(text(row, fieldToCol, "transportationAllowance")))
                        .fuelAllowanceAmount(parseMoney(text(row, fieldToCol, "fuelAllowanceAmount")))
                        .callInternetAllowance(parseMoney(text(row, fieldToCol, "callInternetAllowance")))
                        .rewardAllowance(parseMoney(text(row, fieldToCol, "rewardAllowance")))
                        .salaryDeductions(parseMoney(text(row, fieldToCol, "salaryDeductions")))
                        .netSalary(parseMoney(text(row, fieldToCol, "netSalary")))
                        // Vehicle
                        .usesOwnVehicle(parseBool(text(row, fieldToCol, "usesOwnVehicle")))
                        .fuelAllowanceEligible(parseBool(text(row, fieldToCol, "fuelAllowanceEligible")))
                        // Work Info
                        .workPhone(text(row, fieldToCol, "workPhone"))
                        .department(text(row, fieldToCol, "department"))
                        .section(text(row, fieldToCol, "section"))
                        .supervisor(text(row, fieldToCol, "supervisor"))
                        .workLocation(text(row, fieldToCol, "workLocation"))
                        .workWith(text(row, fieldToCol, "workWith"))
                        // Status
                        .status(text(row, fieldToCol, "status"))
                        .joinDate(date(row, fieldToCol, "joinDate", warnings))
                        .leaveDate(date(row, fieldToCol, "leaveDate", warnings))
                        .notes(text(row, fieldToCol, "notes"))
                        .warnings(warnings)
                        .build());
            }

            log.info("Parsed {} data rows from '{}'", rows.size(), file.getOriginalFilename());
            return rows;

        } catch (ExcelParseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Excel file: {}", e.getMessage(), e);
            throw new ExcelParseException("فشل في قراءة الملف: " + e.getMessage());
        }
    }

    private int findHeaderRowIndex(Sheet sheet) {
        return ExcelCellSupport.findHeaderRowIndex(sheet, COLUMN_MAP);
    }

    private Map<String, Integer> detectHeaders(Sheet sheet) {
        Map<String, Integer> fieldToCol = ExcelCellSupport.detectHeaders(sheet, COLUMN_MAP);
        if (fieldToCol.isEmpty()) {
            // No header matched at all — say what was looked for, not just that it failed.
            throw new ExcelParseException(
                    "لم يتم التعرف على أي عمود في الملف — يجب أن يحتوي صف العناوين على "
                            + "'ID-KSLSC' و 'اسم الكابتن'");
        }
        return fieldToCol;
    }

    private static String normalizeHeader(String raw) {
        return ExcelCellSupport.normalizeHeader(raw);
    }

    private String text(Row row, Map<String, Integer> fieldToCol, String field) {
        return ExcelCellSupport.text(row, fieldToCol, field);
    }

    /** Reads a date column, adding an Arabic warning (and returning null) when it cannot be read. */
    private LocalDate date(Row row, Map<String, Integer> fieldToCol, String field, List<String> warnings) {
        Integer col = fieldToCol.get(field);
        if (col == null) return null;

        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        // Real Excel dates are numeric cells with a date format applied.
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        String raw = getCellString(row, col);
        if (raw == null || raw.trim().isEmpty()) return null;

        LocalDate parsed = parseDate(raw.trim());
        if (parsed == null) {
            warnings.add("تعذّرت قراءة " + FIELD_LABELS_AR.getOrDefault(field, field) + " (" + raw.trim() + ")");
        }
        return parsed;
    }

    private LocalDate parseDate(String raw) {
        String value = toWesternDigits(raw).replaceAll("\\s+", "");
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try the next layout
            }
        }
        return null;
    }

    private static String toWesternDigits(String value) {
        return ExcelCellSupport.toWesternDigits(value);
    }

    private String getCellString(Row row, int colIdx) {
        return ExcelCellSupport.getCellString(row, colIdx);
    }

    private boolean isEmptyRow(Row row) {
        return ExcelCellSupport.isEmptyRow(row);
    }

    /** Parses Arabic yes/no or "نعم"/"لا" into boolean. */
    private boolean parseBool(String value) {
        if (value == null || value.isBlank()) return false;
        String v = value.trim().toLowerCase();
        return v.equals("نعم") || v.equals("yes") || v.equals("true") || v.equals("1");
    }

    /** Parses an integer from text, returns null if unparseable. */
    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim().replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses a BigDecimal from text, returns null if unparseable. */
    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value.trim().replaceAll("[^0-9.,-]", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Thrown when the Excel file cannot be parsed. */
    public static class ExcelParseException extends RuntimeException {
        public ExcelParseException(String message) {
            super(message);
        }
    }
}
