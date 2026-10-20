package com.kamal.lc.captainapp.service;

import com.kamal.lc.auth.entity.AppUser;
import com.kamal.lc.auth.repository.UserRepository;
import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.expiry.DocumentAlert;
import com.kamal.lc.captain.expiry.DocumentAlertEvaluator;
import com.kamal.lc.captain.expiry.DocumentKind;
import com.kamal.lc.captain.expiry.DocumentStatus;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.captainapp.dto.CaptainAppDtos.*;
import com.kamal.lc.captainapp.entity.CaptainDevice;
import com.kamal.lc.captainapp.entity.CaptainDocumentRequest;
import com.kamal.lc.captainapp.repository.CaptainDeviceRepository;
import com.kamal.lc.captainapp.repository.CaptainDocumentRequestRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.custody.entity.CustodyRecord;
import com.kamal.lc.custody.repository.CustodyRepository;
import com.kamal.lc.imports.repository.VacationCommissionRepository;
import com.kamal.lc.loan.entity.Loan;
import com.kamal.lc.loan.entity.LoanPayment;
import com.kamal.lc.loan.repository.LoanPaymentRepository;
import com.kamal.lc.loan.repository.LoanRepository;
import com.kamal.lc.payroll.entity.CaptainAdjustment;
import com.kamal.lc.payroll.entity.PayrollLine;
import com.kamal.lc.payroll.entity.PayrollRun;
import com.kamal.lc.payroll.repository.CaptainAdjustmentRepository;
import com.kamal.lc.payroll.repository.PayrollLineRepository;
import com.kamal.lc.payroll.service.PayrollCalculator;
import com.kamal.lc.settings.service.EffectiveSettings;
import com.kamal.lc.settings.service.SettingsResolver;
import com.kamal.lc.shift.entity.Shift;
import com.kamal.lc.shift.repository.ShiftRepository;
import com.kamal.lc.shift.repository.ShiftRepository.CaptainPeriodTotals;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import com.kamal.lc.tenant.entity.Tenant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Everything the captain app reads, always scoped to the signed-in captain.
 *
 * <p>Pay figures reuse the payroll rules: a period covered by an approved or paid run shows
 * that run's line verbatim, anything else is an estimate computed the way the run will
 * compute it. Supplier revenue and margins never leave this class.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaptainAppService {

    /** Shift times and "today" are judged on Riyadh time, whatever the server runs on. */
    static final ZoneId ZONE = ZoneId.of("Asia/Riyadh");

    private static final int MAX_RANGE_DAYS = 62;
    private static final int RECENT_SHIFTS = 5;
    private static final int NOTIFICATION_DAYS = 30;
    private static final List<String> SLIP_STATUSES = List.of(PayrollRun.APPROVED, PayrollRun.PAID);

    private final CaptainRepository captainRepo;
    private final ShiftRepository shiftRepo;
    private final PayrollLineRepository lineRepo;
    private final CaptainAdjustmentRepository adjustmentRepo;
    private final VacationCommissionRepository vacationCommissionRepo;
    private final PayrollCalculator calculator;
    private final SettingsResolver settingsResolver;
    private final LoanRepository loanRepo;
    private final LoanPaymentRepository loanPaymentRepo;
    private final CustodyRepository custodyRepo;
    private final SupplierIdAssignmentRepository supplierIdRepo;
    private final DocumentAlertEvaluator documentEvaluator;
    private final CaptainDocumentRequestRepository requestRepo;
    private final CaptainDeviceRepository deviceRepo;
    private final UserRepository userRepo;

    // ---- Profile ----

    public Profile profileOf(Captain c) {
        List<String> supplierIds = supplierIdRepo.findByCaptainIdOrderByValidFromDesc(c.getId()).stream()
                .filter(a -> a.getValidTo() == null)
                .map(a -> a.getSupplierIdSlot().getExternalId())
                .toList();
        Tenant tenant = c.getTenant();
        return new Profile(c.getId(), c.getKamalCode(), c.getCaptainType(), c.getFullNameAr(),
                c.getFullNameEn(), c.getPhone(), c.getEmail(), c.getAppId(), supplierIds,
                c.getSupervisor(), c.getWorkLocation(), c.getBankName(), last4(c.getBankAccountNumber()),
                c.getJoinDate(), c.getStatus(),
                tenant != null ? tenant.getName() : null, tenant != null ? tenant.getLogo() : null);
    }

    public MeResponse me(UUID captainId) {
        Captain c = captain(captainId);
        LocalDate today = today();

        BigDecimal years = c.getJoinDate() == null ? null
                : BigDecimal.valueOf(ChronoUnit.DAYS.between(c.getJoinDate(), today))
                        .divide(BigDecimal.valueOf(365.25), 1, RoundingMode.HALF_UP);

        Performance quarter = performance(captainId, today.minusDays(89), today);
        long judged = quarter.attendedShifts() + quarter.absentShifts();
        Integer reliability = judged == 0 ? null
                : (int) Math.round(100.0 * quarter.attendedShifts() / judged);

        YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
        long ordersLastMonth = totals(captainId, lastMonth.atDay(1), lastMonth.atEndOfMonth())
                .map(CaptainPeriodTotals::getTotalOrders).orElse(0L);

        return new MeResponse(profileOf(c), new ProfileStats(years, reliability, ordersLastMonth),
                custody(captainId));
    }

    public List<CustodyItem> custody(UUID captainId) {
        return custodyRepo.findByCaptainIdAndStatus(captainId, "ACTIVE").stream()
                .sorted(Comparator.comparing(CustodyRecord::getAssignedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(r -> new CustodyItem(r.getId(), r.getAssetType(), r.getAssetLabel(),
                        r.getAssignedAt(), r.getNotes()))
                .toList();
    }

    // ---- Home ----

    public HomeResponse home(UUID captainId) {
        Captain c = captain(captainId);
        LocalDate today = today();
        OffsetDateTime now = OffsetDateTime.now(ZONE);

        List<Shift> recent = shiftRepo.findByCaptainIdAndShiftDateBetweenOrderByShiftDateAsc(
                captainId, today.minusDays(14), today);
        ShiftItem todayShift = todayShift(recent.stream()
                .filter(s -> today.equals(s.getShiftDate())).toList(), now);
        List<ShiftItem> recentShifts = recent.stream()
                .filter(s -> s.getShiftDate().isBefore(today))
                .sorted(Comparator.comparing(Shift::getShiftDate).reversed())
                .limit(RECENT_SHIFTS)
                .map(s -> shiftItem(s, now))
                .toList();

        EarningsPeriod month = earnings(captainId, YearMonth.from(today));
        EarningsSnapshot earnings = new EarningsSnapshot(month.from(), month.to(), month.asOf(),
                month.status(), month.pay().net(), month.pay().attendedShifts(), month.pay().totalOrders());

        DocumentItem documentAlert = documentItems(c, today, List.of()).stream()
                .filter(d -> DocumentStatus.valueOf(d.status()).isAlert())
                .min(Comparator.comparing((DocumentItem d) -> !DocumentStatus.EXPIRED.name().equals(d.status()))
                        .thenComparing(DocumentItem::daysLeft, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        LocalDate weekStart = weekStart(today);
        Performance week = performance(captainId, weekStart, weekStart.plusDays(6));

        List<Loan> active = loanRepo.findByCaptainIdAndStatus(captainId, Loan.ACTIVE);
        LoanSnapshot loan = active.isEmpty() ? null : new LoanSnapshot(
                sum(active, Loan::getOriginalAmount),
                sum(active, Loan::getRemaining),
                sum(active, Loan::getOriginalAmount).subtract(sum(active, Loan::getRemaining)),
                sum(active, Loan::getMonthlyDeduction));

        return new HomeResponse(c.getFullNameAr(), c.getFullNameEn(), c.getKamalCode(), todayShift,
                earnings, documentAlert, week, loan, recentShifts, notifications(captainId).size());
    }

    /** The shift running now, else the next one today, else today's latest. */
    private ShiftItem todayShift(List<Shift> today, OffsetDateTime now) {
        if (today.isEmpty()) {
            return null;
        }
        List<ShiftItem> items = today.stream()
                .sorted(Comparator.comparing(Shift::getScheduledStart, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> shiftItem(s, now))
                .toList();
        return items.stream().filter(i -> "LIVE".equals(i.state())).findFirst()
                .or(() -> items.stream().filter(i -> "UPCOMING".equals(i.state())).findFirst())
                .orElse(items.get(items.size() - 1));
    }

    // ---- Shifts ----

    /** Shifts between two dates; without dates, the current Saturday-to-Friday week. */
    public ShiftWeek shifts(UUID captainId, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            from = weekStart(today());
            to = from.plusDays(6);
        } else if (from == null) {
            from = to.minusDays(6);
        } else if (to == null) {
            to = from.plusDays(6);
        }
        requireRange(from, to);
        OffsetDateTime now = OffsetDateTime.now(ZONE);
        List<ShiftItem> items = shiftRepo.findByCaptainIdAndShiftDateBetweenOrderByShiftDateAsc(captainId, from, to)
                .stream()
                .sorted(Comparator.comparing(Shift::getShiftDate)
                        .thenComparing(Shift::getScheduledStart, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> shiftItem(s, now))
                .toList();
        return new ShiftWeek(from, to, performance(captainId, from, to), items);
    }

    public ShiftDetail shiftDetail(UUID captainId, UUID shiftId) {
        Shift s = shiftRepo.findById(shiftId)
                .filter(x -> x.getCaptain() != null && captainId.equals(x.getCaptain().getId()))
                .orElseThrow(() -> new BusinessException("SHIFT_NOT_FOUND", "الوردية غير موجودة"));
        BigDecimal supplierDeduction = s.isSupplierDeductionExcluded() ? BigDecimal.ZERO : s.getSupplierDeduction();
        return new ShiftDetail(shiftItem(s, OffsetDateTime.now(ZONE)), s.getExtraOrders(), s.getShiftTypeHours(),
                s.getBusyTime(), s.getFreeTime(), s.getOfflineTime(), s.getAwayFromZone(),
                s.getBusyUtilization(), s.getDeliveryOntime(), s.getHourlyUtr(),
                s.getDailyWage(), s.getDailyIncentive(), null, s.getFuelAllowance(), s.getCarAllowance(),
                s.getLateDeduction(), supplierDeduction,
                s.isSupplierDeductionExcluded() ? s.getSupplierDeductionExclusionReason() : s.getSupplierDeductionReason(),
                s.getQualifiesFullRate(), s.getCalcNote());
    }

    /**
     * Totals over the captain's shifts. Unlike the payroll aggregate, a shift with no
     * attendance yet (upcoming, running, awaiting import) is not counted as an absence.
     */
    public Performance performance(UUID captainId, LocalDate from, LocalDate to) {
        List<Shift> counted = shiftRepo.findByCaptainIdAndShiftDateBetweenOrderByShiftDateAsc(captainId, from, to)
                .stream().filter(s -> !s.isExcluded()).toList();
        long attended = counted.stream().filter(s -> Boolean.TRUE.equals(s.getAttended())).count();
        long absent = counted.stream().filter(s -> Boolean.FALSE.equals(s.getAttended())).count();
        long orders = counted.stream().map(Shift::getTotalDelivered).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        BigDecimal hours = sum(counted, Shift::getWorkingHours);
        return new Performance(from, to, attended, absent, orders, hours,
                average(counted, Shift::getBusyUtilization), average(counted, Shift::getDeliveryOntime),
                hours.signum() > 0 ? BigDecimal.valueOf(orders).divide(hours, 2, RoundingMode.HALF_UP) : null);
    }

    private static BigDecimal average(List<Shift> shifts, java.util.function.Function<Shift, BigDecimal> field) {
        List<BigDecimal> values = shifts.stream().map(field).filter(Objects::nonNull).toList();
        return values.isEmpty() ? null
                : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    ShiftItem shiftItem(Shift s, OffsetDateTime now) {
        return new ShiftItem(s.getId(), s.getShiftDate(), stateOf(s, now), s.getSupplierName(), s.getCity(),
                s.getZoneName(), s.getShiftName(), s.getScheduledStart(), s.getScheduledEnd(),
                s.getActualCheckin(), s.getDelayMinutes(), s.getWorkingHours(), s.getTotalDelivered(),
                s.getNetShiftPay(), s.isExcluded() ? s.getExclusionReason() : null);
    }

    /**
     * Shifts are imported from the supplier's sheet after the fact, so a shift with no
     * attendance yet is either still ahead, running, or waiting for that import.
     */
    static String stateOf(Shift s, OffsetDateTime now) {
        if (s.isExcluded()) {
            return "EXCLUDED";
        }
        if (Boolean.FALSE.equals(s.getAttended())) {
            return "ABSENT";
        }
        OffsetDateTime start = s.getScheduledStart();
        OffsetDateTime end = s.getScheduledEnd();
        if (start != null && end != null && !now.isBefore(start) && now.isBefore(end)) {
            return "LIVE";
        }
        boolean ahead = start != null ? now.isBefore(start) : s.getShiftDate().isAfter(now.toLocalDate());
        if (ahead) {
            return "UPCOMING";
        }
        if (Boolean.TRUE.equals(s.getAttended())) {
            return s.getDelayMinutes() != null && s.getDelayMinutes().signum() > 0 ? "LATE" : "PRESENT";
        }
        return "AWAITING";
    }

    // ---- Earnings ----

    public EarningsPeriod earnings(UUID captainId, YearMonth month) {
        Captain c = captain(captainId);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        LocalDate today = today();
        if (from.isAfter(today)) {
            throw new BusinessException("FUTURE_MONTH", "لا توجد أرباح لشهر قادم");
        }

        Optional<PayrollLine> slip = lineRepo.findForCaptainInRunStatuses(captainId, SLIP_STATUSES).stream()
                .filter(l -> from.equals(l.getPayrollRun().getPeriodFrom()) && to.equals(l.getPayrollRun().getPeriodTo()))
                .findFirst();
        EffectiveSettings settings = settingsResolver.resolve(to);
        if (slip.isPresent()) {
            PayrollRun run = slip.get().getPayrollRun();
            return new EarningsPeriod(from, to, to, run.getStatus(), slip.get().getId(),
                    settings.getMonthlyOrderTarget(), breakdown(slip.get()));
        }

        LocalDate asOf = to.isBefore(today) ? to : today;
        return new EarningsPeriod(from, to, asOf, "ESTIMATE", null, settings.getMonthlyOrderTarget(),
                estimate(c, from, to, asOf, settings));
    }

    private PayBreakdown estimate(Captain c, LocalDate from, LocalDate to, LocalDate asOf, EffectiveSettings settings) {
        Optional<CaptainPeriodTotals> totals = totals(c.getId(), from, asOf);
        int attended = totals.map(t -> (int) t.getAttendedShifts()).orElse(0);
        int absent = (int) performance(c.getId(), from, asOf).absentShifts();
        int orders = totals.map(t -> (int) t.getTotalOrders()).orElse(0);

        List<AdjustmentItem> adjustments = new ArrayList<>(adjustmentRepo.findForCaptainInPeriod(c.getId(), from, to)
                .stream().map(CaptainAppService::adjustmentItem).toList());
        // The run books the loan instalment itself; until it exists, show what it will take.
        if (adjustments.stream().noneMatch(a -> "LOAN".equals(a.type()))) {
            BigDecimal instalments = loanRepo.findByCaptainIdAndStatus(c.getId(), Loan.ACTIVE).stream()
                    .map(l -> nz(l.getMonthlyDeduction()).min(nz(l.getRemaining())))
                    .filter(a -> a.signum() > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (instalments.signum() > 0) {
                adjustments.add(new AdjustmentItem(null, CaptainAdjustment.DEDUCTION, "LOAN", "سلف",
                        instalments.negate(), null));
            }
        }
        BigDecimal adjustmentsTotal = adjustments.stream().map(AdjustmentItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PayrollCalculator.Result pay = calculator.compute(new PayrollCalculator.Input(
                c.getCaptainType(), c.isUsesOwnVehicle(), attended, orders,
                totals.map(CaptainPeriodTotals::getLateDeductions).orElse(null),
                totals.map(CaptainPeriodTotals::getSupplierDeductions).orElse(null),
                totals.map(CaptainPeriodTotals::getDailyIncentives).orElse(null),
                totals.map(CaptainPeriodTotals::getFuelAllowances).orElse(null),
                totals.map(CaptainPeriodTotals::getCarAllowances).orElse(null),
                vacationCommissionRepo.sumByCaptainAndDateBetween(c.getId(), from, asOf),
                adjustmentsTotal), settings);

        return new PayBreakdown(attended, absent, orders,
                totals.map(t -> nz(t.getTotalHours())).orElse(BigDecimal.ZERO),
                pay.basePay(), pay.dailyIncentives(), pay.vacationCommission(), pay.extraOrderPay(),
                pay.targetBonus(), pay.fuelAllowances(), pay.carAllowances(), pay.lateDeductions(),
                pay.supplierDeductions(), pay.adjustmentsTotal(), pay.gross(), pay.net(), adjustments);
    }

    private PayBreakdown breakdown(PayrollLine l) {
        PayrollRun run = l.getPayrollRun();
        List<AdjustmentItem> adjustments = adjustmentRepo
                .findForCaptainInPeriod(l.getCaptain().getId(), run.getPeriodFrom(), run.getPeriodTo()).stream()
                .filter(a -> a.getPayrollRun() == null || run.getId().equals(a.getPayrollRun().getId()))
                .map(CaptainAppService::adjustmentItem)
                .toList();
        return new PayBreakdown(l.getAttendedShifts(), l.getAbsentShifts(), l.getTotalOrders(), l.getTotalHours(),
                l.getBasePay(), l.getDailyIncentives(), l.getVacationCommission(), l.getExtraOrderPay(),
                l.getTargetBonus(), l.getFuelAllowances(), l.getCarAllowances(), l.getLateDeductions(),
                l.getSupplierDeductions(), l.getAdjustmentsTotal(), l.getGross(), l.getNet(), adjustments);
    }

    private static AdjustmentItem adjustmentItem(CaptainAdjustment a) {
        BigDecimal signed = CaptainAdjustment.BONUS.equals(a.getKind()) ? a.getAmount() : a.getAmount().negate();
        return new AdjustmentItem(a.getId(), a.getKind(), a.getAdjustmentType(), a.getLabelAr(), signed, a.getReason());
    }

    // ---- Payslips ----

    public List<PayslipSummary> payslips(UUID captainId) {
        return lineRepo.findForCaptainInRunStatuses(captainId, SLIP_STATUSES).stream()
                .map(CaptainAppService::payslipSummary)
                .toList();
    }

    public PayslipDetail payslipDetail(UUID captainId, UUID lineId) {
        PayrollLine line = lineRepo.findById(lineId)
                .filter(l -> captainId.equals(l.getCaptain().getId()) && l.getPayrollRun().isLocked())
                .orElseThrow(() -> new BusinessException("PAYSLIP_NOT_FOUND", "مسير الراتب غير موجود"));
        PayrollRun run = line.getPayrollRun();

        List<LoanInstallment> installments = loanPaymentRepo.findByPayrollRunId(run.getId()).stream()
                .filter(p -> p.getLoan().getCaptain() != null && captainId.equals(p.getLoan().getCaptain().getId()))
                .map(p -> {
                    Loan loan = p.getLoan();
                    List<LoanPayment> ordered = loan.getPayments().stream()
                            .sorted(Comparator.comparing(LoanPayment::getCreatedAt)).toList();
                    return new LoanInstallment(loan.getId(), p.getAmount(), ordered.indexOf(p) + 1,
                            instalmentCount(loan.getOriginalAmount(), loan.getMonthlyDeduction()));
                })
                .toList();

        Captain c = line.getCaptain();
        return new PayslipDetail(payslipSummary(line), breakdown(line), installments,
                c.getBankName(), last4(c.getBankAccountNumber()));
    }

    private static PayslipSummary payslipSummary(PayrollLine l) {
        PayrollRun run = l.getPayrollRun();
        return new PayslipSummary(l.getId(), run.getTitle(), run.getPeriodFrom(), run.getPeriodTo(),
                run.getStatus(), run.getPaidAt(), l.getAttendedShifts(), l.getTotalOrders(), l.getNet());
    }

    // ---- Loans ----

    public List<LoanItem> loans(UUID captainId) {
        YearMonth thisMonth = YearMonth.now(ZONE);
        Map<String, String> names = new HashMap<>();
        return loanRepo.findByCaptainIdOrderByCreatedAtDesc(captainId).stream()
                .map(loan -> {
                    BigDecimal original = nz(loan.getOriginalAmount());
                    BigDecimal remaining = nz(loan.getRemaining());
                    BigDecimal repaid = original.subtract(remaining);
                    int percent = original.signum() > 0
                            ? repaid.multiply(BigDecimal.valueOf(100)).divide(original, 0, RoundingMode.DOWN).intValue()
                            : 0;
                    Integer total = instalmentCount(original, loan.getMonthlyDeduction());
                    Integer left = loan.isActive() ? instalmentCount(remaining, loan.getMonthlyDeduction()) : Integer.valueOf(0);
                    YearMonth payoff = left != null && left > 0 ? thisMonth.plusMonths(left - 1) : null;
                    List<LoanPaymentItem> payments = loan.getPayments().stream()
                            .map(p -> new LoanPaymentItem(p.getId(), p.getAmount(), p.getSource(),
                                    p.getPayrollRun() != null ? p.getPayrollRun().getTitle() : null,
                                    p.getPayrollRun() != null ? p.getPayrollRun().getPeriodFrom() : null,
                                    p.getCreatedAt(), p.getNotes()))
                            .toList();
                    String createdBy = loan.getCreatedBy() == null ? null
                            : names.computeIfAbsent(loan.getCreatedBy(), u -> userRepo.findByUsername(u)
                                    .map(AppUser::getDisplayName).orElse(u));
                    return new LoanItem(loan.getId(), loan.getStatus(), original, remaining, repaid, percent,
                            loan.getMonthlyDeduction(), total, payments.size(), left, payoff,
                            loan.getCreatedAt(), createdBy, loan.getNotes(), payments);
                })
                .toList();
    }

    /** Whole deductions needed to clear an amount; null when no monthly deduction is set. */
    private static Integer instalmentCount(BigDecimal amount, BigDecimal monthly) {
        if (monthly == null || monthly.signum() <= 0 || amount == null) {
            return null;
        }
        return amount.divide(monthly, 0, RoundingMode.CEILING).intValue();
    }

    // ---- Documents ----

    public DocumentsResponse documents(UUID captainId) {
        Captain c = captain(captainId);
        LocalDate today = today();
        List<DocumentAlert> alerts = documentEvaluator.evaluate(c, today);
        List<DocumentRequestItem> requests = requestRepo.findByCaptainIdOrderByCreatedAtDesc(captainId).stream()
                .map(CaptainAppService::requestItem).toList();
        return new DocumentsResponse(documentEvaluator.worst(alerts).name(), documentEvaluator.alertCount(alerts),
                documentItems(c, today, requests), requests);
    }

    private List<DocumentItem> documentItems(Captain c, LocalDate today, List<DocumentRequestItem> requests) {
        return documentEvaluator.evaluate(c, today).stream()
                .map(a -> new DocumentItem(a.kind().name(), a.kind().getLabelAr(), last4(a.number()), a.expiry(),
                        a.status().name(), a.daysLeft(),
                        requests.stream()
                                .filter(r -> a.kind().name().equals(r.kind()) && CaptainDocumentRequest.PENDING.equals(r.status()))
                                .findFirst().orElse(null)))
                .toList();
    }

    static DocumentRequestItem requestItem(CaptainDocumentRequest r) {
        return new DocumentRequestItem(r.getId(), r.getDocumentKind(), r.getDocumentNumber(), r.getNewExpiry(),
                r.getStatus(), r.getReviewNote(), r.getCreatedAt(), r.getReviewedAt(),
                r.getFrontDocument() != null ? r.getFrontDocument().getId() : null,
                r.getBackDocument() != null ? r.getBackDocument().getId() : null);
    }

    // ---- Notifications ----

    /** Built on read from the captain's own data; there is no notification table to keep in sync. */
    public List<NotificationItem> notifications(UUID captainId) {
        Captain c = captain(captainId);
        LocalDate today = today();
        OffsetDateTime now = OffsetDateTime.now(ZONE);
        OffsetDateTime since = now.minusDays(NOTIFICATION_DAYS);
        List<NotificationItem> items = new ArrayList<>();

        for (DocumentAlert a : documentEvaluator.evaluate(c, today)) {
            if (!a.isAlert()) continue;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("kind", a.kind().name());
            data.put("status", a.status().name());
            data.put("expiry", a.expiry());
            data.put("daysLeft", a.daysLeft());
            items.add(new NotificationItem("DOCUMENT:" + a.kind().name(), "DOCUMENT",
                    a.status() == DocumentStatus.EXPIRED ? "HIGH" : "MEDIUM", now, data));
        }

        for (PayrollLine l : lineRepo.findForCaptainInRunStatuses(captainId, List.of(PayrollRun.PAID))) {
            OffsetDateTime paidAt = l.getPayrollRun().getPaidAt();
            if (paidAt == null || paidAt.isBefore(since)) continue;
            items.add(new NotificationItem("SALARY_PAID:" + l.getId(), "SALARY_PAID", "LOW", paidAt,
                    Map.of("payslipId", l.getId(), "title", Objects.toString(l.getPayrollRun().getTitle(), ""),
                            "net", l.getNet())));
        }

        for (Shift s : shiftRepo.findByCaptainIdAndShiftDateBetweenOrderByShiftDateAsc(captainId, today, today.plusDays(1))) {
            // A reminder appears 24 hours ahead and is dated then, never in the future.
            if (!"UPCOMING".equals(stateOf(s, now)) || s.getScheduledStart() == null
                    || s.getScheduledStart().minusHours(24).isAfter(now)) continue;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("shiftId", s.getId());
            data.put("scheduledStart", s.getScheduledStart());
            data.put("zoneName", s.getZoneName());
            items.add(new NotificationItem("SHIFT_UPCOMING:" + s.getId(), "SHIFT_UPCOMING", "MEDIUM",
                    s.getScheduledStart().minusHours(24), data));
        }

        for (CaptainDocumentRequest r : requestRepo.findByCaptainIdOrderByCreatedAtDesc(captainId)) {
            if (r.getReviewedAt() == null || r.getReviewedAt().isBefore(since)) continue;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestId", r.getId());
            data.put("kind", r.getDocumentKind());
            data.put("status", r.getStatus());
            data.put("note", r.getReviewNote());
            items.add(new NotificationItem("DOCUMENT_REVIEWED:" + r.getId(), "DOCUMENT_REVIEWED",
                    CaptainDocumentRequest.REJECTED.equals(r.getStatus()) ? "HIGH" : "LOW", r.getReviewedAt(), data));
        }

        items.sort(Comparator.comparing(NotificationItem::at).reversed());
        return items;
    }

    // ---- Devices ----

    @Transactional
    public void registerDevice(UUID captainId, DeviceRequest request) {
        Captain c = captain(captainId);
        OffsetDateTime now = OffsetDateTime.now();
        // A token follows the phone, not the captain: re-registering moves it to whoever signed in.
        CaptainDevice device = deviceRepo.findByPushToken(request.token())
                .orElseGet(() -> CaptainDevice.builder().pushToken(request.token()).createdAt(now).build());
        device.setCaptain(c);
        device.setTenant(c.getTenant());
        device.setPlatform(request.platform().trim().toUpperCase());
        device.setLocale(request.locale());
        device.setAppVersion(request.appVersion());
        device.setLastSeenAt(now);
        deviceRepo.save(device);
    }

    @Transactional
    public void unregisterDevice(UUID captainId, String token) {
        deviceRepo.findByPushToken(token)
                .filter(d -> captainId.equals(d.getCaptain().getId()))
                .ifPresent(deviceRepo::delete);
    }

    // ---- Helpers ----

    Captain captain(UUID captainId) {
        return captainRepo.findById(captainId)
                .orElseThrow(() -> new BusinessException("CAPTAIN_NOT_FOUND", "الكابتن غير موجود"));
    }

    private Optional<CaptainPeriodTotals> totals(UUID captainId, LocalDate from, LocalDate to) {
        return shiftRepo.aggregateForCaptain(captainId, from, to);
    }

    static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** Pay weeks run Saturday to Friday. */
    static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessException("INVALID_RANGE", "تاريخ النهاية قبل تاريخ البداية");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BusinessException("RANGE_TOO_LONG", "الفترة طويلة جداً، الحد الأقصى " + MAX_RANGE_DAYS + " يوماً");
        }
    }

    static String last4(String value) {
        if (value == null) return null;
        String v = value.replaceAll("\\s", "");
        return v.length() <= 4 ? v : v.substring(v.length() - 4);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static <T> BigDecimal sum(List<T> items, java.util.function.Function<T, BigDecimal> field) {
        return items.stream().map(field).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
