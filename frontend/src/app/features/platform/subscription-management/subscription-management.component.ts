import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, SubscriptionResponse } from '../../../core/models/api.models';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { SarPipe } from '../../../shared/pipes/sar.pipe';
import { InitialsPipe } from '../../../shared/pipes/initials.pipe';

type SubFilter = 'ALL' | 'ACTIVE' | 'EXPIRING' | 'EXPIRED';

/** A subscription ending within this many days counts as expiring soon. */
const EXPIRING_DAYS = 30;

@Component({
  selector: 'app-subscription-management',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, SarPipe, InitialsPipe],
  templateUrl: './subscription-management.component.html',
  styleUrl: './subscription-management.component.scss'
})
export class SubscriptionManagementComponent implements OnInit {
  subscriptions: SubscriptionResponse[] = [];
  loading = true;
  filter: SubFilter = 'ALL';
  search = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchSubscriptions();
  }

  fetchSubscriptions(): void {
    this.loading = true;
    this.http.get<ApiResponse<SubscriptionResponse[]>>('/api/platform/subscriptions')
      .subscribe({
        next: (res) => {
          if (res.success && res.data) this.subscriptions = res.data;
          this.loading = false;
        },
        error: () => this.loading = false
      });
  }

  /** Days from today until the subscription ends (negative once it has ended). */
  daysLeft(sub: SubscriptionResponse): number {
    const end = new Date(sub.endDate).getTime();
    return Math.ceil((end - Date.now()) / 86_400_000);
  }

  /** Share of the subscription period already used, 0–100. */
  usedPct(sub: SubscriptionResponse): number {
    const start = new Date(sub.startDate).getTime();
    const end = new Date(sub.endDate).getTime();
    if (!(end > start)) return 100;
    return Math.min(100, Math.max(0, Math.round((Date.now() - start) / (end - start) * 100)));
  }

  isExpiring(sub: SubscriptionResponse): boolean {
    const d = this.daysLeft(sub);
    return sub.status === 'ACTIVE' && d >= 0 && d <= EXPIRING_DAYS;
  }

  get activeCount(): number { return this.subscriptions.filter(s => s.status === 'ACTIVE').length; }
  get expiringCount(): number { return this.subscriptions.filter(s => this.isExpiring(s)).length; }
  get expiredCount(): number { return this.subscriptions.filter(s => s.status !== 'ACTIVE').length; }
  get yearlyCount(): number { return this.subscriptions.filter(s => s.billingCycle === 'YEARLY').length; }
  get autoRenewCount(): number { return this.subscriptions.filter(s => s.autoRenew).length; }
  get totalPaid(): number { return this.subscriptions.reduce((n, s) => n + (s.paidAmount || 0), 0); }

  get visible(): SubscriptionResponse[] {
    const q = this.search.trim().toLowerCase();
    return this.subscriptions.filter(s =>
      (this.filter === 'ALL'
        || (this.filter === 'ACTIVE' && s.status === 'ACTIVE')
        || (this.filter === 'EXPIRING' && this.isExpiring(s))
        || (this.filter === 'EXPIRED' && s.status !== 'ACTIVE'))
      && (!q || s.tenantName.toLowerCase().includes(q) || s.planName.toLowerCase().includes(q)));
  }
}
