import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import {
  ApiResponse, CaptainResponse, DocumentAlertFilter, DocumentStatus, PageResponse
} from '../../core/models/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { TranslationService, TranslatePipe } from '../../core/i18n';
import { SarPipe } from '../../shared/pipes/sar.pipe';

@Component({
  selector: 'app-captains',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, TranslatePipe, SarPipe],
  templateUrl: './captains.component.html',
  styleUrls: ['./captains.component.scss']
})
export class CaptainsComponent implements OnInit {
  readonly translationService = inject(TranslationService);
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  readonly authService = inject(AuthService);

  captains: CaptainResponse[] = [];
  isLoading = true;
  searchQuery = '';
  activeFilter = '';

  /** Set when the list is narrowed to captains whose documents raised an alert. */
  alertFilter: DocumentAlertFilter | '' = '';

  // Pagination
  currentPage = 0;
  pageSize = 20;
  totalElements = 0;
  totalPages = 0;

  // Bulk selection
  selectedIds = new Set<string>();

  get canManage(): boolean {
    return this.authService.hasPermission('MEMBERS_MANAGE');
  }

  get allSelected(): boolean {
    return this.captains.length > 0 && this.captains.every(c => this.selectedIds.has(c.id));
  }

  ngOnInit(): void {
    const alert = this.route.snapshot.queryParamMap.get('alert');
    if (alert === 'ANY' || alert === 'EXPIRED' || alert === 'EXPIRING' || alert === 'MISSING') {
      this.alertFilter = alert;
    }
    this.loadCaptains();
  }

  loadCaptains(): void {
    this.isLoading = true;
    let url = `/api/captains?page=${this.currentPage}&size=${this.pageSize}`;
    if (this.searchQuery.trim()) {
      url += `&search=${encodeURIComponent(this.searchQuery.trim())}`;
    }
    if (this.activeFilter) {
      url += `&type=${this.activeFilter}`;
    }
    if (this.alertFilter) {
      url += `&alert=${this.alertFilter}`;
    }

    this.http.get<ApiResponse<PageResponse<CaptainResponse>>>(url)
      .subscribe({
        next: (res) => {
          this.isLoading = false;
          if (res.success && res.data) {
            this.captains = res.data.content;
            this.totalElements = res.data.totalElements;
            this.totalPages = res.data.totalPages;
          }
        },
        error: () => {
          this.isLoading = false;
          this.captains = [];
        }
      });
  }

  onSearch(): void {
    this.currentPage = 0;
    this.loadCaptains();
  }

  setFilter(type: string): void {
    this.activeFilter = type;
    this.alertFilter = '';
    this.currentPage = 0;
    this.loadCaptains();
  }

  toggleAlertFilter(): void {
    this.alertFilter = this.alertFilter ? '' : 'ANY';
    this.activeFilter = '';
    this.currentPage = 0;
    this.loadCaptains();
  }

  // ---------- Selection ----------

  toggleSelectAll(): void {
    if (this.allSelected) {
      this.captains.forEach(c => this.selectedIds.delete(c.id));
    } else {
      this.captains.forEach(c => this.selectedIds.add(c.id));
    }
  }

  toggleSelect(captainId: string, event: Event): void {
    event.stopPropagation();
    if (this.selectedIds.has(captainId)) {
      this.selectedIds.delete(captainId);
    } else {
      this.selectedIds.add(captainId);
    }
  }

  isSelected(captainId: string): boolean {
    return this.selectedIds.has(captainId);
  }

  deleteSelected(): void {
    if (this.selectedIds.size === 0) return;
    const confirmMsg = this.translationService.translate('CAPTAINS.CONFIRM_BULK_DELETE', { count: String(this.selectedIds.size) });
    if (!confirm(confirmMsg)) return;

    this.http.request<ApiResponse<any>>('DELETE', '/api/captains/bulk', {
      body: Array.from(this.selectedIds)
    }).subscribe({
      next: (res) => {
        this.selectedIds.clear();
        this.loadCaptains();
        alert(res.message || this.translationService.translate('CAPTAINS.DELETE_SUCCESS'));
      },
      error: (err) => {
        alert(err?.error?.message || this.translationService.translate('CAPTAINS.BULK_DELETE_FAILED'));
      }
    });
  }

  deleteCaptain(captain: CaptainResponse, event: Event): void {
    event.stopPropagation();
    const confirmMsg = this.translationService.translate('CAPTAINS.CONFIRM_DELETE', {
      name: captain.fullNameAr,
      code: captain.kamalCode
    });

    if (!confirm(confirmMsg)) {
      return;
    }

    this.http.delete<ApiResponse<void>>(`/api/captains/${captain.id}`)
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.loadCaptains();
          } else {
            alert(res.message || this.translationService.translate('CAPTAINS.DELETE_FAILED'));
          }
        },
        error: (err) => {
          alert(err?.error?.message || this.translationService.translate('CAPTAINS.DELETE_FAILED'));
        }
      });
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.loadCaptains();
    }
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadCaptains();
    }
  }

  getTypeLabel(type: string): string {
    return type === 'EMPLOYEE' ? 'KSC' : 'KSCFL';
  }

  getTypeClass(type: string): string {
    return type === 'EMPLOYEE' ? 'type-employee' : 'type-freelance';
  }

  getStatusLabel(status: string): string {
    const key = 'CAPTAINS.STATUS_' + status;
    const trans = this.translationService.translate(key);
    return trans !== key ? trans : status;
  }

  getStatusClass(status: string): string {
    const map: Record<string, string> = { 'ACTIVE': 'status-active', 'INACTIVE': 'status-inactive', 'TERMINATED': 'status-terminated' };
    return map[status] || '';
  }

  documentAlertLabel(captain: CaptainResponse): string {
    if (captain.documentAlert === 'VALID') return '—';
    const key = 'DOC_STATUS.' + captain.documentAlert;
    const label = this.translationService.translate(key);
    return captain.documentAlertCount > 1 ? `${label} (${captain.documentAlertCount})` : label;
  }

  documentAlertClass(captain: CaptainResponse): string {
    const map: Record<DocumentStatus, string> = {
      'VALID': 'no-data',
      'EXPIRING': 'doc-expiring',
      'EXPIRED': 'doc-expired',
      'MISSING': 'doc-missing'
    };
    return map[captain.documentAlert] ?? 'no-data';
  }

  // ── Export ──

  isExporting = false;
  isExportingPdf = false;

  exportExcel(): void {
    this.isExporting = true;
    const params: Record<string, string> = {};
    if (this.activeFilter) params['type'] = this.activeFilter;

    this.http.get('/api/captains/export/excel', { params, responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.isExporting = false;
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        const filterName = this.activeFilter ? this.activeFilter : this.translationService.translate('COMMON.ALL');
        const prefix = this.translationService.translate('CAPTAINS.TITLE');
        a.download = `${prefix}-${filterName}.xlsx`;
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => {
        this.isExporting = false;
      }
    });
  }

  exportPdf(): void {
    this.isExportingPdf = true;
    const params: Record<string, string> = {};
    if (this.activeFilter) params['type'] = this.activeFilter;

    this.http.get('/api/captains/export/pdf', { params, responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.isExportingPdf = false;
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        const filterName = this.activeFilter ? this.activeFilter : this.translationService.translate('COMMON.ALL');
        const prefix = this.translationService.translate('CAPTAINS.TITLE');
        a.download = `${prefix}-${filterName}.pdf`;
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => {
        this.isExportingPdf = false;
      }
    });
  }
}
