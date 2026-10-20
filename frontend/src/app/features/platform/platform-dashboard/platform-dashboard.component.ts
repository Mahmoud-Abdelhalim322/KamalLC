import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, PlatformDashboardResponse } from '../../../core/models/api.models';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { SarPipe } from '../../../shared/pipes/sar.pipe';

@Component({
  selector: 'app-platform-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslatePipe, SarPipe],
  templateUrl: './platform-dashboard.component.html',
  styleUrl: './platform-dashboard.component.scss'
})
export class PlatformDashboardComponent implements OnInit {
  stats: PlatformDashboardResponse | null = null;
  loading = true;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchDashboard();
  }

  fetchDashboard(): void {
    this.loading = true;
    this.http.get<ApiResponse<PlatformDashboardResponse>>('/api/platform/dashboard')
      .subscribe({
        next: (res) => {
          if (res.success && res.data) {
            this.stats = res.data;
          }
          this.loading = false;
        },
        error: () => this.loading = false
      });
  }

  getActiveSubRate(): number {
    if (!this.stats || !this.stats.totalSubscriptions || this.stats.totalSubscriptions === 0) return 0;
    return Math.round((this.stats.activeSubscriptions / this.stats.totalSubscriptions) * 100);
  }

  getTenantPct(count: number): number {
    if (!this.stats || !this.stats.totalTenants || this.stats.totalTenants === 0) return 0;
    return Math.round((count / this.stats.totalTenants) * 100);
  }
}
