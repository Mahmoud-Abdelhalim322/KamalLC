package com.kamal.lc.captainapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request and response shapes of the captain mobile app API ({@code /api/captain-app}).
 *
 * <p>Amounts are raw numbers and statuses are codes; the app does its own Arabic/English
 * wording. Supplier revenue and margins are never exposed here.
 */
public final class CaptainAppDtos {

    private CaptainAppDtos() {}

    // ---- Sign-in ----

    public record OtpRequest(@NotBlank @Size(max = 20) String phone) {}

    public record OtpRequestResponse(
            String maskedPhone,
            int codeLength,
            long expiresInSeconds,
            long resendInSeconds,
            /** Only when {@code app.captain-app.otp.dev-echo} is on — never in production. */
            String devCode) {}

    public record OtpVerifyRequest(
            @NotBlank @Size(max = 20) String phone,
            @NotBlank @Size(max = 10) String code) {}

    public record CaptainSession(String token, long expiresIn, Profile captain) {}

    // ---- Profile ----

    public record Profile(
            UUID id,
            String kamalCode,
            String captainType,
            String fullNameAr,
            String fullNameEn,
            String phone,
            String email,
            String appId,
            List<String> supplierIds,
            String supervisor,
            String workLocation,
            String bankName,
            String bankAccountLast4,
            LocalDate joinDate,
            String status,
            String tenantName,
            String tenantLogo) {}

    public record ProfileStats(
            /** Whole years since the join date, one decimal. */
            BigDecimal yearsOfService,
            /** Attended share of the last 90 days' shifts, 0–100; null with no shifts. */
            Integer reliabilityPercent,
            long ordersLastMonth) {}

    public record MeResponse(Profile profile, ProfileStats stats, List<CustodyItem> custody) {}

    public record CustodyItem(
            UUID id,
            String assetType,
            String assetLabel,
            OffsetDateTime assignedAt,
            String notes) {}

    // ---- Shifts ----

    /**
     * How a shift reads to the captain: UPCOMING, LIVE, AWAITING (ended, attendance not yet
     * imported), PRESENT, LATE, ABSENT or EXCLUDED.
     */
    public record ShiftItem(
            UUID id,
            LocalDate date,
            String state,
            String supplierName,
            String city,
            String zoneName,
            String shiftName,
            OffsetDateTime scheduledStart,
            OffsetDateTime scheduledEnd,
            OffsetDateTime actualCheckin,
            BigDecimal delayMinutes,
            BigDecimal workingHours,
            Integer totalDelivered,
            BigDecimal netShiftPay,
            String exclusionReason) {}

    public record ShiftDetail(
            ShiftItem shift,
            Integer extraOrders,
            BigDecimal shiftTypeHours,
            /** Hours. */
            BigDecimal busyTime,
            BigDecimal freeTime,
            BigDecimal offlineTime,
            BigDecimal awayFromZone,
            /** Fractions, 0–1. */
            BigDecimal busyUtilization,
            BigDecimal deliveryOntime,
            BigDecimal hourlyUtr,
            BigDecimal dailyWage,
            BigDecimal dailyIncentive,
            BigDecimal extraOrdersPay,
            BigDecimal fuelAllowance,
            BigDecimal carAllowance,
            BigDecimal lateDeduction,
            /** Zero when the supplier penalty was waived. */
            BigDecimal supplierDeduction,
            String supplierDeductionReason,
            Boolean qualifiesFullRate,
            String calcNote) {}

    public record ShiftWeek(LocalDate from, LocalDate to, Performance totals, List<ShiftItem> shifts) {}

    public record Performance(
            LocalDate from,
            LocalDate to,
            long attendedShifts,
            long absentShifts,
            long totalOrders,
            BigDecimal totalHours,
            BigDecimal busyUtilization,
            BigDecimal deliveryOntime,
            BigDecimal ordersPerHour) {}

    // ---- Earnings & payslips ----

    public record AdjustmentItem(
            UUID id,
            /** BONUS or DEDUCTION. */
            String kind,
            String type,
            String labelAr,
            BigDecimal amount,
            String reason) {}

    public record PayBreakdown(
            int attendedShifts,
            int absentShifts,
            int totalOrders,
            BigDecimal totalHours,
            BigDecimal basePay,
            BigDecimal dailyIncentives,
            BigDecimal vacationCommission,
            BigDecimal extraOrderPay,
            BigDecimal targetBonus,
            BigDecimal fuelAllowances,
            BigDecimal carAllowances,
            BigDecimal lateDeductions,
            BigDecimal supplierDeductions,
            BigDecimal adjustmentsTotal,
            BigDecimal gross,
            BigDecimal net,
            List<AdjustmentItem> adjustments) {}

    /**
     * A pay period as the captain sees it. {@code status} is ESTIMATE while no payroll run
     * covers it, otherwise the run's DRAFT / APPROVED / PAID.
     */
    public record EarningsPeriod(
            LocalDate from,
            LocalDate to,
            LocalDate asOf,
            String status,
            UUID payslipId,
            int monthlyOrderTarget,
            PayBreakdown pay) {}

    public record PayslipSummary(
            UUID id,
            String title,
            LocalDate periodFrom,
            LocalDate periodTo,
            String status,
            OffsetDateTime paidAt,
            int attendedShifts,
            int totalOrders,
            BigDecimal net) {}

    public record LoanInstallment(UUID loanId, BigDecimal amount, Integer number, Integer count) {}

    public record PayslipDetail(
            PayslipSummary summary,
            PayBreakdown pay,
            List<LoanInstallment> loanInstallments,
            String bankName,
            String bankAccountLast4) {}

    // ---- Loans ----

    public record LoanPaymentItem(
            UUID id,
            BigDecimal amount,
            /** PAYROLL_DEDUCTION or MANUAL. */
            String source,
            String payrollTitle,
            LocalDate payrollPeriodFrom,
            OffsetDateTime createdAt,
            String notes) {}

    public record LoanItem(
            UUID id,
            String status,
            BigDecimal originalAmount,
            BigDecimal remaining,
            BigDecimal repaid,
            int repaidPercent,
            BigDecimal monthlyDeduction,
            Integer installmentsTotal,
            Integer installmentsPaid,
            Integer installmentsLeft,
            /** Estimated month of the last deduction; null when no monthly deduction is set. */
            YearMonth expectedPayoff,
            OffsetDateTime createdAt,
            String createdByName,
            String notes,
            List<LoanPaymentItem> payments) {}

    // ---- Documents ----

    public record DocumentRequestItem(
            UUID id,
            String kind,
            String documentNumber,
            LocalDate newExpiry,
            String status,
            String reviewNote,
            OffsetDateTime createdAt,
            OffsetDateTime reviewedAt,
            UUID frontDocumentId,
            UUID backDocumentId) {}

    public record DocumentItem(
            /** A DocumentKind name. */
            String kind,
            String labelAr,
            String numberLast4,
            LocalDate expiry,
            /** VALID, EXPIRING, EXPIRED or MISSING. */
            String status,
            Long daysLeft,
            DocumentRequestItem pendingRequest) {}

    public record DocumentsResponse(
            String worstStatus,
            long alertCount,
            List<DocumentItem> documents,
            List<DocumentRequestItem> requests) {}

    // ---- Home ----

    public record EarningsSnapshot(
            LocalDate from,
            LocalDate to,
            LocalDate asOf,
            String status,
            BigDecimal net,
            int attendedShifts,
            int totalOrders) {}

    public record LoanSnapshot(
            BigDecimal originalAmount,
            BigDecimal remaining,
            BigDecimal repaid,
            BigDecimal monthlyDeduction) {}

    public record HomeResponse(
            String fullNameAr,
            String fullNameEn,
            String kamalCode,
            /** The shift running now, else the next one today, else today's latest. */
            ShiftItem todayShift,
            EarningsSnapshot earnings,
            /** The most urgent document alert, if any. */
            DocumentItem documentAlert,
            Performance week,
            /** Summed over active loans; null without one. */
            LoanSnapshot loan,
            List<ShiftItem> recentShifts,
            int notificationCount) {}

    // ---- Notifications ----

    /**
     * {@code type}: DOCUMENT, SALARY_PAID, SHIFT_UPCOMING, DOCUMENT_REVIEWED.
     * {@code data} carries the values the app words the message with.
     */
    public record NotificationItem(
            String id,
            String type,
            /** HIGH, MEDIUM or LOW. */
            String severity,
            OffsetDateTime at,
            Map<String, Object> data) {}

    // ---- Devices ----

    public record DeviceRequest(
            @NotBlank @Size(max = 500) String token,
            @NotBlank @Size(max = 20) String platform,
            @Size(max = 10) String locale,
            @Size(max = 30) String appVersion) {}

    // ---- Supervisor review (web admin) ----

    public record DocumentRequestReview(
            UUID id,
            UUID captainId,
            String kamalCode,
            String captainName,
            String kind,
            String labelAr,
            String currentNumber,
            LocalDate currentExpiry,
            String documentNumber,
            LocalDate newExpiry,
            String status,
            UUID frontDocumentId,
            UUID backDocumentId,
            OffsetDateTime createdAt,
            String reviewedBy,
            OffsetDateTime reviewedAt,
            String reviewNote) {}

    public record ReviewDecision(@Size(max = 1000) String note) {}
}
