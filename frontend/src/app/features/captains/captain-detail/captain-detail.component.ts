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
import { TranslationService } from '../../../core/i18n/translation.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { InitialsPipe } from '../../../shared/pipes/initials.pipe';
import { SarPipe } from '../../../shared/pipes/sar.pipe';

@Component({
  selector: 'app-captain-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, DocumentGalleryComponent, TranslatePipe, SarPipe, InitialsPipe],
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
    private router: Router,
    public translationService: TranslationService
  ) {}

  deleteCaptain(): void {
    if (!this.captain) return;
    const confirmMsg = this.translationService.translate('CAPTAIN_DETAIL.CONFIRM_DELETE', {
      name: this.captain.fullNameAr,
      code: this.captain.kamalCode
    });
    if (!confirm(confirmMsg)) {
      return;
    }

    this.http.delete<ApiResponse<void>>(`/api/captains/${this.captain.id}`)
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.router.navigate(['/captains']);
          } else {
            alert(res.message || this.translationService.translate('CAPTAIN_DETAIL.DELETE_FAILED'));
          }
        },
        error: (err) => {
          alert(err?.error?.message || this.translationService.translate('CAPTAIN_DETAIL.DELETE_FAILED'));
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
            this.errorMessage = this.translationService.translate('CAPTAIN_DETAIL.LOAD_FAILED');
          }
        },
        error: () => {
          this.isLoading = false;
          this.errorMessage = this.translationService.translate('CAPTAIN_DETAIL.LOAD_FAILED');
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
      this.linkError = this.translationService.translate('CAPTAIN_DETAIL.SELECT_ID_FIRST');
      return;
    }

    const payload: AssignmentRequest = {
      captainId: this.captain.id,
      validFrom: this.linkValidFrom || null,
      supplierSideName: this.linkSideName.trim() || null,
      reason: this.translationService.translate('CAPTAIN_DETAIL.MANUAL_LINK')
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
            this.linkError = res.message || this.translationService.translate('CAPTAIN_DETAIL.LINK_FAILED');
          }
        },
        error: (err) => {
          this.isLinking = false;
          this.linkError = err?.error?.message || this.translationService.translate('CAPTAIN_DETAIL.LINK_FAILED');
        }
      });
  }

  /** Ends the assignment but keeps it in the slot's history. */
  unlinkSlot(sid: SupplierIdInfo): void {
    const confirmMsg = this.translationService.translate('CAPTAIN_DETAIL.UNLINK_CONFIRM', { id: sid.externalId });
    if (!confirm(confirmMsg)) {
      return;
    }

    const payload: UnassignRequest = {
      validTo: isoDate(),
      reason: this.translationService.translate('CAPTAIN_DETAIL.MANUAL_UNLINK')
    };

    this.idError = '';
    this.http.post<ApiResponse<SupplierIdSlotResponse>>(
      `/api/supplier-ids/slots/${sid.slotId}/unassign`, payload)
      .subscribe({
        next: () => this.reload(),
        error: (err) => {
          this.idError = err?.error?.message || this.translationService.translate('CAPTAIN_DETAIL.UNLINK_FAILED');
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
    const key = 'CAPTAINS.STATUS_' + status;
    const trans = this.translationService.translate(key);
    return trans !== key ? trans : status;
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

  getDocLabel(doc: DocumentStatusInfo): string {
    if (this.translationService.currentLang() === 'ar') {
      return doc.labelAr;
    }
    const key = 'DOC_TYPES.' + doc.kind;
    const trans = this.translationService.translate(key);
    return trans !== key ? trans : doc.labelAr;
  }

  getDocStatusLabel(doc: DocumentStatusInfo): string {
    if (this.translationService.currentLang() === 'ar') {
      return doc.statusLabelAr;
    }
    const key = 'DOC_STATUS.' + doc.status;
    const trans = this.translationService.translate(key);
    return trans !== key ? trans : doc.statusLabelAr;
  }

  /** "In 12 days" / "12 days ago" / "Expires today" */
  daysLeftLabel(doc: DocumentStatusInfo): string {
    const days = doc.daysLeft;
    if (days === null) return '';
    if (days === 0) return this.translationService.translate('CAPTAIN_DETAIL.EXPIRES_TODAY');
    return days > 0
      ? this.translationService.translate('CAPTAIN_DETAIL.IN_DAYS', { days: String(days) })
      : this.translationService.translate('CAPTAIN_DETAIL.AGO_DAYS', { days: String(Math.abs(days)) });
  }

  /** Headline of the banner: how bad the worst document is. */
  documentBannerTitle(): string {
    if (!this.captain) return '';
    const map: Record<DocumentStatus, string> = {
      'VALID': '',
      'EXPIRING': this.translationService.translate('CAPTAIN_DETAIL.BANNER_EXPIRING'),
      'EXPIRED': this.translationService.translate('CAPTAIN_DETAIL.BANNER_EXPIRED'),
      'MISSING': this.translationService.translate('CAPTAIN_DETAIL.BANNER_MISSING')
    };
    return map[this.captain.worstDocumentStatus] || '';
  }

  documentBannerDetail(): string {
    if (!this.captain) return '';
    return this.captain.documentStatuses
      .filter(d => d.status !== 'VALID')
      .map(d => `${this.getDocLabel(d)}: ${this.getDocStatusLabel(d)}`)
      .join(' — ');
  }
}
