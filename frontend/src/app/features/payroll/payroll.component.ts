import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import {
  AdjustmentRequest, AdjustmentType,
  PayrollLineResponse, PayrollRunResponse,
  StaffPayrollLineResponse, StaffAdjustmentType, StaffAdjustmentRequest
} from '../../core/models/api.models';
import { PayrollService } from '../../core/services/payroll.service';
import { downloadBlob, errorMessage, isoDate } from '../../core/services/api.util';

interface AdjustmentOption {
  type: AdjustmentType;
  label: string;
  kind: 'BONUS' | 'DEDUCTION';
}

interface StaffAdjustmentOption {
  type: StaffAdjustmentType;
  label: string;
  kind: 'BONUS' | 'DEDUCTION';
}

@Component({
  selector: 'app-payroll',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './payroll.component.html',
  styleUrls: ['./payroll.component.scss']
})
export class PayrollComponent implements OnInit {

  private payrollService = inject(PayrollService);

  /** Mirrors the named types in CaptainAdjustment — each maps to a payroll-sheet column. */
  readonly adjustmentOptions: AdjustmentOption[] = [
    { type: 'HOUSING_ALLOWANCE', label: 'بدل سكن', kind: 'BONUS' },
    { type: 'REWARD', label: 'مكافأة', kind: 'BONUS' },
    { type: 'FUEL_DIFFERENCE', label: 'فروقات البنزين', kind: 'BONUS' },
    { type: 'OTHER_BONUS', label: 'إضافة أخرى', kind: 'BONUS' },
    { type: 'SUPPLIER_PENALTY', label: 'غرامات المورد', kind: 'DEDUCTION' },
    { type: 'LOAN', label: 'سلف', kind: 'DEDUCTION' },
    { type: 'OTHER_DEDUCTION', label: 'خصم آخر', kind: 'DEDUCTION' },
  ];

  readonly staffAdjustmentOptions: StaffAdjustmentOption[] = [
    { type: 'REWARD', label: 'مكافأة', kind: 'BONUS' },
    { type: 'OTHER_BONUS', label: 'إضافة أخرى', kind: 'BONUS' },
    { type: 'SUPPLIER_PENALTY', label: 'غرامات المورد', kind: 'DEDUCTION' },
    { type: 'LOAN', label: 'سلف', kind: 'DEDUCTION' },
    { type: 'OTHER_DEDUCTION', label: 'خصم آخر', kind: 'DEDUCTION' },
  ];

  runs: PayrollRunResponse[] = [];
  selectedRun: PayrollRunResponse | null = null;
  
  activeTab: 'CAPTAIN' | 'WORKER' | 'MANAGER' = 'CAPTAIN';
  lines: PayrollLineResponse[] = [];
  staffLines: StaffPayrollLineResponse[] = [];

  isLoadingRuns = true;
  isLoadingLines = false;
  isGenerating = false;
  isBusy = false;

  // Generate form
  showGenerateForm = false;
  periodFrom = '';
  periodTo = '';
  title = '';

  // Adjustment form
  adjustmentForCaptain: PayrollLineResponse | null = null;
  adjustmentType: AdjustmentType = 'HOUSING_ALLOWANCE';
  adjustmentAmount: number | null = null;
  adjustmentReason = '';

  // Staff adjustment form
  staffAdjustmentFor: StaffPayrollLineResponse | null = null;
  staffAdjustmentType: StaffAdjustmentType = 'REWARD';
  staffAdjustmentAmount: number | null = null;
  staffAdjustmentReason = '';

  // Confirmations
  confirmingApproval = false;
  confirmingDelete = false;
  reopenReason = '';
  showReopenForm = false;

  errorMessage = '';
  successMessage = '';

  ngOnInit(): void {
    const today = new Date();
    this.periodFrom = isoDate(new Date(today.getFullYear(), today.getMonth(), 1));
    this.periodTo = isoDate(new Date(today.getFullYear(), today.getMonth() + 1, 0));
    this.loadRuns();
  }

  // ---------------------------------------------------------------- data

  loadRuns(selectId?: string): void {
    this.isLoadingRuns = true;
    this.payrollService.list().subscribe({
      next: (page) => {
        this.isLoadingRuns = false;
        this.runs = page.content;

        const target = selectId
          ? this.runs.find(r => r.id === selectId)
          : this.runs.find(r => r.id === this.selectedRun?.id) ?? this.runs[0];

        if (!target) {
          this.selectedRun = null;
          this.lines = [];
          return;
        }

        // Re-selecting the run already on screen would refetch lines that have not
        // changed — swap the header in place and keep the loaded lines.
        if (target.id === this.selectedRun?.id) this.selectedRun = target;
        else this.selectRun(target);
      },
      error: (err) => {
        this.isLoadingRuns = false;
        this.errorMessage = errorMessage(err, 'فشل في تحميل المسيرات');
      }
    });
  }

  selectRun(run: PayrollRunResponse): void {
    this.selectedRun = run;
    this.confirmingApproval = false;
    this.confirmingDelete = false;
    this.showReopenForm = false;
    this.cancelAdjustment();
    this.cancelStaffAdjustment();
    this.loadData();
  }

  switchTab(tab: 'CAPTAIN' | 'WORKER' | 'MANAGER'): void {
    this.activeTab = tab;
    if (this.selectedRun) {
      this.loadData();
    }
  }

  loadData(): void {
    if (!this.selectedRun) return;
    if (this.activeTab === 'CAPTAIN') {
      this.loadLines(this.selectedRun.id);
    } else {
      this.loadStaffLines(this.selectedRun.id, this.activeTab);
    }
  }

  loadLines(runId: string): void {
    this.isLoadingLines = true;
    this.payrollService.getLines(runId).subscribe({
      next: (lines) => {
        this.isLoadingLines = false;
        this.lines = lines;
      },
      error: (err) => {
        this.isLoadingLines = false;
        this.lines = [];
        this.errorMessage = errorMessage(err, 'فشل في تحميل أسطر المسير');
      }
    });
  }

  loadStaffLines(runId: string, type: string): void {
    this.isLoadingLines = true;
    this.payrollService.getStaffLines(runId, type).subscribe({
      next: (lines) => {
        this.isLoadingLines = false;
        this.staffLines = lines;
      },
      error: (err) => {
        this.isLoadingLines = false;
        this.staffLines = [];
        this.errorMessage = errorMessage(err, 'فشل في تحميل أسطر الموظفين');
      }
    });
  }

  // ---------------------------------------------------------------- actions

  generate(): void {
    this.isGenerating = true;
    this.clearMessages();

    this.payrollService.generate({
      periodFrom: this.periodFrom,
      periodTo: this.periodTo,
      title: this.title.trim() || null
    }).subscribe({
      next: (run) => {
        this.isGenerating = false;
        this.successMessage = 'تم توليد المسير بنجاح';
        this.showGenerateForm = false;
        this.title = '';
        this.loadRuns(run.id);
      },
      error: (err) => {
        this.isGenerating = false;
        this.errorMessage = errorMessage(err, 'فشل في توليد المسير');
      }
    });
  }

  /**
   * Rebuilds the selected draft from the shifts as they stand now.
   *
   * <p>A run is a snapshot taken at generation time, so one generated before its
   * period's shifts were imported stays empty until it is rebuilt — without this the
   * only way out was deleting the run and recreating it with the same dates.
   */
  regenerate(): void {
    const run = this.selectedRun;
    if (!run || run.locked) return;

    this.isGenerating = true;
    this.clearMessages();

    this.payrollService.generate({
      periodFrom: run.periodFrom,
      periodTo: run.periodTo,
      title: run.title || null
    }).subscribe({
      next: (updated) => {
        this.isGenerating = false;
        this.successMessage = 'تمت إعادة توليد المسير من البيانات المسجلة';
        if (this.activeTab === 'CAPTAIN') {
          this.loadLines(updated.id);
        } else {
          this.loadStaffLines(updated.id, this.activeTab);
        }
        this.loadRuns(updated.id);
      },
      error: (err) => {
        this.isGenerating = false;
        this.errorMessage = errorMessage(err, 'فشل في إعادة توليد المسير');
      }
    });
  }

  approve(): void {
    if (!this.selectedRun) return;
    this.confirmingApproval = false;
    this.runAction(
      this.payrollService.approve(this.selectedRun.id),
      'تم اعتماد المسير — لن تتغير أرقامه بعد الآن'
    );
  }

  markPaid(): void {
    if (!this.selectedRun) return;
    this.runAction(this.payrollService.markPaid(this.selectedRun.id), 'تم تسجيل المسير كمدفوع');
  }

  reopen(): void {
    if (!this.selectedRun || !this.reopenReason.trim()) return;
    this.showReopenForm = false;
    this.runAction(
      this.payrollService.reopen(this.selectedRun.id, this.reopenReason.trim()),
      'تم إعادة فتح المسير كمسودة'
    );
    this.reopenReason = '';
  }

  deleteRun(): void {
    if (!this.selectedRun) return;
    this.isBusy = true;
    this.confirmingDelete = false;
    this.clearMessages();

    this.payrollService.delete(this.selectedRun.id).subscribe({
      next: () => {
        this.isBusy = false;
        this.successMessage = 'تم حذف المسير';
        this.selectedRun = null;
        this.lines = [];
        this.staffLines = [];
        this.loadRuns();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMessage = errorMessage(err, 'فشل في حذف المسير');
      }
    });
  }

  /** Status transitions all return the updated run header and refresh the list. */
  private runAction(action: Observable<PayrollRunResponse>, message: string): void {
    this.isBusy = true;
    this.clearMessages();
    action.subscribe({
      next: (run) => {
        this.isBusy = false;
        this.successMessage = message;
        this.loadRuns(run.id);
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMessage = errorMessage(err, 'فشل في تنفيذ العملية');
      }
    });
  }

  export(): void {
    if (!this.selectedRun) return;
    const run = this.selectedRun;
    const tab = this.activeTab;

    let filePrefix = 'مسير-رواتب-';
    if (tab === 'WORKER') filePrefix = 'مسير-رواتب-العاملين-';
    else if (tab === 'MANAGER') filePrefix = 'مسير-رواتب-الإداريين-';

    this.payrollService.exportWorkbook(run.id, tab).subscribe({
      next: (blob) => downloadBlob(blob, `${filePrefix}${run.periodFrom}.xlsx`),
      error: () => { this.errorMessage = 'فشل تحميل ملف المسير'; }
    });
  }

  // ---------------------------------------------------------------- adjustments

  openAdjustment(line: PayrollLineResponse): void {
    this.adjustmentForCaptain = line;
    this.adjustmentType = 'HOUSING_ALLOWANCE';
    this.adjustmentAmount = null;
    this.adjustmentReason = '';
  }

  cancelAdjustment(): void {
    this.adjustmentForCaptain = null;
    this.adjustmentAmount = null;
    this.adjustmentReason = '';
  }

  saveAdjustment(): void {
    if (!this.selectedRun || !this.adjustmentForCaptain || !this.adjustmentAmount) return;

    const payload: AdjustmentRequest = {
      captainId: this.adjustmentForCaptain.captainId,
      adjustmentType: this.adjustmentType,
      amount: this.adjustmentAmount,
      reason: this.adjustmentReason.trim() || null
    };

    this.isBusy = true;
    this.clearMessages();
    this.payrollService.addAdjustment(this.selectedRun.id, payload).subscribe({
      next: (line) => {
        this.isBusy = false;
        this.replaceLine(line);
        this.successMessage = 'تمت إضافة البند';
        this.cancelAdjustment();
        this.refreshRunTotals();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMessage = errorMessage(err, 'فشل في إضافة البند');
      }
    });
  }

  removeAdjustment(adjustmentId: string): void {
    if (!this.selectedRun) return;
    this.isBusy = true;
    this.clearMessages();

    this.payrollService.deleteAdjustment(this.selectedRun.id, adjustmentId).subscribe({
      next: (line) => {
        this.isBusy = false;
        this.replaceLine(line);
        this.successMessage = 'تم حذف البند';
        this.refreshRunTotals();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMessage = errorMessage(err, 'فشل في حذف البند');
      }
    });
  }

  private replaceLine(updated: PayrollLineResponse): void {
    this.lines = this.lines.map(l => l.captainId === updated.captainId ? updated : l);
  }

  // ---------------------------------------------------------------- staff adjustments

  openStaffAdjustment(line: StaffPayrollLineResponse): void {
    this.staffAdjustmentFor = line;
    this.staffAdjustmentType = 'REWARD';
    this.staffAdjustmentAmount = null;
    this.staffAdjustmentReason = '';
  }

  cancelStaffAdjustment(): void {
    this.staffAdjustmentFor = null;
    this.staffAdjustmentAmount = null;
    this.staffAdjustmentReason = '';
  }

  saveStaffAdjustment(): void {
    if (!this.selectedRun || !this.staffAdjustmentFor || !this.staffAdjustmentAmount) return;

    const payload: StaffAdjustmentRequest = {
      staffId: this.staffAdjustmentFor.staffId,
      adjustmentType: this.staffAdjustmentType,
      amount: this.staffAdjustmentAmount,
      reason: this.staffAdjustmentReason.trim() || null
    };

    this.isBusy = true;
    this.clearMessages();
    this.payrollService.addStaffAdjustment(this.selectedRun.id, payload).subscribe({
      next: (line) => {
        this.isBusy = false;
        this.replaceStaffLine(line);
        this.successMessage = 'تمت إضافة البند للموظف';
        this.cancelStaffAdjustment();
        this.refreshRunTotals();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMessage = errorMessage(err, 'فشل في إضافة البند');
      }
    });
  }

  removeStaffAdjustment(adjustmentId: string): void {
    if (!this.selectedRun) return;
    this.isBusy = true;
    this.clearMessages();

    this.payrollService.deleteStaffAdjustment(this.selectedRun.id, adjustmentId).subscribe({
      next: (line) => {
        this.isBusy = false;
        this.replaceStaffLine(line);
        this.successMessage = 'تم حذف البند';
        this.refreshRunTotals();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMessage = errorMessage(err, 'فشل في حذف البند');
      }
    });
  }

  private replaceStaffLine(updated: StaffPayrollLineResponse): void {
    this.staffLines = this.staffLines.map(l => l.staffId === updated.staffId ? updated : l);
  }

  /** The run header carries its own totals, so refetch it after a line changes. */
  private refreshRunTotals(): void {
    if (!this.selectedRun) return;
    const runId = this.selectedRun.id;
    this.payrollService.get(runId).subscribe({
      next: (run) => {
        // The user may have switched runs while this was in flight.
        if (this.selectedRun?.id !== runId) return;
        this.selectedRun = run;
        this.runs = this.runs.map(r => r.id === run.id ? run : r);
      }
    });
  }

  clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }

  // ---------------------------------------------------------------- display

  get totals() {
    return this.lines.reduce((acc, l) => ({
      shifts: acc.shifts + l.attendedShifts,
      orders: acc.orders + l.totalOrders,
      basePay: acc.basePay + l.basePay,
      deductions: acc.deductions + l.lateDeductions,
      incentives: acc.incentives + l.dailyIncentives,
      fuel: acc.fuel + l.fuelAllowances,
      car: acc.car + l.carAllowances,
      targetBonus: acc.targetBonus + l.targetBonus,
      extraOrderPay: acc.extraOrderPay + l.extraOrderPay,
      gross: acc.gross + l.gross,
      adjustments: acc.adjustments + l.adjustmentsTotal,
      net: acc.net + l.net,
      revenue: acc.revenue + l.supplierRevenueTotal,
      margin: acc.margin + l.margin,
    }), {
      shifts: 0, orders: 0, basePay: 0, deductions: 0, incentives: 0, fuel: 0, car: 0,
      targetBonus: 0, extraOrderPay: 0, gross: 0, adjustments: 0, net: 0, revenue: 0, margin: 0
    });
  }

  statusLabel(status: string): string {
    return { DRAFT: 'مسودة', APPROVED: 'معتمد', PAID: 'مدفوع' }[status] || status;
  }

  statusClass(status: string): string {
    return { DRAFT: 'badge-warning', APPROVED: 'badge-info', PAID: 'badge-success' }[status] || '';
  }

  typeLabel(captainType: string): string {
    return captainType === 'FREELANCE' ? 'فريلانس' : 'كفالة';
  }

  money(value: number | null | undefined): string {
    if (value == null) return '—';
    return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  percent(value: number | null): string {
    return value == null ? '—' : `${(value * 100).toFixed(1)}%`;
  }

}
