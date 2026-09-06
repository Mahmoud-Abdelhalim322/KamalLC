import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, AuditLogResponse } from '../../../core/models/api.models';

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './audit-log.component.html',
  styleUrl: './audit-log.component.scss'
})
export class AuditLogComponent implements OnInit {
  logs: AuditLogResponse[] = [];
  loading = true;

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
}
