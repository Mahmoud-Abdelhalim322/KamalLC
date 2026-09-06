import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, DocumentAlertFilter, DocumentStatus, PageResponse, StaffResponse } from '../../core/models/api.models';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-managers',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './managers.component.html',
  styleUrls: ['./managers.component.scss']
})
export class ManagersComponent implements OnInit {
  staff: StaffResponse[] = [];
  isLoading = true;
  searchQuery = '';
  alertFilter: DocumentAlertFilter | '' = '';

  currentPage = 0;
  pageSize = 20;
  totalElements = 0;
  totalPages = 0;

  constructor(
    private http: HttpClient,
    private route: ActivatedRoute,
    public authService: AuthService
  ) {}

  get canManage(): boolean {
    return this.authService.hasPermission('MEMBERS_MANAGE');
  }

  ngOnInit(): void {
    const alert = this.route.snapshot.queryParamMap.get('alert');
    if (alert === 'ANY' || alert === 'EXPIRED' || alert === 'EXPIRING' || alert === 'MISSING') {
      this.alertFilter = alert;
    }
    this.load();
  }

  load(): void {
    this.isLoading = true;
    let url = `/api/staff?type=MANAGER&page=${this.currentPage}&size=${this.pageSize}`;
    if (this.searchQuery.trim()) {
      url += `&search=${encodeURIComponent(this.searchQuery.trim())}`;
    }
    if (this.alertFilter) {
      url += `&alert=${this.alertFilter}`;
    }

    this.http.get<ApiResponse<PageResponse<StaffResponse>>>(url).subscribe({
      next: (res) => {
        this.isLoading = false;
        if (res.success && res.data) {
          this.staff = res.data.content;
          this.totalElements = res.data.totalElements;
          this.totalPages = res.data.totalPages;
        }
      },
      error: () => {
        this.isLoading = false;
        this.staff = [];
      }
    });
  }

  onSearch(): void {
    this.currentPage = 0;
    this.load();
  }

  setAlertFilter(filter: DocumentAlertFilter | ''): void {
    this.alertFilter = filter;
    this.currentPage = 0;
    this.load();
  }

  toggleAlertFilter(): void {
    this.alertFilter = this.alertFilter ? '' : 'ANY';
    this.currentPage = 0;
    this.load();
  }

  deleteStaff(s: StaffResponse, event: Event): void {
    event.stopPropagation();
    if (!confirm(`هل أنت متأكد من حذف الإداري "${s.fullNameAr}" (كود: ${s.staffCode}) نهائياً؟`)) {
      return;
    }

    this.http.delete<ApiResponse<void>>(`/api/staff/${s.id}`).subscribe({
      next: (res) => {
        if (res.success) {
          this.load();
        } else {
          alert(res.message || 'فشل حذف الإداري');
        }
      },
      error: (err) => {
        alert(err?.error?.message || 'فشل حذف الإداري');
      }
    });
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.load();
    }
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.load();
    }
  }

  getStatusLabel(status: string): string {
    const map: Record<string, string> = { 'ACTIVE': 'نشط', 'INACTIVE': 'غير نشط', 'TERMINATED': 'منتهي' };
    return map[status] || status;
  }

  getStatusClass(status: string): string {
    const map: Record<string, string> = { 'ACTIVE': 'status-active', 'INACTIVE': 'status-inactive', 'TERMINATED': 'status-terminated' };
    return map[status] || '';
  }

  documentAlertLabel(s: StaffResponse): string {
    if (!s.documentAlert) return '—';
    const map: Record<DocumentStatus, string> = {
      'VALID': '—',
      'EXPIRING': 'على وشك الانتهاء',
      'EXPIRED': 'منتهى',
      'MISSING': 'غير مسجل'
    };
    const label = map[s.documentAlert] ?? '—';
    return s.documentAlertCount && s.documentAlertCount > 1 ? `${label} (${s.documentAlertCount})` : label;
  }

  documentAlertClass(s: StaffResponse): string {
    if (!s.documentAlert) return 'no-data';
    const map: Record<DocumentStatus, string> = {
      'VALID': 'no-data',
      'EXPIRING': 'doc-expiring',
      'EXPIRED': 'doc-expired',
      'MISSING': 'doc-missing'
    };
    return map[s.documentAlert] ?? 'no-data';
  }
}
