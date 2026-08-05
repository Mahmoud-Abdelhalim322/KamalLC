import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { ShiftFilter, ShiftResponse } from '../../core/models/api.models';
import { ShiftService } from '../../core/services/shift.service';
import { downloadBlob, errorMessage, isoDate } from '../../core/services/api.util';

import { AuthService } from '../../core/auth/auth.service';

type ShiftTab = 'all' | 'attended' | 'absent' | 'excluded' | 'unmatched';

@Component({
  selector: 'app-shifts',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './shifts.component.html',
  styleUrls: ['./shifts.component.scss']
})
export class ShiftsComponent implements OnInit, OnDestroy {

  private shiftService = inject(ShiftService);
  public authService = inject(AuthService);

  get canManage(): boolean {
    return this.authService.hasPermission('SHIFTS_MANAGE');
  }

  readonly tabs: { key: ShiftTab; label: string; icon: string }[] = [
    { key: 'all', label: 'الكل', icon: 'list' },
    { key: 'attended', label: 'حضور', icon: 'check_circle' },
    { key: 'absent', label: 'غياب', icon: 'cancel' },
    { key: 'excluded', label: 'مستبعد', icon: 'block' },
    { key: 'unmatched', label: 'غير مربوط', icon: 'link_off' },
  ];

  activeTab: ShiftTab = 'all';

  from = '';
  to = '';
  search = '';
  zoneName = '';
  zones: string[] = [];

  shifts: ShiftResponse[] = [];
  page = 0;
  size = 50;
  totalElements = 0;
  totalPages = 0;
  isLoading = true;

  /** The row shown in the side panel, with its calculation broken out. */
  selected: ShiftResponse | null = null;

  isRecalculating = false;
  isExporting = false;
  excludeReason = '';
  showExcludeForm = false;

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

    this.shiftService.search(this.buildFilter(), this.page, this.size).subscribe({
      next: (page) => {
        this.isLoading = false;
        this.shifts = page.content;
        this.totalElements = page.totalElements;
        this.totalPages = page.totalPages;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = errorMessage(err, 'فشل في تحميل الشيفتات');
      }
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

  // ---------------------------------------------------------------- actions

  select(shift: ShiftResponse): void {
    this.selected = shift;
    this.showExcludeForm = false;
    this.excludeReason = '';
  }

  closePanel(): void {
    this.selected = null;
    this.showExcludeForm = false;
  }

  exclude(): void {
    if (!this.selected || !this.excludeReason.trim()) return;
    this.applyExclusion(this.selected.id, true, this.excludeReason.trim());
  }

  include(): void {
    if (!this.selected) return;
    this.applyExclusion(this.selected.id, false, null);
  }

  private applyExclusion(id: string, excluded: boolean, reason: string | null): void {
    this.clearMessages();
    this.shiftService.setExcluded(id, excluded, reason).subscribe({
      next: (shift) => {
        this.successMessage = excluded ? 'تم استبعاد الشفت' : 'تم إرجاع الشفت للحساب';
        this.selected = shift;
        this.showExcludeForm = false;
        this.excludeReason = '';
        this.load(false);
      },
      error: (err) => { this.errorMessage = errorMessage(err, 'فشل في التحديث'); }
    });
  }

  /** Re-matches and re-prices the visible period — used after linking a supplier ID. */
  recalculate(): void {
    if (!this.from || !this.to) {
      this.errorMessage = 'حدّد الفترة أولًا';
      return;
    }
    this.isRecalculating = true;
    this.clearMessages();

    this.shiftService.recalculate(this.from, this.to).subscribe({
      next: (result) => {
        this.isRecalculating = false;
        this.successMessage =
          `تمت إعادة حساب ${result.recalculated} شفت (${result.relinked} تم ربطها بكابتن)`;
        this.load(false);
      },
      error: (err) => {
        this.isRecalculating = false;
        this.errorMessage = errorMessage(err, 'فشل في إعادة الحساب');
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
        downloadBlob(blob, `الشيفتات-${this.from || 'الكل'}.xlsx`);
      },
      error: () => {
        this.isExporting = false;
        this.errorMessage = 'فشل تحميل ملف الشيفتات';
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
        downloadBlob(blob, `تقرير-الشيفتات-${this.from || 'الكل'}.pdf`);
      },
      error: () => {
        this.isExportingPdf = false;
        this.errorMessage = 'فشل تحميل ملف PDF للشيفتات';
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
        this.successMessage = `تم استيراد ${res.matched} خصم مورد (${res.unmatched} غير مطابق)`;
        this.load(false);
      },
      error: (err) => {
        this.isImportingDeductions = false;
        this.errorMessage = errorMessage(err, 'فشل في استيراد خصومات المورد');
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

}
