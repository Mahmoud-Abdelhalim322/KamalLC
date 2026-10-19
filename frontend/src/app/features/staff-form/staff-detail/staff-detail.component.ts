import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, StaffDetailResponse } from '../../../core/models/api.models';
import { DocumentGalleryComponent } from '../../../shared/components/document-gallery/document-gallery.component';
import { TranslationService } from '../../../core/i18n/translation.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

@Component({
  selector: 'app-staff-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, DocumentGalleryComponent, TranslatePipe],
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
    private router: Router,
    public translationService: TranslationService
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
    const isManager = this.staff?.staffType === 'MANAGER';
    return this.translationService.translate(isManager ? 'STAFF_DETAIL.TYPE_MANAGER' : 'STAFF_DETAIL.TYPE_WORKER');
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
    const type = this.typeLabel;
    const confirmMsg = this.translationService.translate('STAFF_DETAIL.CONFIRM_DELETE', {
      type,
      name: this.staff.fullNameAr
    });
    if (!confirm(confirmMsg)) return;

    this.http.delete<ApiResponse<void>>(`/api/staff/${this.staffId}`).subscribe({
      next: (res) => {
        if (res.success) {
          this.router.navigate([this.backRoute]);
        } else {
          alert(res.message || this.translationService.translate('STAFF_DETAIL.DELETE_FAILED'));
        }
      },
      error: (err) => {
        alert(err?.error?.message || this.translationService.translate('STAFF_DETAIL.DELETE_FAILED'));
      }
    });
  }

  get hasAlerts(): boolean {
    return !!this.staff?.worstDocumentStatus && this.staff.worstDocumentStatus !== 'VALID';
  }

  docInfo(kind: string) {
    return this.staff?.documentStatuses?.find(d => d.kind === kind);
  }

  getDocStatusLabel(doc: any): string {
    if (!doc) return '';
    if (this.translationService.currentLang() === 'ar') {
      return doc.statusLabelAr;
    }
    const key = 'DOC_STATUS.' + doc.status;
    const trans = this.translationService.translate(key);
    return trans !== key ? trans : doc.statusLabelAr;
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
}
