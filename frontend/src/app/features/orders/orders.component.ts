import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrdersDashboardResponse, OrdersCaptainRow } from '../../core/models/api.models';
import { ReportService } from '../../core/services/report.service';
import { downloadBlob, errorMessage, isoDate } from '../../core/services/api.util';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationService } from '../../core/i18n/translation.service';

type CaptainTypeFilter = 'ALL' | 'EMPLOYEE' | 'FREELANCE';
type DisplayMode = 'ORDERS' | 'PERFORMANCE' | 'HOURS';

/** Maps color CSS class to its order range for filtering. */
interface ColorRange {
  colorClass: string;
  min: number;
  max: number;
}

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './orders.component.html',
  styleUrls: ['./orders.component.scss']
})
export class OrdersComponent implements OnInit {

  private reportService = inject(ReportService);
  public i18n = inject(TranslationService);

  from = '';
  to = '';
  captainType: CaptainTypeFilter = 'ALL';
  displayMode: DisplayMode = 'ORDERS';

  data: OrdersDashboardResponse | null = null;
  isLoading = false;
  isExporting = false;
  errorMessage = '';

  /** Active legend color filter — null = show all */
  activeLegendFilter: string | null = null;

  /** Color ranges matching the conditional formatting rules */
  readonly colorRanges: ColorRange[] = [
    { colorClass: 'cell-grey', min: 1, max: 4 },
    { colorClass: 'cell-red', min: 5, max: 8 },
    { colorClass: 'cell-orange', min: 9, max: 12 },
    { colorClass: 'cell-yellow-green', min: 13, max: 18 },
    { colorClass: 'cell-green', min: 19, max: Infinity },
  ];

  ngOnInit(): void {
    const today = new Date();
    // Default to current month start and current date
    this.from = isoDate(new Date(today.getFullYear(), today.getMonth(), 1));
    this.to = isoDate(today);
    this.loadData();
  }

  loadData(): void {
    if (!this.from || !this.to) return;
    this.isLoading = true;
    this.errorMessage = '';

    this.reportService.ordersDashboard(this.from, this.to, this.captainType).subscribe({
      next: (res) => {
        this.isLoading = false;
        this.data = res;
      },
      error: (err) => {
        this.isLoading = false;
        this.data = null;
        this.errorMessage = errorMessage(err, this.i18n.get('ORDERS.LOAD_FAILED'));
      }
    });
  }

  setCaptainType(type: CaptainTypeFilter): void {
    this.captainType = type;
    this.loadData();
  }

  setDisplayMode(mode: DisplayMode): void {
    this.displayMode = mode;
  }

  onPeriodChange(): void {
    this.loadData();
  }

  exportExcel(): void {
    if (!this.from || !this.to) return;
    this.isExporting = true;
    this.errorMessage = '';
    const isAr = this.i18n.currentLang() === 'ar';

    this.reportService.exportOrdersDashboard(this.from, this.to, this.captainType).subscribe({
      next: (blob) => {
        this.isExporting = false;
        downloadBlob(blob, `${isAr ? 'تحقيق-الطلبات-' : 'orders-achievement-'}${this.from}.xlsx`);
      },
      error: (err) => {
        this.isExporting = false;
        this.errorMessage = this.i18n.get('ORDERS.EXPORT_FAILED');
      }
    });
  }

  // --- Legend Filter & Dynamic Computations ---

  toggleLegendFilter(colorClass: string): void {
    this.activeLegendFilter = this.activeLegendFilter === colorClass ? null : colorClass;
  }

  /** Checks if a day's shift orders match the active legend filter (or true if no filter active) */
  isDayMatchingFilter(shiftOrders: number[] | undefined): boolean {
    if (!this.activeLegendFilter) return true;
    const range = this.colorRanges.find(r => r.colorClass === this.activeLegendFilter);
    if (!range) return true;
    const total = this.dayTotal(shiftOrders);
    return total >= range.min && total <= range.max;
  }

  /** Returns filtered captain rows based on active legend color filter */
  get filteredRows(): OrdersCaptainRow[] {
    if (!this.data) return [];
    if (!this.activeLegendFilter) return this.data.captainRows;

    return this.data.captainRows.filter(row => {
      return Object.values(row.dailyOrders).some(shiftOrders => this.isDayMatchingFilter(shiftOrders));
    });
  }

  /** Count of work days for a captain matching the active legend filter */
  getCaptainWorkDays(row: OrdersCaptainRow): number {
    if (!this.activeLegendFilter) return row.workDays;
    return Object.values(row.dailyOrders).filter(shiftOrders => this.isDayMatchingFilter(shiftOrders)).length;
  }

  /** Total actual orders for a captain matching the active legend filter */
  getCaptainActual(row: OrdersCaptainRow): number {
    if (!this.activeLegendFilter) return row.actual;
    return Object.values(row.dailyOrders).reduce((sum, shiftOrders) => {
      if (this.isDayMatchingFilter(shiftOrders)) {
        return sum + this.dayTotal(shiftOrders);
      }
      return sum;
    }, 0);
  }

  /** Achievement rate for a captain matching the active legend filter */
  getCaptainAchievementRate(row: OrdersCaptainRow): number {
    if (!this.activeLegendFilter) return row.achievementRate;
    if (!row.target || row.target <= 0) return 0;
    return this.getCaptainActual(row) / row.target;
  }

  /** Total actual orders across all filtered rows for footer */
  get filteredTotalActual(): number {
    return this.filteredRows.reduce((sum, row) => sum + this.getCaptainActual(row), 0);
  }

  /** Total target across all filtered rows for footer */
  get filteredTotalTarget(): number {
    if (!this.data) return 0;
    return this.filteredRows.length * this.data.target;
  }

  /** Total achievement rate across all filtered rows for footer */
  get filteredTotalRate(): number {
    const target = this.filteredTotalTarget;
    if (target <= 0) return 0;
    return this.filteredTotalActual / target;
  }

  /** Total orders for a specific date column considering the active filter */
  getDailyTotalForDate(dateStr: string): number {
    return this.filteredRows.reduce((sum, row) => {
      const shiftOrders = row.dailyOrders[dateStr];
      if (this.isDayMatchingFilter(shiftOrders)) {
        return sum + this.dayTotal(shiftOrders);
      }
      return sum;
    }, 0);
  }

  /** Count of filtered rows' work days for the active legend filter */
  get filteredWorkDays(): number {
    if (!this.activeLegendFilter) return 0;
    return this.filteredRows.reduce((sum, row) => sum + this.getCaptainWorkDays(row), 0);
  }

  // --- Performance mode helpers ---

  /** Total late deduction across all filtered rows */
  get filteredTotalLateDeduction(): number {
    return this.filteredRows.reduce((sum, row) => sum + (row.totalLateDeduction || 0), 0);
  }

  /** Total extra orders (above 18) across all filtered rows */
  get filteredTotalExtraOrders(): number {
    return this.filteredRows.reduce((sum, row) => sum + (row.totalExtraOrders || 0), 0);
  }

  // --- HOURS mode helpers ---

  /** Sum of all shift hours for a day */
  dayHoursTotal(shiftHours: number[] | undefined): number {
    if (!shiftHours || shiftHours.length === 0) return 0;
    return shiftHours.reduce((sum, v) => sum + v, 0);
  }

  /** Whether a day has multiple shifts (hours mode) */
  isMultiShiftHours(shiftHours: number[] | undefined): boolean {
    return !!shiftHours && shiftHours.length > 1;
  }

  /** Total hours for a specific date column */
  getDailyHoursTotalForDate(dateStr: string): number {
    return this.filteredRows.reduce((sum, row) => {
      const shiftHours = row.dailyHours?.[dateStr];
      return sum + this.dayHoursTotal(shiftHours);
    }, 0);
  }

  /** Total hours across all filtered rows */
  get filteredTotalHours(): number {
    return this.filteredRows.reduce((sum, row) => sum + (row.totalHours || 0), 0);
  }

  // --- Display & Color Helpers ---

  /** Returns 1-31 day number from ISO date string */
  getDayNumber(dateStr: string): number {
    const parts = dateStr.split('-');
    return parts.length === 3 ? parseInt(parts[2], 10) : 0;
  }

  /** Short day name for headers (Arabic or English) */
  getDayName(dateStr: string): string {
    const d = new Date(dateStr);
    const day = d.getDay();
    const isAr = this.i18n.currentLang() === 'ar';
    const daysAr = ['أحد', 'إثن', 'ثلا', 'أرب', 'خمي', 'جمع', 'سبت'];
    const daysEn = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    return (isAr ? daysAr[day] : daysEn[day]) || '';
  }

  /** Backward compatibility */
  getDayNameAr(dateStr: string): string {
    return this.getDayName(dateStr);
  }

  /** Sum of all shift orders for a day */
  dayTotal(shiftOrders: number[] | undefined): number {
    if (!shiftOrders || shiftOrders.length === 0) return 0;
    return shiftOrders.reduce((sum, v) => sum + v, 0);
  }

  /** Whether a day has multiple shifts */
  isMultiShift(shiftOrders: number[] | undefined): boolean {
    return !!shiftOrders && shiftOrders.length > 1;
  }

  /**
   * Color rules matching requirements:
   * 1-4   -> grey
   * 5-8   -> red
   * 9-12  -> orange
   * 13-18 -> yellow-green
   * >=19  -> green
   * 0/null -> blank
   */
  getColorClass(orders: number | undefined | null): string {
    if (!orders || orders <= 0) return 'cell-blank';
    if (orders >= 19) return 'cell-green';
    if (orders >= 13) return 'cell-yellow-green';
    if (orders >= 9) return 'cell-orange';
    if (orders >= 5) return 'cell-red';
    return 'cell-grey';
  }

  /** Color class for a day cell based on the total of all shifts */
  getDayCellColorClass(shiftOrders: number[] | undefined): string {
    return this.getColorClass(this.dayTotal(shiftOrders));
  }

  percent(value: number | null | undefined): string {
    if (value == null) return '0%';
    return `${(value * 100).toFixed(1)}%`;
  }

  hours(value: number | null | undefined): string {
    if (value == null) return '0';
    return value.toFixed(1);
  }

  formatHours(value: number | null | undefined): string {
    if (value == null || value === 0) return '';
    return value.toFixed(1);
  }
}
