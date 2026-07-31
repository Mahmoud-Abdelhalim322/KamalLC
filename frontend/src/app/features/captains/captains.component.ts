import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import {
  ApiResponse, CaptainResponse, DocumentAlertFilter, DocumentStatus, PageResponse
} from '../../core/models/api.models';

@Component({
  selector: 'app-captains',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './captains.component.html',
  styleUrls: ['./captains.component.scss']
})
export class CaptainsComponent implements OnInit {
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

  constructor(private http: HttpClient, private route: ActivatedRoute) {}

  ngOnInit(): void {
    // The dashboard tiles link here with ?alert=..., so the filter has to survive the jump.
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
    // The alert filter is server-side by id, so it cannot be combined with a type filter.
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
    return type === 'EMPLOYEE' ? 'كفالة' : 'فريلانس';
  }

  getTypeClass(type: string): string {
    return type === 'EMPLOYEE' ? 'type-employee' : 'type-freelance';
  }

  getStatusLabel(status: string): string {
    const map: Record<string, string> = { 'ACTIVE': 'نشط', 'INACTIVE': 'غير نشط', 'TERMINATED': 'منتهي' };
    return map[status] || status;
  }

  getStatusClass(status: string): string {
    const map: Record<string, string> = { 'ACTIVE': 'status-active', 'INACTIVE': 'status-inactive', 'TERMINATED': 'status-terminated' };
    return map[status] || '';
  }

  /**
   * The badge text: the worst thing this captain's documents say, with a count when more
   * than one of them says it. A valid set shows a dash, not a green badge — the column is
   * there to be scanned for problems.
   */
  documentAlertLabel(captain: CaptainResponse): string {
    const map: Record<DocumentStatus, string> = {
      'VALID': '—',
      'EXPIRING': 'على وشك الانتهاء',
      'EXPIRED': 'منتهى',
      'MISSING': 'غير مسجل'
    };
    const label = map[captain.documentAlert] ?? '—';
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
}
