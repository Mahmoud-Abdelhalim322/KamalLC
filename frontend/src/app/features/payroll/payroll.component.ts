import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable, Subject, debounceTime } from 'rxjs';
import {
  AdjustmentRequest, AdjustmentType,
  PayrollLineResponse, PayrollLineTotals, PayrollRunResponse,
  StaffPayrollLineResponse, StaffAdjustmentType, StaffAdjustmentRequest
} from '../../core/models/api.models';
import { PayrollService } from '../../core/services/payroll.service';
import { PAGE_SIZE, downloadBlob, errorMessage, isoDate } from '../../core/services/api.util';

import { AuthService } from '../../core/auth/auth.service';

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

import { TranslationService, TranslatePipe } from '../../core/i18n';
import { SarPipe } from '../../shared/pipes/sar.pipe';
import { InitialsPipe } from '../../shared/pipes/initials.pipe';
import { PagerComponent } from '../../shared/components/pager/pager.component';

@Component({
  selector: 'app-payroll',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, SarPipe, InitialsPipe, PagerComponent],
  templateUrl: './payroll.component.html',
  styleUrls: ['./payroll.component.scss']
})
export class PayrollComponent implements OnInit {

  private payrollService = inject(PayrollService);
  public authService = inject(AuthService);
  public translationService = inject(TranslationService);

  get canManage(): boolean {
    return this.authService.hasPermission('PAYROLL_MANAGE');
  }

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

  getAdjustmentLabel(type: AdjustmentType): string {
    const isAr = this.translationService.currentLang() === 'ar';
    const map: Record<AdjustmentType, { ar: string; en: string }> = {
      'HOUSING_ALLOWANCE': { ar: 'بدل سكن', en: 'Housing Allowance' },
      'REWARD': { ar: 'مكافأة', en: 'Reward' },
      'FUEL_DIFFERENCE': { ar: 'فروقات البنزين', en: 'Fuel Difference' },
      'OTHER_BONUS': { ar: 'إضافة أخرى', en: 'Other Bonus' },
      'SUPPLIER_PENALTY': { ar: 'غرامات المورد', en: 'Supplier Penalty' },
      'LOAN': { ar: 'سلف', en: 'Loan' },
      'OTHER_DEDUCTION': { ar: 'خصم آخر', en: 'Other Deduction' },
    };
    return map[type] ? (isAr ? map[type].ar : map[type].en) : type;
  }

  getStaffAdjustmentLabel(type: StaffAdjustmentType): string {
    const isAr = this.translationService.currentLang() === 'ar';
    const map: Record<StaffAdjustmentType, { ar: string; en: string }> = {
      'REWARD': { ar: 'مكافأة', en: 'Reward' },
      'OTHER_BONUS': { ar: 'إضافة أخرى', en: 'Other Bonus' },
      'SUPPLIER_PENALTY': { ar: 'غرامات المورد', en: 'Supplier Penalty' },
      'LOAN': { ar: 'سلف', en: 'Loan' },
      'OTHER_DEDUCTION': { ar: 'خصم آخر', en: 'Other Deduction' },
    };
    return map[type] ? (isAr ? map[type].ar : map[type].en) : type;
  }

  runs: PayrollRunResponse[] = [];
  selectedRun: PayrollRunResponse | null = null;

  activeTab: 'CAPTAIN' | 'WORKER' | 'MANAGER' = 'CAPTAIN';
  captainTypeFilter: '' | 'EMPLOYEE' | 'FREELANCE' = '';
  lines: PayrollLineResponse[] = [];
  staffLines: StaffPayrollLineResponse[] = [];
  searchQuery = '';

  // Server paging of the lines table (captains and staff share it; switching tab or filters resets it)
  readonly pageSize = PAGE_SIZE;
  linePage = 0;
  lineTotalElements = 0;
  runsPage = 0;
  runsTotalElements = 0;
  runsTotalPages = 0;
  lineTotalPages = 0;
  totals: PayrollLineTotals = PayrollComponent.emptyTotals();
  private readonly search$ = new Subject<void>();



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

  constructor() {
    this.search$.pipe(debounceTime(300), takeUntilDestroyed(inject(DestroyRef))).subscribe(() => {
      this.linePage = 0;
      this.loadData();
    });
  }

  ngOnInit(): void {
    const today = new Date();
    this.periodFrom = isoDate(new Date(today.getFullYear(), today.getMonth(), 1));
    this.periodTo = isoDate(new Date(today.getFullYear(), today.getMonth() + 1, 0));
    this.loadRuns();
  }

  // ---------------------------------------------------------------- data

  loadRuns(selectId?: string): void {
    this.isLoadingRuns = true;
    // A run just generated or reopened is the newest one, on the first page.
    if (selectId) this.runsPage = 0;
    this.payrollService.list(this.runsPage, this.pageSize).subscribe({
      next: (page) => {
        this.isLoadingRuns = false;
        this.runs = page.content;
        this.runsTotalElements = page.totalElements;
        this.runsTotalPages = page.totalPages;

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
        this.errorMessage = errorMessage(err, this.translationService.currentLang() === 'ar' ? 'فشل في تحميل المسيرات' : 'Failed to load payroll runs');
      }
    });
  }

  selectRun(run: PayrollRunResponse): void {
    this.selectedRun = run;
    this.linePage = 0;
    this.confirmingApproval = false;
    this.confirmingDelete = false;
    this.showReopenForm = false;
    this.cancelAdjustment();
    this.cancelStaffAdjustment();
    this.loadData();
  }

  switchTab(tab: 'CAPTAIN' | 'WORKER' | 'MANAGER'): void {
    this.activeTab = tab;
    this.linePage = 0;
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
    this.payrollService.getLines(runId, {
      captainType: this.captainTypeFilter || undefined, search: this.searchQuery.trim() || undefined
    }, this.linePage, this.pageSize).subscribe({
      next: (res) => {
        this.isLoadingLines = false;
        this.lines = res.content;
        this.totals = res.summary;
        this.lineTotalElements = res.totalElements;
        this.lineTotalPages = res.totalPages;
      },
      error: (err) => {
        this.isLoadingLines = false;
        this.lines = [];
        this.errorMessage = errorMessage(err, this.translationService.currentLang() === 'ar' ? 'فشل في تحميل أسطر المسير' : 'Failed to load payroll lines');
      }
    });
  }

  setCaptainTypeFilter(filter: '' | 'EMPLOYEE' | 'FREELANCE'): void {
    this.captainTypeFilter = filter;
    this.linePage = 0;
    if (this.selectedRun && this.activeTab === 'CAPTAIN') {
      this.loadLines(this.selectedRun.id);
    }
  }

  /** Search typed: wait for a pause, then reload the lines from the first page. */
  onSearchInput(): void {
    this.search$.next();
  }

  goToRunsPage(page: number): void {
    this.runsPage = page;
    this.loadRuns();
  }

  goToLinePage(page: number): void {
    this.linePage = page;
    this.loadData();
  }

  private static emptyTotals(): PayrollLineTotals {
    return {
      shifts: 0, orders: 0, basePay: 0, deductions: 0, supplierDeductions: 0, incentives: 0, car: 0, targetBonus: 0,
      extraOrderPay: 0, vacationCommission: 0, gross: 0, activeLoans: 0, adjustments: 0, net: 0, revenue: 0, margin: 0
    };
  }

  loadStaffLines(runId: string, type: string): void {
    this.isLoadingLines = true;
    this.payrollService.getStaffLines(runId, { staffType: type, search: this.searchQuery.trim() || undefined },
      this.linePage, this.pageSize).subscribe({
      next: (res) => {
        this.isLoadingLines = false;
        this.staffLines = res.content;
        this.lineTotalElements = res.totalElements;
        this.lineTotalPages = res.totalPages;
      },
      error: (err) => {
        this.isLoadingLines = false;
        this.staffLines = [];
        this.errorMessage = errorMessage(err, this.translationService.currentLang() === 'ar' ? 'فشل في تحميل أسطر الموظفين' : 'Failed to load staff payroll lines');
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
        this.successMessage = this.translationService.currentLang() === 'ar' ? 'تم توليد المسير بنجاح' : 'Payroll generated successfully';
        this.showGenerateForm = false;
        this.title = '';
        this.loadRuns(run.id);
      },
      error: (err) => {
        this.isGenerating = false;
        this.errorMessage = errorMessage(err, this.translationService.currentLang() === 'ar' ? 'فشل في توليد المسير' : 'Failed to generate payroll');
      }
    });
  }

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
        this.successMessage = this.translationService.currentLang() === 'ar' ? 'تمت إعادة توليد المسير من البيانات المسجلة' : 'Payroll regenerated from recorded data';
        if (this.activeTab === 'CAPTAIN') {
          this.loadLines(updated.id);
        } else {
          this.loadStaffLines(updated.id, this.activeTab);
        }
        this.loadRuns(updated.id);
      },
      error: (err) => {
        this.isGenerating = false;
        this.errorMessage = errorMessage(err, this.translationService.currentLang() === 'ar' ? 'فشل في إعادة توليد المسير' : 'Failed to regenerate payroll');
      }
    });
  }

  approve(): void {
    if (!this.selectedRun) return;
    this.confirmingApproval = false;
    this.runAction(
      this.payrollService.approve(this.selectedRun.id),
      this.translationService.currentLang() === 'ar' ? 'تم اعتماد المسير — لن تتغير أرقامه بعد الآن' : 'Payroll approved — figures locked'
    );
  }

  markPaid(): void {
    if (!this.selectedRun) return;
    this.runAction(this.payrollService.markPaid(this.selectedRun.id), this.translationService.currentLang() === 'ar' ? 'تم تسجيل المسير كمدفوع' : 'Payroll marked as paid');
  }

  reopen(): void {
    if (!this.selectedRun || !this.reopenReason.trim()) return;
    this.showReopenForm = false;
    this.runAction(
      this.payrollService.reopen(this.selectedRun.id, this.reopenReason.trim()),
      this.translationService.currentLang() === 'ar' ? 'تم إعادة فتح المسير كمسودة' : 'Payroll reopened as draft'
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
        this.successMessage = this.translationService.currentLang() === 'ar' ? 'تم حذف المسير' : 'Payroll run deleted';
        this.selectedRun = null;
        this.lines = [];
        this.staffLines = [];
        this.loadRuns();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMessage = errorMessage(err, this.translationService.currentLang() === 'ar' ? 'فشل في حذف المسير' : 'Failed to delete payroll run');
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
        this.errorMessage = errorMessage(err, this.translationService.currentLang() === 'ar' ? 'فشل في تنفيذ العملية' : 'Operation failed');
      }
    });
  }

  export(): void {
    if (!this.selectedRun) return;
    const run = this.selectedRun;
    const tab = this.activeTab;
    const isAr = this.translationService.currentLang() === 'ar';

    let filePrefix = isAr ? 'مسير-رواتب-' : 'payroll-';
    if (tab === 'WORKER') filePrefix = isAr ? 'مسير-رواتب-العاملين-' : 'payroll-workers-';
    else if (tab === 'MANAGER') filePrefix = isAr ? 'مسير-رواتب-الإداريين-' : 'payroll-managers-';
    else if (tab === 'CAPTAIN' && this.captainTypeFilter === 'EMPLOYEE') filePrefix = isAr ? 'مسير-رواتب-KSC-' : 'payroll-KSC-';
    else if (tab === 'CAPTAIN' && this.captainTypeFilter === 'FREELANCE') filePrefix = isAr ? 'مسير-رواتب-KSCFL-' : 'payroll-KSCFL-';

    const captainType = tab === 'CAPTAIN' && this.captainTypeFilter ? this.captainTypeFilter : undefined;
    this.payrollService.exportWorkbook(run.id, tab === 'CAPTAIN' ? undefined : tab, captainType).subscribe({
      next: (blob) => downloadBlob(blob, `${filePrefix}${run.periodFrom}.xlsx`),
      error: () => { this.errorMessage = this.translationService.currentLang() === 'ar' ? 'فشل تحميل ملف المسير' : 'Failed to download payroll file'; }
    });
  }

  isExportingPdf = false;

  exportPdf(): void {
    if (!this.selectedRun) return;
    const run = this.selectedRun;
    const tab = this.activeTab;
    const isAr = this.translationService.currentLang() === 'ar';

    let filePrefix = isAr ? 'مسير-رواتب-' : 'payroll-';
    if (tab === 'WORKER') filePrefix = isAr ? 'مسير-رواتب-العاملين-' : 'payroll-workers-';
    else if (tab === 'MANAGER') filePrefix = isAr ? 'مسير-رواتب-الإداريين-' : 'payroll-managers-';
    else if (tab === 'CAPTAIN' && this.captainTypeFilter === 'EMPLOYEE') filePrefix = isAr ? 'مسير-رواتب-KSC-' : 'payroll-KSC-';
    else if (tab === 'CAPTAIN' && this.captainTypeFilter === 'FREELANCE') filePrefix = isAr ? 'مسير-رواتب-KSCFL-' : 'payroll-KSCFL-';

    const captainType = tab === 'CAPTAIN' && this.captainTypeFilter ? this.captainTypeFilter : undefined;
    this.isExportingPdf = true;
    this.payrollService.exportPdf(run.id, tab === 'CAPTAIN' ? undefined : tab, captainType).subscribe({
      next: (blob) => {
        this.isExportingPdf = false;
        downloadBlob(blob, `${filePrefix}${run.periodFrom}.pdf`);
      },
      error: () => {
        this.isExportingPdf = false;
        this.errorMessage = this.translationService.currentLang() === 'ar' ? 'فشل تحميل ملف PDF للمسير' : 'Failed to download payroll PDF';
      }
    });
  }

  exportPayslip(captainId: string, captainCode: string): void {
    if (!this.selectedRun) return;
    const isAr = this.translationService.currentLang() === 'ar';
    this.payrollService.exportPayslip(this.selectedRun.id, captainId).subscribe({
      next: (blob) => downloadBlob(blob, `${isAr ? 'مسير-فردي-' : 'payslip-'}${captainCode}.pdf`),
      error: () => { this.errorMessage = this.translationService.currentLang() === 'ar' ? 'فشل تحميل المسير الفردي' : 'Failed to download payslip'; }
    });
  }

  exportStaffPayslip(staffId: string, staffCode: string): void {
    if (!this.selectedRun) return;
    const isAr = this.translationService.currentLang() === 'ar';
    this.payrollService.exportStaffPayslip(this.selectedRun.id, staffId).subscribe({
      next: (blob) => downloadBlob(blob, `${isAr ? 'مسير-فردي-' : 'payslip-'}${staffCode}.pdf`),
      error: () => { this.errorMessage = this.translationService.currentLang() === 'ar' ? 'فشل تحميل المسير الفردي للموظف' : 'Failed to download staff payslip'; }
    });
  }

  isExportingBulkPayslips = false;

  exportBulkPayslips(): void {
    if (!this.selectedRun) return;
    this.isExportingBulkPayslips = true;
    const isAr = this.translationService.currentLang() === 'ar';
    this.payrollService.exportBulkPayslips(this.selectedRun.id).subscribe({
      next: (blob) => {
        this.isExportingBulkPayslips = false;
        downloadBlob(blob, `${isAr ? 'مسيرات-فردية-مجمعة-' : 'bulk-payslips-'}${this.selectedRun?.periodFrom}.pdf`);
      },
      error: () => {
        this.isExportingBulkPayslips = false;
        this.errorMessage = this.translationService.currentLang() === 'ar' ? 'فشل تحميل المسيرات الفردية المجمعة' : 'Failed to download bulk payslips';
      }
    });
  }

  exportBulkStaffPayslips(staffType?: string): void {
    if (!this.selectedRun) return;
    this.isExportingBulkPayslips = true;
    const isAr = this.translationService.currentLang() === 'ar';
    this.payrollService.exportBulkStaffPayslips(this.selectedRun.id, staffType).subscribe({
      next: (blob) => {
        this.isExportingBulkPayslips = false;
        const prefix = staffType === 'WORKER' ? (isAr ? 'مسيرات-فردية-العاملين-' : 'bulk-payslips-workers-')
          : staffType === 'MANAGER' ? (isAr ? 'مسيرات-فردية-الإداريين-' : 'bulk-payslips-managers-')
            : (isAr ? 'مسيرات-فردية-الموظفين-' : 'bulk-payslips-staff-');
        downloadBlob(blob, `${prefix}${this.selectedRun?.periodFrom}.pdf`);
      },
      error: () => {
        this.isExportingBulkPayslips = false;
        this.errorMessage = this.translationService.currentLang() === 'ar' ? 'فشل تحميل المسيرات الفردية المجمعة للموظفين' : 'Failed to download bulk staff payslips';
      }
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
        this.successMessage = this.translationService.currentLang() === 'ar' ? 'تمت إضافة البند' : 'Adjustment added';
        this.cancelAdjustment();
        this.refreshRunTotals();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMessage = errorMessage(err, this.translationService.currentLang() === 'ar' ? 'فشل في إضافة البند' : 'Failed to add adjustment');
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
        this.successMessage = this.translationService.currentLang() === 'ar' ? 'تم حذف البند' : 'Adjustment deleted';
        this.refreshRunTotals();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMessage = errorMessage(err, this.translationService.currentLang() === 'ar' ? 'فشل في حذف البند' : 'Failed to delete adjustment');
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
        this.successMessage = this.translationService.currentLang() === 'ar' ? 'تمت إضافة البند للموظف' : 'Staff adjustment added';
        this.cancelStaffAdjustment();
        this.refreshRunTotals();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMessage = errorMessage(err, this.translationService.currentLang() === 'ar' ? 'فشل في إضافة البند' : 'Failed to add adjustment');
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
        this.successMessage = this.translationService.currentLang() === 'ar' ? 'تم حذف البند' : 'Adjustment deleted';
        this.refreshRunTotals();
      },
      error: (err) => {
        this.isBusy = false;
        this.errorMessage = errorMessage(err, this.translationService.currentLang() === 'ar' ? 'فشل في حذف البند' : 'Failed to delete adjustment');
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


  statusLabel(status: string): string {
    const isAr = this.translationService.currentLang() === 'ar';
    const mapAr: Record<string, string> = { DRAFT: 'مسودة', APPROVED: 'معتمد', PAID: 'مدفوع' };
    const mapEn: Record<string, string> = { DRAFT: 'Draft', APPROVED: 'Approved', PAID: 'Paid' };
    return (isAr ? mapAr[status] : mapEn[status]) || status;
  }

  statusClass(status: string): string {
    return { DRAFT: 'badge-warning', APPROVED: 'badge-info', PAID: 'badge-success' }[status] || '';
  }

  typeLabel(captainType: string): string {
    return captainType === 'FREELANCE' ? 'KSCFL' : 'KSC';
  }

  money(value: number | null | undefined): string {
    if (value == null) return '—';
    return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  percent(value: number | null): string {
    return value == null ? '—' : `${(value * 100).toFixed(1)}%`;
  }

}
