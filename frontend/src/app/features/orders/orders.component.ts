import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrdersDashboardResponse } from '../../core/models/api.models';
import { ReportService } from '../../core/services/report.service';
import { downloadBlob, errorMessage, isoDate } from '../../core/services/api.util';

type CaptainTypeFilter = 'ALL' | 'EMPLOYEE' | 'FREELANCE';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './orders.component.html',
  styleUrls: ['./orders.component.scss']
})
export class OrdersComponent implements OnInit {

  private reportService = inject(ReportService);

  from = '';
  to = '';
  captainType: CaptainTypeFilter = 'ALL';

  data: OrdersDashboardResponse | null = null;
  isLoading = false;
  isExporting = false;
  errorMessage = '';

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
        this.errorMessage = errorMessage(err, 'فشل في تحميل لوحة تحكم تحقيق الطلبات');
      }
    });
  }

  setCaptainType(type: CaptainTypeFilter): void {
    this.captainType = type;
    this.loadData();
  }

  onPeriodChange(): void {
    this.loadData();
  }

  exportExcel(): void {
    if (!this.from || !this.to) return;
    this.isExporting = true;
    this.errorMessage = '';

    this.reportService.exportOrdersDashboard(this.from, this.to, this.captainType).subscribe({
      next: (blob) => {
        this.isExporting = false;
        downloadBlob(blob, `تحقيق-الطلبات-${this.from}.xlsx`);
      },
      error: (err) => {
        this.isExporting = false;
        this.errorMessage = 'فشل في تصدير ملف الإكسل';
      }
    });
  }

  // --- Display & Color Helpers ---

  /** Returns 1-31 day number from ISO date string */
  getDayNumber(dateStr: string): number {
    const parts = dateStr.split('-');
    return parts.length === 3 ? parseInt(parts[2], 10) : 0;
  }

  /** Short Arabic day name for headers */
  getDayNameAr(dateStr: string): string {
    const d = new Date(dateStr);
    const day = d.getDay();
    const days = ['أحد', 'إثن', 'ثلا', 'أرب', 'خمي', 'جمع', 'سبت'];
    return days[day] || '';
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

  percent(value: number | null | undefined): string {
    if (value == null) return '0%';
    return `${(value * 100).toFixed(1)}%`;
  }
}
