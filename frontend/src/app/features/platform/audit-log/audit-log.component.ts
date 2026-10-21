import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, AuditLogResponse } from '../../../core/models/api.models';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { InitialsPipe } from '../../../shared/pipes/initials.pipe';

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, InitialsPipe],
  templateUrl: './audit-log.component.html',
  styleUrl: './audit-log.component.scss'
})
export class AuditLogComponent implements OnInit {
  logs: AuditLogResponse[] = [];
  loading = true;
  search = '';
  actionFilter = '';
  tenantFilter = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchAuditLogs();
  }

  fetchAuditLogs(): void {
    this.loading = true;
    this.http.get<ApiResponse<AuditLogResponse[]>>('/api/platform/audit-logs')
      .subscribe({
        next: (res) => {
          if (res.success && res.data) this.logs = res.data;
          this.loading = false;
        },
        error: () => this.loading = false
      });
  }

  get actions(): string[] { return [...new Set(this.logs.map(l => l.action).filter(Boolean))].sort(); }
  get tenantNames(): string[] { return [...new Set(this.logs.map(l => l.tenantName).filter((t): t is string => !!t))].sort(); }

  get visible(): AuditLogResponse[] {
    const q = this.search.trim().toLowerCase();
    return this.logs.filter(l =>
      (!this.actionFilter || l.action === this.actionFilter)
      && (!this.tenantFilter || l.tenantName === this.tenantFilter)
      && (!q || (l.actorUsername || '').toLowerCase().includes(q) || (l.details || '').toLowerCase().includes(q)));
  }

  /** Badge tone for an audit action, so destructive actions stand out. */
  actionTone(action: string): string {
    if (/DELETE|SUSPEND|DISABLE/.test(action)) return 'danger';
    if (/CREATE|ACTIVATE/.test(action)) return 'success';
    if (/UPDATE|PASSWORD/.test(action)) return 'warning';
    return 'info';
  }
}
