import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, StaffDetailResponse } from '../../../core/models/api.models';
import { DocumentGalleryComponent } from '../../../shared/components/document-gallery/document-gallery.component';

@Component({
  selector: 'app-staff-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, DocumentGalleryComponent],
  templateUrl: './staff-detail.component.html',
  styleUrls: ['./staff-detail.component.scss']
})
export class StaffDetailComponent implements OnInit {
  staff: StaffDetailResponse | null = null;
  isLoading = true;
  staffId!: string;

  constructor(
    private http: HttpClient,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.staffId = this.route.snapshot.paramMap.get('id')!;
    this.load();
  }

  get backRoute(): string {
    return this.staff?.staffType === 'MANAGER' ? '/managers' : '/workers';
  }

  get editRoute(): string[] {
    const base = this.staff?.staffType === 'MANAGER' ? '/managers' : '/workers';
    return [base, this.staffId, 'edit'];
  }

  get typeLabel(): string {
    return this.staff?.staffType === 'MANAGER' ? 'إداري' : 'عامل';
  }

  load(): void {
    this.isLoading = true;
    this.http.get<ApiResponse<StaffDetailResponse>>(`/api/staff/${this.staffId}`).subscribe({
      next: (res) => {
        this.isLoading = false;
        if (res.success && res.data) this.staff = res.data;
      },
      error: () => { this.isLoading = false; }
    });
  }

  deleteStaff(): void {
    if (!this.staff) return;
    const label = this.staff.staffType === 'MANAGER' ? 'الإداري' : 'العامل';
    if (!confirm(`هل أنت متأكد من حذف ${label} "${this.staff.fullNameAr}" نهائياً؟`)) return;

    this.http.delete<ApiResponse<void>>(`/api/staff/${this.staffId}`).subscribe({
      next: (res) => {
        if (res.success) {
          this.router.navigate([this.backRoute]);
        } else {
          alert(res.message || 'فشل حذف البيانات');
        }
      },
      error: (err) => {
        alert(err?.error?.message || 'فشل حذف البيانات');
      }
    });
  }

  get hasAlerts(): boolean {
    return !!this.staff?.worstDocumentStatus && this.staff.worstDocumentStatus !== 'VALID';
  }

  docInfo(kind: string) {
    return this.staff?.documentStatuses?.find(d => d.kind === kind);
  }

  getStatusLabel(status: string): string {
    const map: Record<string, string> = { 'ACTIVE': 'نشط', 'INACTIVE': 'غير نشط', 'TERMINATED': 'منتهي' };
    return map[status] || status;
  }

  getStatusClass(status: string): string {
    const map: Record<string, string> = { 'ACTIVE': 'status-active', 'INACTIVE': 'status-inactive', 'TERMINATED': 'status-terminated' };
    return map[status] || '';
  }
}
