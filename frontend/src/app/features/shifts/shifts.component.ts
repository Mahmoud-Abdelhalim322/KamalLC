import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { ShiftFilter, ShiftResponse } from '../../core/models/api.models';
import { ShiftService } from '../../core/services/shift.service';
import { downloadBlob, errorMessage, isoDate, PAGE_SIZE } from '../../core/services/api.util';

import { AuthService } from '../../core/auth/auth.service';

import { VacationCommissionResponse } from '../../core/models/api.models';

import { TranslationService, TranslatePipe } from '../../core/i18n';
import { InitialsPipe } from '../../shared/pipes/initials.pipe';
import { SarPipe } from '../../shared/pipes/sar.pipe';
import { PagerComponent } from '../../shared/components/pager/pager.component';

type ShiftTab = 'all' | 'attended' | 'absent' | 'excluded' | 'unmatched' | 'vacation-days';

@Component({
  selector: 'app-shifts',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, SarPipe, InitialsPipe, PagerComponent],
  templateUrl: './shifts.component.html',
  styleUrls: ['./shifts.component.scss']
})
export class ShiftsComponent implements OnInit, OnDestroy {

  private shiftService = inject(ShiftService);
  public authService = inject(AuthService);
  public translationService = inject(TranslationService);

  get canManage(): boolean {
    return this.authService.hasPermission('SHIFTS_MANAGE');
  }

  get tabs(): { key: ShiftTab; label: string; icon: string }[] {
    return [
      { key: 'all', label: this.translationService.translate('SHIFTS.TAB_ALL'), icon: 'list' },
      { key: 'attended', label: this.translationService.translate('SHIFTS.TAB_ATTENDED'), icon: 'check_circle' },
      { key: 'absent', label: this.translationService.translate('SHIFTS.TAB_ABSENT'), icon: 'cancel' },
      { key: 'excluded', label: this.translationService.translate('SHIFTS.TAB_EXCLUDED'), icon: 'block' },
      { key: 'unmatched', label: this.translationService.translate('SHIFTS.TAB_UNMATCHED'), icon: 'link_off' },
      { key: 'vacation-days', label: this.translationService.translate('SHIFTS.TAB_VACATION_DAYS'), icon: 'beach_access' },
    ];
  }

  activeTab: ShiftTab = 'all';

  from = '';
  to = '';
  search = '';
  zoneName = '';
  zones: string[] = [];

  shifts: ShiftResponse[] = [];
  vacationCommissions: VacationCommissionResponse[] = [];
  totalVacationCommissionsAmount = 0;
  page = 0;
  size = PAGE_SIZE;
  totalElements = 0;
  totalPages = 0;
  isLoading = true;

  /** The row shown in the side panel, with its calculation broken out. */
  selected: ShiftResponse | null = null;

  isRecalculating = false;
  isExporting = false;
  excludeReason = '';
  showExcludeForm = false;

  deductionExcludeReason = '';
  showDeductionExcludeForm = false;

  errorMessage = '';
  successMessage = '';

  /** Keeps typing in the search box from firing a request per keystroke. */
  private searchInput$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    const today = new Date();
    this.from = isoDate(new Date(today.getFullYear(), today.getMonth(), 1));
    this.to = isoDate(today);

    this.searchInput$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe(() => this.load());

    this.loadZones();
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ---------------------------------------------------------------- data

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.isLoading = true;
    this.errorMessage = '';

    if (this.activeTab === 'vacation-days') {
      this.shiftService.getVacationCommissions({
        from: this.from || undefined,
        to: this.to || undefined,
        search: this.search.trim() || undefined,
        page: this.page,
        size: this.size
      }).subscribe({
        next: (page) => {
          this.isLoading = false;
          this.vacationCommissions = page.content;
          this.totalElements = page.totalElements;
          this.totalPages = page.totalPages;
          this.totalVacationCommissionsAmount = page.content.reduce((sum, item) => sum + (item.amount || 0), 0);
        },
        error: (err) => {
          this.isLoading = false;
          this.errorMessage = errorMessage(err, this.translationService.translate('SHIFTS.LOAD_VACATION_FAILED'));
        }
      });
      return;
    }

    this.shiftService.search(this.buildFilter(), this.page, this.size).subscribe({
      next: (page) => {
        this.isLoading = false;
        this.shifts = page.content;
        this.totalElements = page.totalElements;
        this.totalPages = page.totalPages;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = errorMessage(err, this.translationService.translate('SHIFTS.LOAD_SHIFTS_FAILED'));
      }
    });
  }

  deleteVacationCommission(id: string): void {
    if (!confirm(this.translationService.translate('SHIFTS.DELETE_VACATION_CONFIRM'))) return;
    this.shiftService.deleteVacationCommission(id).subscribe({
      next: () => {
        this.successMessage = this.translationService.translate('SHIFTS.DELETE_VACATION_SUCCESS');
        this.load(false);
      },
      error: (err) => this.errorMessage = errorMessage(err, this.translationService.translate('SHIFTS.DELETE_VACATION_FAILED'))
    });
  }

  /** Each tab is just a preset over the same filter the server already supports. */
  private buildFilter(): ShiftFilter {
    const filter: ShiftFilter = {
      from: this.from || undefined,
      to: this.to || undefined,
      search: this.search.trim() || undefined,
      zoneName: this.zoneName || undefined,
    };

    switch (this.activeTab) {
      case 'attended': return { ...filter, attended: true, excluded: false };
      case 'absent': return { ...filter, attended: false, excluded: false };
      case 'excluded': return { ...filter, excluded: true };
      case 'unmatched': return { ...filter, unmatched: true };
      default: return filter;
    }
  }

  loadZones(): void {
    this.shiftService.getZones().subscribe({
      next: (zones) => { this.zones = zones; }
    });
  }

  onSearchChange(): void {
    this.searchInput$.next(this.search);
  }

  setTab(tab: ShiftTab): void {
    this.activeTab = tab;
    this.selected = null;
    this.load();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.page = page;
    this.load(false);
  }

  // ──────── Shift Detail Modal ────────

  openDetail(shift: ShiftResponse): void {
    this.selected = shift;
    this.showExcludeForm = false;
    this.excludeReason = '';
    this.showDeductionExcludeForm = false;
    this.deductionExcludeReason = '';
  }

  closeDetail(): void {
    this.selected = null;
    this.showExcludeForm = false;
    this.excludeReason = '';
    this.showDeductionExcludeForm = false;
    this.deductionExcludeReason = '';
  }

  // ──────── Shift Exclusion ────────

  excludeCurrent(): void {
    if (!this.selected) return;
    this.applyExclusion(this.selected.id, true, this.excludeReason.trim() || null);
  }

  restoreCurrent(): void {
    if (!this.selected) return;
    this.applyExclusion(this.selected.id, false, null);
  }

  private applyExclusion(id: string, excluded: boolean, reason: string | null): void {
    this.clearMessages();
    this.shiftService.setExcluded(id, excluded, reason).subscribe({
      next: (shift) => {
        this.successMessage = excluded
          ? this.translationService.translate('SHIFTS.EXCLUDE_SHIFT')
          : this.translationService.translate('SHIFTS.INCLUDE_SHIFT');
        this.selected = shift;
        this.showExcludeForm = false;
        this.excludeReason = '';
        this.load(false);
      },
      error: (err) => { this.errorMessage = errorMessage(err, this.translationService.translate('SHIFTS.UPDATE_FAILED')); }
    });
  }

  // ──────── Supplier Deduction Exclusion ────────

  excludeDeduction(): void {
    if (!this.selected || !this.deductionExcludeReason.trim()) return;
    this.clearMessages();
    this.shiftService.excludeDeduction(this.selected.id, this.deductionExcludeReason.trim()).subscribe({
      next: (shift) => {
        this.successMessage = this.translationService.translate('SHIFTS.EXCLUDE_DEDUCTION');
        this.selected = shift;
        this.showDeductionExcludeForm = false;
        this.deductionExcludeReason = '';
        this.load(false);
      },
      error: (err) => { this.errorMessage = errorMessage(err, this.translationService.translate('SHIFTS.UPDATE_FAILED')); }
    });
  }

  restoreDeduction(): void {
    if (!this.selected) return;
    this.clearMessages();
    this.shiftService.restoreDeduction(this.selected.id).subscribe({
      next: (shift) => {
        this.successMessage = this.translationService.translate('SHIFTS.INCLUDE_DEDUCTION');
        this.selected = shift;
        this.load(false);
      },
      error: (err) => { this.errorMessage = errorMessage(err, this.translationService.translate('SHIFTS.UPDATE_FAILED')); }
    });
  }

  /** Re-matches and re-prices the visible period — used after linking a supplier ID. */
  recalculate(): void {
    if (!this.from || !this.to) {
      this.errorMessage = this.translationService.translate('SHIFTS.SET_PERIOD');
      return;
    }
    this.isRecalculating = true;
    this.clearMessages();

    this.shiftService.recalculate(this.from, this.to).subscribe({
      next: (result) => {
        this.isRecalculating = false;
        this.successMessage = this.translationService.translate('SHIFTS.RECALC_RESULT', {
          count: String(result.recalculated),
          linked: String(result.relinked)
        });
        this.load(false);
      },
      error: (err) => {
        this.isRecalculating = false;
        this.errorMessage = errorMessage(err, this.translationService.translate('SHIFTS.RECALC_FAILED'));
      }
    });
  }

  /** Exports every row the current filters match — the file is not limited to this page. */
  export(): void {
    this.isExporting = true;
    this.clearMessages();

    this.shiftService.exportWorkbook(this.buildFilter()).subscribe({
      next: (blob) => {
        this.isExporting = false;
        const prefix = this.translationService.translate('SHIFTS.TITLE');
        const filterName = this.from || this.translationService.translate('COMMON.ALL');
        downloadBlob(blob, `${prefix}-${filterName}.xlsx`);
      },
      error: () => {
        this.isExporting = false;
        this.errorMessage = this.translationService.translate('SHIFTS.EXPORT_FAILED');
      }
    });
  }

  isExportingPdf = false;

  exportPdf(): void {
    this.isExportingPdf = true;
    this.clearMessages();

    this.shiftService.exportPdf(this.buildFilter()).subscribe({
      next: (blob) => {
        this.isExportingPdf = false;
        const prefix = this.translationService.translate('SHIFTS.TITLE');
        const filterName = this.from || this.translationService.translate('COMMON.ALL');
        downloadBlob(blob, `${prefix}-${filterName}.pdf`);
      },
      error: () => {
        this.isExportingPdf = false;
        this.errorMessage = this.translationService.translate('SHIFTS.EXPORT_PDF_FAILED');
      }
    });
  }

  isImportingDeductions = false;

  onSupplierDeductionFileSelected(event: any): void {
    const file: File = event.target.files?.[0];
    if (!file) return;

    this.isImportingDeductions = true;
    this.clearMessages();

    this.shiftService.importSupplierDeductions(file).subscribe({
      next: (res) => {
        this.isImportingDeductions = false;
        this.successMessage = this.translationService.translate('SHIFTS.IMPORT_DEDUCTION_RESULT', {
          matched: String(res.matched),
          unmatched: String(res.unmatched)
        });
        this.load(false);
      },
      error: (err) => {
        this.isImportingDeductions = false;
        this.errorMessage = errorMessage(err, this.translationService.translate('SHIFTS.IMPORT_DEDUCTION_FAILED'));
      }
    });

    event.target.value = '';
  }

  clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }

  // ---------------------------------------------------------------- display

  /** Totals for the rows currently on screen, so the footer matches what is visible. */
  get pageTotals() {
    return this.shifts.reduce((acc, s) => ({
      orders: acc.orders + (s.totalDelivered ?? 0),
      hours: acc.hours + (s.workingHours ?? 0),
      deductions: acc.deductions + s.lateDeduction,
      supplierDeductions: acc.supplierDeductions + (s.supplierDeduction ?? 0),
      incentives: acc.incentives + s.dailyIncentive,
      pay: acc.pay + s.netShiftPay,
      revenue: acc.revenue + s.supplierRevenue + s.supplierExtraRevenue,
    }), { orders: 0, hours: 0, deductions: 0, supplierDeductions: 0, incentives: 0, pay: 0, revenue: 0 });
  }

  rowClass(shift: ShiftResponse): string {
    if (shift.excluded) return 'row-excluded';
    if (!shift.attended) return 'row-absent';
    if (shift.lateDeduction >= shift.dailyWage && shift.dailyWage > 0) return 'row-full-deduction';
    if (shift.lateDeduction > 0) return 'row-deducted';
    return '';
  }

  percent(value: number | null): string {
    return value == null ? '—' : `${(value * 100).toFixed(1)}%`;
  }

  num(value: number | null, digits = 2): string {
    return value == null ? '—' : value.toFixed(digits);
  }

  money(value: number | null): string {
    return value == null ? '—' : value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  time(iso: string | null): string {
    if (!iso) return '—';
    const date = new Date(iso);
    return date.toLocaleTimeString('ar-SA', { hour: '2-digit', minute: '2-digit' });
  }

  formatAttendanceFlag(flag: string | null | undefined): string {
    if (!flag) return '—';
    const lower = flag.toLowerCase().trim();
    if (lower === 'present' || lower === 'attended') return this.translationService.translate('SHIFTS.PRESENT');
    if (lower === 'absent') return this.translationService.translate('SHIFTS.ABSENT');
    if (lower === 'late') return this.translationService.translate('SHIFTS.LATE');
    return flag;
  }

}
