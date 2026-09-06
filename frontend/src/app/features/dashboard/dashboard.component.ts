import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslationService, TranslatePipe } from '../../core/i18n';
import { ApiResponse, CaptainPerformance, DashboardData } from '../../core/models/api.models';

interface DashboardCard {
  icon: string;
  label: string;
  value: string | number;
  color: string;
  bgColor: string;
  subtext?: string;
  link?: string;
  linkParams?: Record<string, string>;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, TranslatePipe],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  readonly translationService = inject(TranslationService);
  private readonly http = inject(HttpClient);

  data: DashboardData | null = null;
  cards: DashboardCard[] = [];
  hasUnmatched = false;
  expiredDocuments = 0;
  isLoading = true;

  // Date Range Controls
  fromDate: string = '';
  toDate: string = '';
  activePreset: 'THIS_MONTH' | 'LAST_MONTH' | 'LAST_30' | 'CUSTOM' = 'THIS_MONTH';

  // Leaderboard toggles
  leaderboardTimeframe: 'MONTH' | 'WEEK' = 'MONTH';
  leaderboardTab: 'TOP' | 'BOTTOM' = 'TOP';

  // SVG Donut Calculations
  assignedPct = 0;
  availablePct = 0;
  maintenancePct = 0;

  // Fleet SVG Dash offsets
  assignedDash = '0 100';
  availableDash = '0 100';
  maintenanceDash = '0 100';

  ngOnInit(): void {
    // Initialize with current month as default
    const today = new Date();
    const formatDate = (d: Date) => {
      const year = d.getFullYear();
      const month = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    };
    const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
    const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0);
    this.fromDate = formatDate(firstDay);
    this.toDate = formatDate(lastDay);
    this.loadDashboard();
  }

  public loadDashboard(): void {
    this.isLoading = true;
    const params: Record<string, string> = {};
    if (this.fromDate) params['from'] = this.fromDate;
    if (this.toDate) params['to'] = this.toDate;

    this.http.get<ApiResponse<DashboardData>>('/api/dashboard', { params })
      .subscribe({
        next: (res) => {
          this.isLoading = false;
          if (res.success && res.data) {
            this.data = res.data;
            const d = res.data;
            this.hasUnmatched = d.unmatchedNinjaIds > 0 || d.unmatchedShifts > 0;
            this.expiredDocuments = d.expiredDocuments;

            this.calculateFleetChart(d);
            this.buildCards(d);
          }
        },
        error: () => {
          this.isLoading = false;
          this.expiredDocuments = 0;
          this.data = null;
        }
      });
  }

  public applyPreset(preset: 'THIS_MONTH' | 'LAST_MONTH' | 'LAST_30'): void {
    this.activePreset = preset;
    const today = new Date();
    const formatDate = (d: Date) => {
      const year = d.getFullYear();
      const month = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    };

    if (preset === 'THIS_MONTH') {
      const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
      const lastDay = new Date(today.getFullYear(), today.getMonth() + 1, 0);
      this.fromDate = formatDate(firstDay);
      this.toDate = formatDate(lastDay);
    } else if (preset === 'LAST_MONTH') {
      const firstDay = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      const lastDay = new Date(today.getFullYear(), today.getMonth(), 0);
      this.fromDate = formatDate(firstDay);
      this.toDate = formatDate(lastDay);
    } else if (preset === 'LAST_30') {
      const past = new Date();
      past.setDate(today.getDate() - 30);
      this.fromDate = formatDate(past);
      this.toDate = formatDate(today);
    }
    this.loadDashboard();
  }

  public onDateChange(): void {
    this.activePreset = 'CUSTOM';
    this.loadDashboard();
  }

  private calculateFleetChart(d: DashboardData): void {
    const total = d.totalVehicles || 1;
    this.assignedPct = Math.round((d.assignedVehicles / total) * 100);
    this.availablePct = Math.round((d.availableVehicles / total) * 100);
    this.maintenancePct = Math.round((d.maintenanceVehicles / total) * 100);

    // Circumference = 2 * PI * R (R=15.915 => ~100)
    const pAssigned = Math.min(100, Math.max(0, this.assignedPct));
    const pAvailable = Math.min(100 - pAssigned, Math.max(0, this.availablePct));
    const pMaint = Math.max(0, 100 - pAssigned - pAvailable);

    this.assignedDash = `${pAssigned} ${100 - pAssigned}`;
    this.availableDash = `${pAvailable} ${100 - pAvailable}`;
    this.maintenanceDash = `${pMaint} ${100 - pMaint}`;
  }

  private buildCards(d: DashboardData): void {
    const isAr = this.translationService.currentLang() === 'ar';
    this.cards = [
      {
        icon: 'people',
        label: isAr ? 'الكباتن النشطين' : 'Active Captains',
        value: d.totalActiveCaptains,
        color: '#1B5E20',
        bgColor: '#E8F5E9',
        subtext: isAr ? `${d.captainsAtTarget} محقق للتارقت` : `${d.captainsAtTarget} at target`,
        link: '/captains'
      },
      {
        icon: 'work_history',
        label: isAr ? 'شفتات الفترة' : 'Period Shifts',
        value: d.shiftsThisMonth,
        color: '#1565C0',
        bgColor: '#E3F2FD',
        subtext: isAr ? `${d.shiftsLast7Days} شفت آخر 7 أيام بالفترة` : `${d.shiftsLast7Days} shifts last 7 days`,
        link: '/shifts'
      },
      {
        icon: 'shopping_bag',
        label: isAr ? 'الطلبات المنفذة' : 'Fulfilled Orders',
        value: d.ordersThisMonth.toLocaleString(),
        color: '#2E7D32',
        bgColor: '#E8F5E9',
        subtext: isAr ? `متوسط ${d.avgOrdersPerShift || 0} طلب/شفت` : `Avg ${d.avgOrdersPerShift || 0} orders/shift`,
        link: '/reports'
      },
      {
        icon: 'warning',
        label: isAr ? 'معرفات غير مطابقة' : 'Unmatched IDs',
        value: d.unmatchedNinjaIds,
        color: d.unmatchedNinjaIds > 0 ? '#C62828' : '#2E7D32',
        bgColor: d.unmatchedNinjaIds > 0 ? '#FFEBEE' : '#E8F5E9',
        subtext: isAr ? `${d.unmatchedShifts} شفت غير مربوط` : `${d.unmatchedShifts} unlinked shifts`,
        link: '/suppliers'
      },
      ...this.documentCards(d),
      {
        icon: 'directions_car',
        label: isAr ? 'السيارات (متاحة / إجمالي)' : 'Vehicles (Avail / Total)',
        value: `${d.availableVehicles} / ${d.totalVehicles}`,
        color: '#0D47A1',
        bgColor: '#E3F2FD',
        subtext: isAr ? `استغلال ${d.fleetUtilizationRate || 0}%` : `${d.fleetUtilizationRate || 0}% utilization`,
        link: '/fleet'
      },
      ...(d.lateReturns > 0 ? [{
        icon: 'alarm',
        label: isAr ? 'تأخيرات إرجاع السيارات' : 'Overdue Vehicle Returns',
        value: d.lateReturns,
        color: '#B71C1C',
        bgColor: '#FFEBEE',
        subtext: isAr ? 'تحتاج متابعة مع الكباتن' : 'Requires follow-up',
        link: '/fleet'
      }] : []),
      {
        icon: 'account_balance_wallet',
        label: isAr ? 'العهد النشطة' : 'Active Custodies',
        value: d.activeCustodyItems,
        color: '#4A148C',
        bgColor: '#F3E5F5',
        subtext: isAr ? 'أجهزة وسيارات وبطاقات' : 'Devices, vehicles & cards',
        link: '/custody'
      },
      {
        icon: 'cloud_upload',
        label: isAr ? 'آخر ملف استيراد' : 'Last Import File',
        value: d.lastImportFile || (isAr ? 'لا يوجد' : 'None'),
        color: '#E65100',
        bgColor: '#FFF3E0',
        subtext: d.lastImportResult || '',
        link: '/imports'
      }
    ];
  }

  private documentCards(d: DashboardData): DashboardCard[] {
    const isAr = this.translationService.currentLang() === 'ar';
    const cards: DashboardCard[] = [];

    if (d.expiredDocuments > 0) {
      cards.push({
        icon: 'event_busy',
        label: isAr ? 'وثائق منتهية' : 'Expired Docs',
        value: d.expiredDocuments,
        color: '#C62828',
        bgColor: '#FFEBEE',
        subtext: isAr ? 'إجبارية التجديد' : 'Mandatory renewal',
        link: '/captains',
        linkParams: { alert: 'EXPIRED' }
      });
    }
    if (d.expiringDocuments > 0) {
      cards.push({
        icon: 'schedule',
        label: isAr ? 'وثائق على وشك الانتهاء' : 'Expiring Soon Docs',
        value: d.expiringDocuments,
        color: '#E65100',
        bgColor: '#FFF3E0',
        subtext: isAr ? `خلال ${d.currentAlertDays || 30} يوم` : `Within ${d.currentAlertDays || 30} days`,
        link: '/captains',
        linkParams: { alert: 'EXPIRING' }
      });
    }
    if (d.missingDocuments > 0) {
      cards.push({
        icon: 'help_outline',
        label: isAr ? 'وثائق غير مسجلة' : 'Missing Documents',
        value: d.missingDocuments,
        color: '#6A1B9A',
        bgColor: '#F3E5F5',
        subtext: isAr ? 'بيانات غير مكملة' : 'Incomplete data',
        link: '/captains',
        linkParams: { alert: 'MISSING' }
      });
    }
    return cards;
  }

  public get currentLeaderboard(): CaptainPerformance[] {
    if (!this.data) return [];
    if (this.leaderboardTimeframe === 'MONTH') {
      return this.leaderboardTab === 'TOP'
        ? (this.data.topCaptainsThisMonth || [])
        : (this.data.bottomCaptainsThisMonth || []);
    } else {
      return this.leaderboardTab === 'TOP'
        ? (this.data.topCaptainsThisWeek || [])
        : (this.data.bottomCaptainsThisWeek || []);
    }
  }

  public setTimeframe(tf: 'MONTH' | 'WEEK'): void {
    this.leaderboardTimeframe = tf;
  }

  public setTab(tab: 'TOP' | 'BOTTOM'): void {
    this.leaderboardTab = tab;
  }
}
