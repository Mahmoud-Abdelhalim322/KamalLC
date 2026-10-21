import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, AuditLogFilters, AuditLogResponse, SummaryPage } from '../../../core/models/api.models';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { InitialsPipe } from '../../../shared/pipes/initials.pipe';
import { PagerComponent } from '../../../shared/components/pager/pager.component';
import { PAGE_SIZE } from '../../../core/services/api.util';

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, InitialsPipe, PagerComponent],
  templateUrl: './audit-log.component.html',
  styleUrl: './audit-log.component.scss'
})
export class AuditLogComponent implements OnInit {
  logs: AuditLogResponse[] = [];
  loading = true;
  search = '';
  actionFilter = '';
  tenantFilter = '';
  filters: AuditLogFilters = { actions: [], tenants: [] };

  readonly pageSize = PAGE_SIZE;
  page = 0;
  totalElements = 0;
  totalPages = 0;
  private readonly search$ = new Subject<void>();

  constructor(private http: HttpClient) {
    this.search$.pipe(debounceTime(300), takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.applyFilters());
  }

  ngOnInit(): void {
    this.fetchAuditLogs();
  }

  fetchAuditLogs(): void {
    this.loading = true;
    const params: Record<string, string | number> = { page: this.page, size: this.pageSize };
    if (this.actionFilter) params['action'] = this.actionFilter;
    if (this.tenantFilter) params['tenantId'] = this.tenantFilter;
    if (this.search.trim()) params['search'] = this.search.trim();
    this.http.get<ApiResponse<SummaryPage<AuditLogResponse, AuditLogFilters>>>('/api/platform/audit-logs', { params })
      .subscribe({
        next: (res) => {
          if (res.success && res.data) {
            this.logs = res.data.content;
            this.filters = res.data.summary;
            this.totalElements = res.data.totalElements;
            this.totalPages = res.data.totalPages;
          }
          this.loading = false;
        },
        error: () => this.loading = false
      });
  }

  /** A filter changed: back to the first page. */
  applyFilters(): void {
    this.page = 0;
    this.fetchAuditLogs();
  }

  onSearchInput(): void {
    this.search$.next();
  }

  goToPage(page: number): void {
    this.page = page;
    this.fetchAuditLogs();
  }

  /** Badge tone for an audit action, so destructive actions stand out. */
  actionTone(action: string): string {
    if (/DELETE|SUSPEND|DISABLE/.test(action)) return 'danger';
    if (/CREATE|ACTIVATE/.test(action)) return 'success';
    if (/UPDATE|PASSWORD/.test(action)) return 'warning';
    return 'info';
  }
}
