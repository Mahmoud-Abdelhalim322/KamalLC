import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, SubscriptionResponse, SummaryPage, SubscriptionListSummary } from '../../../core/models/api.models';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { SarPipe } from '../../../shared/pipes/sar.pipe';
import { InitialsPipe } from '../../../shared/pipes/initials.pipe';
import { PagerComponent } from '../../../shared/components/pager/pager.component';
import { PAGE_SIZE } from '../../../core/services/api.util';


type SubFilter = 'ALL' | 'ACTIVE' | 'EXPIRING' | 'EXPIRED';

/** A subscription ending within this many days counts as expiring soon. */
const EXPIRING_DAYS = 30;

@Component({
  selector: 'app-subscription-management',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, SarPipe, InitialsPipe, PagerComponent],
  templateUrl: './subscription-management.component.html',
  styleUrl: './subscription-management.component.scss'
})
export class SubscriptionManagementComponent implements OnInit {
  subscriptions: SubscriptionResponse[] = [];
  summary: SubscriptionListSummary | null = null;
  loading = true;

  readonly pageSize = PAGE_SIZE;
  page = 0;
  totalElements = 0;
  totalPages = 0;
  private readonly search$ = new Subject<void>();
  filter: SubFilter = 'ALL';
  search = '';

  constructor(private http: HttpClient) {
    this.search$.pipe(debounceTime(300), takeUntilDestroyed(inject(DestroyRef))).subscribe(() => {
      this.page = 0;
      this.fetchSubscriptions();
    });
  }

  ngOnInit(): void {
    this.fetchSubscriptions();
  }
  setFilter(filter: typeof this.filter): void {
    this.filter = filter;
    this.page = 0;
    this.fetchSubscriptions();
  }

  onSearchInput(): void {
    this.search$.next();
  }

  goToPage(page: number): void {
    this.page = page;
    this.fetchSubscriptions();
  }


  fetchSubscriptions(): void {
    this.loading = true;
    this.http.get<ApiResponse<SummaryPage<SubscriptionResponse, SubscriptionListSummary>>>('/api/platform/subscriptions', {
      params: { page: this.page, size: this.pageSize, filter: this.filter, search: this.search.trim() }
    })
      .subscribe({
        next: (res) => {
          if (res.success && res.data) {
            this.subscriptions = res.data.content;
            this.summary = res.data.summary;
            this.totalElements = res.data.totalElements;
            this.totalPages = res.data.totalPages;
          }
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

}
