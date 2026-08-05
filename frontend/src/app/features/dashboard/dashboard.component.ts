import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
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
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
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

  constructor(private http: HttpClient) { }

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
    this.cards = [
      {
        icon: 'people',
        label: 'الكباتن النشطين',
        value: d.totalActiveCaptains,
        color: '#1B5E20',
        bgColor: '#E8F5E9',
        subtext: `${d.captainsAtTarget} محقق للتارقت`,
        link: '/captains'
      },
      {
        icon: 'work_history',
        label: 'شفتات الفترة',
        value: d.shiftsThisMonth,
        color: '#1565C0',
        bgColor: '#E3F2FD',
        subtext: `${d.shiftsLast7Days} شفت آخر 7 أيام بالفترة`,
        link: '/shifts'
      },
      {
        icon: 'shopping_bag',
        label: 'الطلبات المنفذة',
        value: d.ordersThisMonth.toLocaleString(),
        color: '#2E7D32',
        bgColor: '#E8F5E9',
        subtext: `متوسط ${d.avgOrdersPerShift || 0} طلب/شفت`,
        link: '/reports'
      },
      {
        icon: 'warning',
        label: 'معرفات غير مطابقة',
        value: d.unmatchedNinjaIds,
        color: d.unmatchedNinjaIds > 0 ? '#C62828' : '#2E7D32',
        bgColor: d.unmatchedNinjaIds > 0 ? '#FFEBEE' : '#E8F5E9',
        subtext: `${d.unmatchedShifts} شفت غير مربوط`,
        link: '/suppliers'
      },
      ...this.documentCards(d),
      {
        icon: 'directions_car',
        label: 'السيارات (متاحة / إجمالي)',
        value: `${d.availableVehicles} / ${d.totalVehicles}`,
        color: '#0D47A1',
        bgColor: '#E3F2FD',
        subtext: `استغلال ${d.fleetUtilizationRate || 0}%`,
        link: '/fleet'
      },
      ...(d.lateReturns > 0 ? [{
        icon: 'alarm',
        label: 'تأخيرات إرجاع السيارات',
        value: d.lateReturns,
        color: '#B71C1C',
        bgColor: '#FFEBEE',
        subtext: 'تحتاج متابعة مع الكباتن',
        link: '/fleet'
      }] : []),
      {
        icon: 'account_balance_wallet',
        label: 'العهد النشطة',
        value: d.activeCustodyItems,
        color: '#4A148C',
        bgColor: '#F3E5F5',
        subtext: 'أجهزة وسيارات وبطاقات',
        link: '/custody'
      },
      {
        icon: 'cloud_upload',
        label: 'آخر ملف استيراد',
        value: d.lastImportFile || 'لا يوجد',
        color: '#E65100',
        bgColor: '#FFF3E0',
        subtext: d.lastImportResult || '',
        link: '/imports'
      }
    ];
  }

  private documentCards(d: DashboardData): DashboardCard[] {
    const cards: DashboardCard[] = [];

    if (d.expiredDocuments > 0) {
      cards.push({
        icon: 'event_busy',
        label: 'وثائق منتهية',
        value: d.expiredDocuments,
        color: '#C62828',
        bgColor: '#FFEBEE',
        subtext: 'إجبارية التجديد',
        link: '/captains',
        linkParams: { alert: 'EXPIRED' }
      });
    }
    if (d.expiringDocuments > 0) {
      cards.push({
        icon: 'schedule',
        label: 'وثائق على وشك الانتهاء',
        value: d.expiringDocuments,
        color: '#E65100',
        bgColor: '#FFF3E0',
        subtext: `خلال ${d.currentAlertDays || 30} يوم`,
        link: '/captains',
        linkParams: { alert: 'EXPIRING' }
      });
    }
    if (d.missingDocuments > 0) {
      cards.push({
        icon: 'help_outline',
        label: 'وثائق غير مسجلة',
        value: d.missingDocuments,
        color: '#6A1B9A',
        bgColor: '#F3E5F5',
        subtext: 'بيانات غير مكملة',
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
