package com.kamal.lc.loan.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.loan.dto.*;
import com.kamal.lc.loan.entity.Loan;
import com.kamal.lc.loan.entity.LoanPayment;
import com.kamal.lc.loan.repository.LoanPaymentRepository;
import com.kamal.lc.loan.repository.LoanRepository;
import com.kamal.lc.payroll.entity.CaptainAdjustment;
import com.kamal.lc.payroll.entity.PayrollRun;
import com.kamal.lc.payroll.entity.StaffAdjustment;
import com.kamal.lc.payroll.repository.CaptainAdjustmentRepository;
import com.kamal.lc.payroll.repository.StaffAdjustmentRepository;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages salary advances (سُلف). When a payroll run is generated the service
 * automatically creates LOAN adjustments for every active loan and records the
 * corresponding payments so the remaining balance stays in sync.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepo;
    private final LoanPaymentRepository paymentRepo;
    private final CaptainRepository captainRepo;
    private final StaffRepository staffRepo;
    private final CaptainAdjustmentRepository captainAdjRepo;
    private final StaffAdjustmentRepository staffAdjRepo;

    // ---- Members list ----

    @Transactional(readOnly = true)
    public List<MemberLoanSummaryResponse> getMembers(String memberTypeFilter) {
        List<MemberLoanSummaryResponse> result = new ArrayList<>();

        if (memberTypeFilter == null || memberTypeFilter.isBlank() || "ALL".equalsIgnoreCase(memberTypeFilter)
                || "CAPTAIN".equalsIgnoreCase(memberTypeFilter)) {
            loanRepo.findCaptainSummaries().forEach(s -> result.add(toSummaryResponse(s)));
        }
        if (memberTypeFilter == null || memberTypeFilter.isBlank() || "ALL".equalsIgnoreCase(memberTypeFilter)
                || "WORKER".equalsIgnoreCase(memberTypeFilter)) {
            loanRepo.findStaffSummaries("WORKER").forEach(s -> result.add(toSummaryResponse(s)));
        }
        if (memberTypeFilter == null || memberTypeFilter.isBlank() || "ALL".equalsIgnoreCase(memberTypeFilter)
                || "MANAGER".equalsIgnoreCase(memberTypeFilter)) {
            loanRepo.findStaffSummaries("MANAGER").forEach(s -> result.add(toSummaryResponse(s)));
        }

        return result;
    }

    // ---- Member loans ----

    @Transactional(readOnly = true)
    public List<LoanResponse> getMemberLoans(UUID memberId, String memberType) {
        List<Loan> loans;
        if ("CAPTAIN".equalsIgnoreCase(memberType)) {
            loans = loanRepo.findByCaptainIdOrderByCreatedAtDesc(memberId);
        } else {
            loans = loanRepo.findByStaffIdOrderByCreatedAtDesc(memberId);
        }
        return loans.stream().map(this::toLoanResponse).toList();
    }

    // ---- Create loan ----

    @Transactional
    public LoanResponse createLoan(LoanRequest request) {
        Captain captain = null;
        Staff staff = null;

        if ("CAPTAIN".equalsIgnoreCase(request.getMemberType())) {
            if (request.getCaptainId() == null) {
                throw new BusinessException("MISSING_CAPTAIN", "معرف الكابتن مطلوب");
            }
            captain = captainRepo.findById(request.getCaptainId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "الكابتن غير موجود"));
        } else {
            if (request.getStaffId() == null) {
                throw new BusinessException("MISSING_STAFF", "معرف الموظف مطلوب");
            }
            staff = staffRepo.findById(request.getStaffId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "الموظف غير موجود"));
        }

        Loan loan = Loan.builder()
                .captain(captain)
                .staff(staff)
                .memberType(request.getMemberType().toUpperCase())
                .originalAmount(request.getAmount())
                .remaining(request.getAmount())
                .monthlyDeduction(request.getMonthlyDeduction() != null ? request.getMonthlyDeduction() : BigDecimal.ZERO)
                .status(Loan.ACTIVE)
                .notes(request.getNotes())
                .createdBy(currentUser())
                .build();

        loan = loanRepo.save(loan);
        log.info("Created loan {} for {} {} — amount: {}, monthly: {}",
                loan.getId(), request.getMemberType(),
                captain != null ? captain.getKamalCode() : staff.getStaffCode(),
                request.getAmount(), request.getMonthlyDeduction());

        return toLoanResponse(loan);
    }

    // ---- Manual payment ----

    @Transactional
    public LoanResponse addManualPayment(UUID loanId, LoanPaymentRequest request) {
        Loan loan = requireLoan(loanId);
        if (!loan.isActive()) {
            throw new BusinessException("LOAN_NOT_ACTIVE", "السلفة غير نشطة — لا يمكن تسجيل دفعة");
        }

        BigDecimal paymentAmount = request.getAmount();
        if (paymentAmount.compareTo(loan.getRemaining()) > 0) {
            paymentAmount = loan.getRemaining();
        }

        LoanPayment payment = LoanPayment.builder()
                .loan(loan)
                .amount(paymentAmount)
                .source(LoanPayment.MANUAL)
                .notes(request.getNotes())
                .createdBy(currentUser())
                .build();

        paymentRepo.save(payment);

        loan.setRemaining(loan.getRemaining().subtract(paymentAmount));
        if (loan.getRemaining().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setRemaining(BigDecimal.ZERO);
            loan.setStatus(Loan.PAID_OFF);
        }
        loanRepo.save(loan);

        log.info("Manual payment {} on loan {} — remaining: {}", paymentAmount, loanId, loan.getRemaining());
        return toLoanResponse(loan);
    }

    // ---- Update monthly deduction ----

    @Transactional
    public LoanResponse updateMonthlyDeduction(UUID loanId, BigDecimal newDeduction) {
        Loan loan = requireLoan(loanId);
        if (!loan.isActive()) {
            throw new BusinessException("LOAN_NOT_ACTIVE", "السلفة غير نشطة");
        }
        loan.setMonthlyDeduction(newDeduction);
        loanRepo.save(loan);
        return toLoanResponse(loan);
    }

    // ---- Cancel loan ----

    @Transactional
    public LoanResponse cancelLoan(UUID loanId) {
        Loan loan = requireLoan(loanId);
        if (!loan.isActive()) {
            throw new BusinessException("LOAN_NOT_ACTIVE", "السلفة ليست نشطة");
        }
        loan.setStatus(Loan.CANCELLED);
        loanRepo.save(loan);
        log.info("Cancelled loan {}", loanId);
        return toLoanResponse(loan);
    }

    // ---- Payroll integration ----

    /**
     * Called during payroll generation: for each active loan whose member is in the run,
     * create a LOAN adjustment and a payment record, then reduce the remaining balance.
     */
    @Transactional
    public void processPayrollDeductions(PayrollRun run) {
        List<Loan> activeLoans = loanRepo.findByStatusOrderByCreatedAtAsc(Loan.ACTIVE);
        int count = 0;

        for (Loan loan : activeLoans) {
            BigDecimal deduction = loan.getMonthlyDeduction();
            if (deduction == null || deduction.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Cap at remaining balance
            if (deduction.compareTo(loan.getRemaining()) > 0) {
                deduction = loan.getRemaining();
            }
            if (deduction.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Create the appropriate adjustment
            if ("CAPTAIN".equals(loan.getMemberType()) && loan.getCaptain() != null) {
                CaptainAdjustment adj = CaptainAdjustment.builder()
                        .captain(loan.getCaptain())
                        .periodFrom(run.getPeriodFrom())
                        .periodTo(run.getPeriodTo())
                        .kind(CaptainAdjustment.DEDUCTION)
                        .adjustmentType("LOAN")
                        .labelAr("سلف")
                        .amount(deduction)
                        .reason("خصم تلقائي من سلفة #" + loan.getId().toString().substring(0, 8))
                        .payrollRun(run)
                        .createdBy("SYSTEM")
                        .build();
                captainAdjRepo.save(adj);
            } else if (loan.getStaff() != null) {
                StaffAdjustment adj = StaffAdjustment.builder()
                        .staff(loan.getStaff())
                        .periodFrom(run.getPeriodFrom())
                        .periodTo(run.getPeriodTo())
                        .kind(StaffAdjustment.DEDUCTION)
                        .adjustmentType("LOAN")
                        .labelAr("سلف")
                        .amount(deduction)
                        .reason("خصم تلقائي من سلفة #" + loan.getId().toString().substring(0, 8))
                        .payrollRun(run)
                        .createdBy("SYSTEM")
                        .build();
                staffAdjRepo.save(adj);
            }

            // Record the payment
            LoanPayment payment = LoanPayment.builder()
                    .loan(loan)
                    .amount(deduction)
                    .source(LoanPayment.PAYROLL_DEDUCTION)
                    .payrollRun(run)
                    .notes("خصم تلقائي — مسير " + run.getPeriodFrom() + " إلى " + run.getPeriodTo())
                    .createdBy("SYSTEM")
                    .build();
            paymentRepo.save(payment);

            // Reduce remaining
            loan.setRemaining(loan.getRemaining().subtract(deduction));
            if (loan.getRemaining().compareTo(BigDecimal.ZERO) <= 0) {
                loan.setRemaining(BigDecimal.ZERO);
                loan.setStatus(Loan.PAID_OFF);
            }
            loanRepo.save(loan);
            count++;
        }

        log.info("Processed {} loan deductions for payroll run {}", count, run.getId());
    }

    /**
     * Reverses automatic payroll loan deductions when a payroll run is regenerated or deleted.
     * Only reverses payments that were auto-generated by the system (not manual LOAN adjustments
     * added by the user through the adjustments UI), so manual loan deductions are preserved.
     * Also cleans up the system-generated LOAN CaptainAdjustment/StaffAdjustment records to
     * prevent duplicates accumulating on each regeneration.
     */
    @Transactional
    public void reversePayrollDeductions(PayrollRun run) {
        // Only reverse automatic payments (those NOT linked to a manual adjustment)
        List<LoanPayment> automaticPayments = paymentRepo
                .findByPayrollRunIdAndCaptainAdjustmentIdIsNullAndStaffAdjustmentIdIsNull(run.getId());

        for (LoanPayment payment : automaticPayments) {
            Loan loan = payment.getLoan();
            loan.setRemaining(loan.getRemaining().add(payment.getAmount()));
            if (loan.getRemaining().compareTo(BigDecimal.ZERO) > 0
                    && Loan.PAID_OFF.equals(loan.getStatus())) {
                loan.setStatus(Loan.ACTIVE);
            }
            loanRepo.save(loan);
        }
        paymentRepo.deleteByPayrollRunIdAndCaptainAdjustmentIdIsNullAndStaffAdjustmentIdIsNull(run.getId());

        // Clean up system-generated LOAN adjustments to prevent duplicates on regeneration
        captainAdjRepo.deleteByPayrollRunIdAndCreatedBy(run.getId(), "SYSTEM");
        staffAdjRepo.deleteByPayrollRunIdAndCreatedBy(run.getId(), "SYSTEM");

        log.info("Reversed {} automatic loan payments for payroll run {}", automaticPayments.size(), run.getId());
    }

    @Transactional
    public void processManualLoanDeduction(UUID memberId, String memberType, BigDecimal amount, PayrollRun run, UUID captainAdjId, UUID staffAdjId, String notes) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        List<Loan> activeLoans;
        if ("CAPTAIN".equalsIgnoreCase(memberType)) {
            activeLoans = loanRepo.findByCaptainIdAndStatusOrderByCreatedAtAsc(memberId, Loan.ACTIVE);
        } else {
            activeLoans = loanRepo.findByStaffIdAndStatusOrderByCreatedAtAsc(memberId, Loan.ACTIVE);
        }

        BigDecimal remainingToDeduct = amount;

        for (Loan loan : activeLoans) {
            if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal deduction = remainingToDeduct;
            if (deduction.compareTo(loan.getRemaining()) > 0) {
                deduction = loan.getRemaining();
            }

            LoanPayment payment = LoanPayment.builder()
                    .loan(loan)
                    .amount(deduction)
                    .source(LoanPayment.PAYROLL_DEDUCTION)
                    .payrollRun(run)
                    .captainAdjustmentId(captainAdjId)
                    .staffAdjustmentId(staffAdjId)
                    .notes(notes != null && !notes.isBlank() ? notes : "خصم يدوي من مسير الرواتب")
                    .createdBy(currentUser())
                    .build();
            paymentRepo.save(payment);

            loan.setRemaining(loan.getRemaining().subtract(deduction));
            if (loan.getRemaining().compareTo(BigDecimal.ZERO) <= 0) {
                loan.setRemaining(BigDecimal.ZERO);
                loan.setStatus(Loan.PAID_OFF);
            }
            loanRepo.save(loan);

            remainingToDeduct = remainingToDeduct.subtract(deduction);
        }

        log.info("Processed manual loan deduction of {} for {} {}", amount, memberType, memberId);
    }

    @Transactional
    public void reverseManualLoanDeduction(UUID captainAdjId, UUID staffAdjId) {
        List<LoanPayment> payments = new ArrayList<>();
        if (captainAdjId != null) {
            payments.addAll(paymentRepo.findByCaptainAdjustmentId(captainAdjId));
        }
        if (staffAdjId != null) {
            payments.addAll(paymentRepo.findByStaffAdjustmentId(staffAdjId));
        }

        for (LoanPayment payment : payments) {
            Loan loan = payment.getLoan();
            loan.setRemaining(loan.getRemaining().add(payment.getAmount()));
            if (loan.getRemaining().compareTo(BigDecimal.ZERO) > 0 && Loan.PAID_OFF.equals(loan.getStatus())) {
                loan.setStatus(Loan.ACTIVE);
            }
            loanRepo.save(loan);
            paymentRepo.delete(payment);
        }
        log.info("Reversed {} payments for manual adjustment captainAdjId={} staffAdjId={}", payments.size(), captainAdjId, staffAdjId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getActiveLoanBalance(UUID memberId, String memberType) {
        if ("CAPTAIN".equalsIgnoreCase(memberType)) {
            return loanRepo.findActiveLoanBalanceByCaptainId(memberId);
        } else {
            return loanRepo.findActiveLoanBalanceByStaffId(memberId);
        }
    }

    // ---- Helpers ----

    private Loan requireLoan(UUID loanId) {
        return loanRepo.findById(loanId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "السلفة غير موجودة"));
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "SYSTEM";
    }

    private LoanResponse toLoanResponse(Loan loan) {
        Captain captain = loan.getCaptain();
        Staff staff = loan.getStaff();

        return LoanResponse.builder()
                .id(loan.getId().toString())
                .memberId(captain != null ? captain.getId().toString() : staff.getId().toString())
                .memberCode(captain != null ? captain.getKamalCode() : staff.getStaffCode())
                .memberName(captain != null ? captain.getFullNameAr() : staff.getFullNameAr())
                .memberType(loan.getMemberType())
                .originalAmount(loan.getOriginalAmount())
                .remaining(loan.getRemaining())
                .monthlyDeduction(loan.getMonthlyDeduction())
                .status(loan.getStatus())
                .notes(loan.getNotes())
                .createdBy(loan.getCreatedBy())
                .createdAt(loan.getCreatedAt() != null ? loan.getCreatedAt().toString() : null)
                .payments(loan.getPayments() != null
                        ? loan.getPayments().stream().map(this::toPaymentResponse).toList()
                        : List.of())
                .build();
    }

    private LoanPaymentResponse toPaymentResponse(LoanPayment p) {
        return LoanPaymentResponse.builder()
                .id(p.getId().toString())
                .loanId(p.getLoan().getId().toString())
                .amount(p.getAmount())
                .source(p.getSource())
                .sourceLabel(LoanPayment.PAYROLL_DEDUCTION.equals(p.getSource()) ? "خصم من المسير" : "دفعة يدوية")
                .payrollRunId(p.getPayrollRun() != null ? p.getPayrollRun().getId().toString() : null)
                .notes(p.getNotes())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null)
                .build();
    }

    private MemberLoanSummaryResponse toSummaryResponse(LoanRepository.MemberLoanSummary s) {
        return MemberLoanSummaryResponse.builder()
                .memberId(s.getMemberId().toString())
                .memberCode(s.getMemberCode())
                .memberName(s.getMemberName())
                .memberNameEn(s.getMemberNameEn())
                .memberType(s.getMemberType())
                .memberTypeLabel(memberTypeLabel(s.getMemberType()))
                .phone(s.getPhone())
                .activeLoanCount(s.getActiveLoanCount())
                .totalRemaining(s.getTotalRemaining())
                .totalOriginal(s.getTotalOriginal())
                .build();
    }

    private static String memberTypeLabel(String memberType) {
        return switch (memberType) {
            case "CAPTAIN" -> "كابتن";
            case "WORKER" -> "عامل";
            case "MANAGER" -> "إداري";
            default -> memberType;
        };
    }
}
