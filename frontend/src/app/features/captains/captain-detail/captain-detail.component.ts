import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { isoDate } from '../../../core/services/api.util';
import {
  ApiResponse, AssignmentRequest, CaptainDetailResponse, DocumentStatus, DocumentStatusInfo,
  SupplierIdInfo, SupplierIdSlotResponse, SupplierResponse, UnassignRequest
} from '../../../core/models/api.models';
import { DocumentGalleryComponent } from '../../../shared/components/document-gallery/document-gallery.component';

@Component({
  selector: 'app-captain-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, DocumentGalleryComponent],
  templateUrl: './captain-detail.component.html',
  styleUrls: ['./captain-detail.component.scss']
})
export class CaptainDetailComponent implements OnInit {
  captain: CaptainDetailResponse | null = null;
  isLoading = true;
  errorMessage = '';

  // Linking supplier IDs
  showLinkModal = false;
  suppliers: SupplierResponse[] = [];
  linkSupplierId = '';
  slotQuery = '';
  slotResults: SupplierIdSlotResponse[] = [];
  selectedSlot: SupplierIdSlotResponse | null = null;
  linkValidFrom = isoDate();
  linkSideName = '';
  isSearchingSlots = false;
  isLinking = false;
  linkError = '';
  idError = '';
  private slotSearch$ = new Subject<string>();

  constructor(
    private http: HttpClient,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  deleteCaptain(): void {
    if (!this.captain) return;
    if (!confirm(`هل أنت تأكد من حذف الكابتن "${this.captain.fullNameAr}" (كود: ${this.captain.kamalCode}) نهائياً؟`)) {
      return;
    }

    this.http.delete<ApiResponse<void>>(`/api/captains/${this.captain.id}`)
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.router.navigate(['/captains']);
          } else {
            alert(res.message || 'فشل حذف الكابتن');
          }
        },
        error: (err) => {
          alert(err?.error?.message || 'فشل حذف الكابتن');
        }
      });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadCaptain(id);
    }

    this.slotSearch$
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(query => this.searchSlots(query));
  }

  private loadCaptain(id: string): void {
    this.http.get<ApiResponse<CaptainDetailResponse>>(`/api/captains/${id}`)
      .subscribe({
        next: (res) => {
          this.isLoading = false;
          if (res.success && res.data) {
            this.captain = res.data;
          } else {
            this.errorMessage = 'فشل في تحميل بيانات الكابتن';
          }
        },
        error: () => {
          this.isLoading = false;
          this.errorMessage = 'فشل في تحميل بيانات الكابتن';
        }
      });
  }

  // ---------- Linking supplier IDs ----------

  openLinkModal(): void {
    this.showLinkModal = true;
    this.linkSupplierId = '';
    this.slotQuery = '';
    this.slotResults = [];
    this.selectedSlot = null;
    this.linkSideName = '';
    this.linkValidFrom = isoDate();
    this.linkError = '';

    if (this.suppliers.length === 0) {
      this.http.get<ApiResponse<SupplierResponse[]>>('/api/supplier-ids/suppliers')
        .subscribe({
          next: (res) => {
            this.suppliers = (res.success && res.data) ? res.data : [];
            this.searchSlots(this.slotQuery);
          },
          error: () => { this.suppliers = []; }
        });
    } else {
      this.searchSlots('');
    }
  }

  closeLinkModal(): void {
    this.showLinkModal = false;
  }

  onSlotQueryChange(query: string): void {
    this.selectedSlot = null;
    this.slotSearch$.next(query);
  }

  onLinkSupplierChange(): void {
    this.selectedSlot = null;
    this.searchSlots(this.slotQuery);
  }

  private searchSlots(query: string): void {
    this.isSearchingSlots = true;

    let url = `/api/supplier-ids/slots/lookup?onlyAvailable=true&q=${encodeURIComponent(query.trim())}`;
    if (this.linkSupplierId) {
      url += `&supplierId=${this.linkSupplierId}`;
    }

    this.http.get<ApiResponse<SupplierIdSlotResponse[]>>(url)
      .subscribe({
        next: (res) => {
          this.isSearchingSlots = false;
          this.slotResults = (res.success && res.data) ? res.data : [];
        },
        error: () => {
          this.isSearchingSlots = false;
          this.slotResults = [];
        }
      });
  }

  selectSlot(slot: SupplierIdSlotResponse): void {
    this.selectedSlot = slot;
    this.slotQuery = slot.externalId;
    this.slotResults = [];
  }

  linkSlot(): void {
    if (!this.selectedSlot || !this.captain) {
      this.linkError = 'يرجى اختيار معرف من القائمة';
      return;
    }

    const payload: AssignmentRequest = {
      captainId: this.captain.id,
      validFrom: this.linkValidFrom || null,
      supplierSideName: this.linkSideName.trim() || null,
      reason: 'ربط يدوي من صفحة الكابتن'
    };

    this.isLinking = true;
    this.linkError = '';

    this.http.post<ApiResponse<SupplierIdSlotResponse>>(
      `/api/supplier-ids/slots/${this.selectedSlot.id}/assign`, payload)
      .subscribe({
        next: (res) => {
          this.isLinking = false;
          if (res.success) {
            this.showLinkModal = false;
            this.reload();
          } else {
            this.linkError = res.message || 'فشل ربط المعرف';
          }
        },
        error: (err) => {
          this.isLinking = false;
          this.linkError = err?.error?.message || 'فشل ربط المعرف';
        }
      });
  }

  /** Ends the assignment but keeps it in the slot's history. */
  unlinkSlot(sid: SupplierIdInfo): void {
    if (!confirm(`هل تريد فك ربط المعرف ${sid.externalId} عن هذا الكابتن؟`)) {
      return;
    }

    const payload: UnassignRequest = {
      validTo: isoDate(),
      reason: 'فك ربط يدوي من صفحة الكابتن'
    };

    this.idError = '';
    this.http.post<ApiResponse<SupplierIdSlotResponse>>(
      `/api/supplier-ids/slots/${sid.slotId}/unassign`, payload)
      .subscribe({
        next: () => this.reload(),
        error: (err) => {
          this.idError = err?.error?.message || 'فشل فك الربط';
        }
      });
  }

  private reload(): void {
    if (this.captain) {
      this.loadCaptain(this.captain.id);
    }
  }

  getTypeLabel(type: string): string {
    return type === 'EMPLOYEE' ? 'KSC' : 'KSCFL';
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

  // ------------------------------------------------------- document expiry

  docStatusClass(doc: DocumentStatusInfo): string {
    const map: Record<DocumentStatus, string> = {
      'VALID': 'status-active',
      'EXPIRING': 'doc-expiring',
      'EXPIRED': 'doc-expired',
      'MISSING': 'doc-missing'
    };
    return map[doc.status] ?? '';
  }

  /** "بعد 12 يوم" / "منذ 40 يوم" / "ينتهي اليوم" — the number the office acts on. */
  daysLeftLabel(doc: DocumentStatusInfo): string {
    const days = doc.daysLeft;
    if (days === null) return '';
    if (days === 0) return 'ينتهي اليوم';
    return days > 0 ? `بعد ${days} يوم` : `منذ ${Math.abs(days)} يوم`;
  }

  /** Headline of the banner: how bad the worst document is. */
  documentBannerTitle(): string {
    const map: Record<DocumentStatus, string> = {
      'VALID': '',
      'EXPIRING': 'وثائق على وشك الانتهاء',
      'EXPIRED': 'وثائق منتهية تحتاج تجديداً',
      'MISSING': 'وثائق بلا تاريخ مسجل'
    };
    return this.captain ? map[this.captain.worstDocumentStatus] : '';
  }

  /**
   * Names the documents behind the headline rather than repeating the count, so the banner
   * says what to go and do without the reader scrolling to the card below it.
   */
  documentBannerDetail(): string {
    if (!this.captain) return '';
    return this.captain.documentStatuses
      .filter(d => d.status !== 'VALID')
      .map(d => `${d.labelAr}: ${d.statusLabelAr}`)
      .join(' — ');
  }

}
