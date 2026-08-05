import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LoanService } from '../../../core/services/loan.service';
import { LoanResponse, LoanRequest } from '../../../core/models/api.models';
import { errorMessage } from '../../../core/services/api.util';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-loan-detail',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './loan-detail.component.html',
  styleUrls: ['./loan-detail.component.scss']
})
export class LoanDetailComponent implements OnInit {

  private loanService = inject(LoanService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  public authService = inject(AuthService);

  get canManage(): boolean {
    return this.authService.hasPermission('LOANS_MANAGE');
  }

  memberId = '';
  memberType = '';
  memberName = '';
  memberCode = '';

  loans: LoanResponse[] = [];
  isLoading = true;
  isBusy = false;
  errorMsg = '';
  successMsg = '';

  // New loan form
  showNewLoanForm = false;
  newAmount: number | null = null;
  newMonthlyDeduction: number | null = null;
  newNotes = '';

  // Payment form
  paymentForLoan: LoanResponse | null = null;
  paymentAmount: number | null = null;
  paymentNotes = '';

  // Deduction edit
  editingDeduction: LoanResponse | null = null;
  editDeductionValue: number | null = null;

  ngOnInit(): void {
    this.memberType = this.route.snapshot.paramMap.get('memberType') || '';
    this.memberId = this.route.snapshot.paramMap.get('memberId') || '';
    this.loadLoans();
  }

  loadLoans(): void {
    this.isLoading = true;
    this.loanService.getMemberLoans(this.memberId, this.memberType).subscribe({
      next: (data) => {
        this.isLoading = false;
        this.loans = data;
        if (data.length > 0) {
          this.memberName = data[0].memberName;
          this.memberCode = data[0].memberCode;
        }
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMsg = errorMessage(err, 'فشل في تحميل بيانات السلف');
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/loans']);
  }

  // ---- New loan ----

  createLoan(): void {
    if (!this.newAmount) return;

    const request: LoanRequest = {
      captainId: this.memberType === 'CAPTAIN' ? this.memberId : null,
      staffId: this.memberType !== 'CAPTAIN' ? this.memberId : null,
      memberType: this.memberType,
      amount: this.newAmount,
      monthlyDeduction: this.newMonthlyDeduction || 0,
      notes: this.newNotes.trim() || null
    };

    this.isBusy = true;
    this.clearMessages();
    this.loanService.create(request).subscribe({
      next: () => {
        this.isBusy = false;
        this.successMsg = 'تم تسجيل السلفة بنجاح';
        this.showNewLoanForm = false;
        this.newAmount = null;
        this.newMonthlyDeduction = null;
        this.newNotes = '';
        this.loadLoans();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMsg = errorMessage(err, 'فشل في تسجيل السلفة');
      }
    });
  }

  // ---- Manual payment ----

  openPaymentForm(loan: LoanResponse): void {
    this.paymentForLoan = loan;
    this.paymentAmount = null;
    this.paymentNotes = '';
  }

  cancelPayment(): void {
    this.paymentForLoan = null;
  }

  savePayment(): void {
    if (!this.paymentForLoan || !this.paymentAmount) return;

    this.isBusy = true;
    this.clearMessages();
    this.loanService.addPayment(this.paymentForLoan.id, {
      amount: this.paymentAmount,
      notes: this.paymentNotes.trim() || null
    }).subscribe({
      next: () => {
        this.isBusy = false;
        this.successMsg = 'تم تسجيل الدفعة';
        this.cancelPayment();
        this.loadLoans();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMsg = errorMessage(err, 'فشل في تسجيل الدفعة');
      }
    });
  }

  // ---- Edit deduction ----

  startEditDeduction(loan: LoanResponse): void {
    this.editingDeduction = loan;
    this.editDeductionValue = loan.monthlyDeduction;
  }

  cancelEditDeduction(): void {
    this.editingDeduction = null;
  }

  saveDeduction(): void {
    if (!this.editingDeduction || this.editDeductionValue == null) return;

    this.isBusy = true;
    this.clearMessages();
    this.loanService.updateDeduction(this.editingDeduction.id, this.editDeductionValue).subscribe({
      next: () => {
        this.isBusy = false;
        this.successMsg = 'تم تحديث مبلغ الخصم الشهري';
        this.cancelEditDeduction();
        this.loadLoans();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMsg = errorMessage(err, 'فشل في التحديث');
      }
    });
  }

  // ---- Cancel loan ----

  cancelLoan(loan: LoanResponse): void {
    if (!confirm('هل أنت متأكد من إلغاء هذه السلفة؟')) return;

    this.isBusy = true;
    this.clearMessages();
    this.loanService.cancel(loan.id).subscribe({
      next: () => {
        this.isBusy = false;
        this.successMsg = 'تم إلغاء السلفة';
        this.loadLoans();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMsg = errorMessage(err, 'فشل في إلغاء السلفة');
      }
    });
  }

  clearMessages(): void {
    this.errorMsg = '';
    this.successMsg = '';
  }

  get activeLoans(): LoanResponse[] {
    return this.loans.filter(l => l.status === 'ACTIVE');
  }

  get closedLoans(): LoanResponse[] {
    return this.loans.filter(l => l.status !== 'ACTIVE');
  }

  get totalRemaining(): number {
    return this.activeLoans.reduce((sum, l) => sum + l.remaining, 0);
  }

  get totalOriginal(): number {
    return this.activeLoans.reduce((sum, l) => sum + l.originalAmount, 0);
  }

  statusLabel(status: string): string {
    return { ACTIVE: 'نشطة', PAID_OFF: 'مسددة', CANCELLED: 'ملغاة' }[status] || status;
  }

  statusClass(status: string): string {
    return { ACTIVE: 'status-active', PAID_OFF: 'status-paid', CANCELLED: 'status-cancelled' }[status] || '';
  }

  memberTypeLabel(type: string): string {
    return { CAPTAIN: 'كابتن', WORKER: 'عامل', MANAGER: 'إداري' }[type] || type;
  }

  money(value: number | null | undefined): string {
    if (value == null) return '—';
    return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
