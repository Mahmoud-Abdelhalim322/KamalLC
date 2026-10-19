import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { isoDate } from '../../core/services/api.util';
import {
  ApiResponse, SupplierResponse, SupplierIdSlotResponse,
  PageResponse, AssignmentHistoryItem, CaptainLookupItem,
  CreateSupplierRequest, SupplierIdSlotRequest
} from '../../core/models/api.models';
import { TranslationService } from '../../core/i18n/translation.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

@Component({
  selector: 'app-ninja-ids',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, TranslatePipe],
  templateUrl: './ninja-ids.component.html',
  styleUrls: ['./ninja-ids.component.scss']
})
export class NinjaIdsComponent implements OnInit {
  suppliers: SupplierResponse[] = [];
  activeSupplier: SupplierResponse | null = null;
  /** Guards the "select the first tab" default so it only applies to the initial load. */
  hasLoadedSuppliers = false;
  slots: SupplierIdSlotResponse[] = [];
  isLoading = true;
  searchQuery = '';

  // Pagination
  currentPage = 0;
  pageSize = 20;
  totalElements = 0;
  totalPages = 0;

  // Multi-select for deletion
  selectedSlotIds = new Set<string>();
  selectAll = false;

  // History modal
  selectedSlot: SupplierIdSlotResponse | null = null;
  historyItems: AssignmentHistoryItem[] = [];
  isLoadingHistory = false;

  // Add supplier
  showAddSupplier = false;
  newSupplierName = '';
  newSupplierCode = '';
  newSupplierColor = '#4CAF50';
  isSavingSupplier = false;
  supplierError = '';

  // Add slot (supplier ID)
  showAddSlot = false;
  newSlotSupplierId = '';
  newSlotExternalId = '';
  newSlotNotes = '';
  newSlotValidFrom = isoDate();
  newSlotSideName = '';
  isSavingSlot = false;
  slotError = '';

  // Captain picker inside the add-slot modal (linking is optional)
  captainQuery = '';
  captainResults: CaptainLookupItem[] = [];
  selectedCaptain: CaptainLookupItem | null = null;
  isSearchingCaptains = false;
  private captainSearch$ = new Subject<string>();

  constructor(
    private http: HttpClient,
    public translationService: TranslationService
  ) {}

  ngOnInit(): void {
    this.loadSuppliers();

    this.captainSearch$
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(query => this.searchCaptains(query));
  }

  loadSuppliers(): void {
    this.http.get<ApiResponse<SupplierResponse[]>>('/api/supplier-ids/suppliers')
      .subscribe({
        next: (res) => {
          this.suppliers = (res.success && res.data) ? res.data : [];
          if (!this.hasLoadedSuppliers) {
            this.hasLoadedSuppliers = true;
            this.activeSupplier = this.suppliers[0] ?? null;
          } else if (this.activeSupplier) {
            this.activeSupplier = this.suppliers.find(s => s.id === this.activeSupplier!.id) ?? null;
          }
          this.loadSlots();
        },
        error: () => {
          this.suppliers = [];
          this.isLoading = false;
        }
      });
  }

  loadSlots(): void {
    this.isLoading = true;
    let url = `/api/supplier-ids/slots?page=${this.currentPage}&size=${this.pageSize}`;
    if (this.activeSupplier) {
      url += `&supplierId=${this.activeSupplier.id}`;
    }
    if (this.searchQuery.trim()) {
      url += `&q=${encodeURIComponent(this.searchQuery.trim())}`;
    }

    this.http.get<ApiResponse<PageResponse<SupplierIdSlotResponse>>>(url)
      .subscribe({
        next: (res) => {
          this.isLoading = false;
          if (res.success && res.data) {
            this.slots = res.data.content;
            this.totalElements = res.data.totalElements;
            this.totalPages = res.data.totalPages;
            this.updateSelectAllState();
          } else {
            this.slots = [];
          }
        },
        error: () => {
          this.isLoading = false;
          this.slots = [];
        }
      });
  }

  onSearch(): void {
    this.currentPage = 0;
    this.clearSelection();
    this.loadSlots();
  }

  // ---------- Multi-select for deletion ----------

  toggleSelectAll(): void {
    this.selectAll = !this.selectAll;
    if (this.selectAll) {
      this.slots.forEach(s => this.selectedSlotIds.add(s.id));
    } else {
      this.clearSelection();
    }
  }

  toggleSlot(slotId: string, event: Event): void {
    event.stopPropagation();
    if (this.selectedSlotIds.has(slotId)) {
      this.selectedSlotIds.delete(slotId);
    } else {
      this.selectedSlotIds.add(slotId);
    }
    this.updateSelectAllState();
  }

  private updateSelectAllState(): void {
    this.selectAll = this.slots.length > 0 && this.slots.every(s => this.selectedSlotIds.has(s.id));
  }

  clearSelection(): void {
    this.selectedSlotIds.clear();
    this.selectAll = false;
  }

  isSlotSelected(slotId: string): boolean {
    return this.selectedSlotIds.has(slotId);
  }

  deleteSelected(): void {
    const count = this.selectedSlotIds.size;
    if (count === 0) return;
    const confirmMsg = this.translationService.translate('NINJA_IDS.CONFIRM_BULK_DELETE', { count: String(count) });
    if (!confirm(confirmMsg)) return;

    const ids = Array.from(this.selectedSlotIds);
    this.http.request<ApiResponse<void>>('DELETE', '/api/supplier-ids/slots/batch', { body: ids })
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.clearSelection();
            this.loadSuppliers();
          } else {
            alert(res.message || this.translationService.translate('NINJA_IDS.DELETE_SLOTS_FAILED'));
          }
        },
        error: (err) => {
          alert(err?.error?.message || this.translationService.translate('NINJA_IDS.DELETE_SLOTS_FAILED'));
        }
      });
  }

  deleteSingleSlot(slotId: string, event: Event): void {
    event.stopPropagation();
    const confirmMsg = this.translationService.translate('NINJA_IDS.CONFIRM_DELETE_ID');
    if (!confirm(confirmMsg)) return;

    this.http.delete<ApiResponse<void>>(`/api/supplier-ids/slots/${slotId}`)
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.selectedSlotIds.delete(slotId);
            this.loadSuppliers();
          } else {
            alert(res.message || this.translationService.translate('NINJA_IDS.DELETE_SLOT_FAILED'));
          }
        },
        error: (err) => {
          alert(err?.error?.message || this.translationService.translate('NINJA_IDS.DELETE_SLOT_FAILED'));
        }
      });
  }

  deleteSupplier(supplier: SupplierResponse, event: Event): void {
    event.stopPropagation();
    const confirmMsg = this.translationService.translate('NINJA_IDS.CONFIRM_DELETE_SUPPLIER', { name: supplier.name });
    if (!confirm(confirmMsg)) {
      return;
    }

    this.http.delete<ApiResponse<void>>(`/api/supplier-ids/suppliers/${supplier.id}`)
      .subscribe({
        next: (res) => {
          if (res.success) {
            if (this.activeSupplier?.id === supplier.id) {
              this.activeSupplier = null;
            }
            this.loadSuppliers();
          } else {
            alert(res.message || this.translationService.translate('NINJA_IDS.DELETE_SUPPLIER_FAILED'));
          }
        },
        error: (err) => {
          alert(err?.error?.message || this.translationService.translate('NINJA_IDS.DELETE_SUPPLIER_FAILED'));
        }
      });
  }

  selectSupplier(supplier: SupplierResponse): void {
    this.activeSupplier = supplier;
    this.currentPage = 0;
    this.clearSelection();
    this.loadSlots();
  }

  showAll(): void {
    this.activeSupplier = null;
    this.currentPage = 0;
    this.clearSelection();
    this.loadSlots();
  }

  openHistory(slot: SupplierIdSlotResponse): void {
    this.selectedSlot = slot;
    this.isLoadingHistory = true;
    this.historyItems = [];

    this.http.get<ApiResponse<AssignmentHistoryItem[]>>(`/api/supplier-ids/slots/${slot.id}/history`)
      .subscribe({
        next: (res) => {
          this.isLoadingHistory = false;
          if (res.success && res.data) {
            this.historyItems = res.data;
          }
        },
        error: () => {
          this.isLoadingHistory = false;
        }
      });
  }

  closeHistory(): void {
    this.selectedSlot = null;
    this.historyItems = [];
  }

  // ---------- Add supplier ----------

  openAddSupplier(): void {
    this.showAddSupplier = true;
    this.newSupplierName = '';
    this.newSupplierCode = '';
    this.newSupplierColor = '#4CAF50';
    this.supplierError = '';
  }

  closeAddSupplier(): void {
    this.showAddSupplier = false;
  }

  saveSupplier(): void {
    if (!this.newSupplierName.trim() || !this.newSupplierCode.trim()) {
      this.supplierError = this.translationService.translate('NINJA_IDS.NAME_CODE_REQUIRED');
      return;
    }

    const payload: CreateSupplierRequest = {
      name: this.newSupplierName.trim(),
      code: this.newSupplierCode.trim().toUpperCase(),
      color: this.newSupplierColor
    };

    this.isSavingSupplier = true;
    this.supplierError = '';

    this.http.post<ApiResponse<SupplierResponse>>('/api/supplier-ids/suppliers', payload)
      .subscribe({
        next: (res) => {
          this.isSavingSupplier = false;
          if (res.success && res.data) {
            this.showAddSupplier = false;
            this.activeSupplier = res.data;
            this.currentPage = 0;
            this.loadSuppliers();
          } else {
            this.supplierError = res.message || this.translationService.translate('NINJA_IDS.CREATE_FAILED');
          }
        },
        error: (err) => {
          this.isSavingSupplier = false;
          this.supplierError = err?.error?.message || this.translationService.translate('NINJA_IDS.CREATE_FAILED');
        }
      });
  }

  // ---------- Add supplier ID (slot) ----------

  openAddSlot(): void {
    this.showAddSlot = true;
    this.newSlotSupplierId = this.activeSupplier?.id || (this.suppliers[0]?.id ?? '');
    this.newSlotExternalId = '';
    this.newSlotNotes = '';
    this.newSlotSideName = '';
    this.newSlotValidFrom = isoDate();
    this.captainQuery = '';
    this.captainResults = [];
    this.selectedCaptain = null;
    this.slotError = '';
  }

  closeAddSlot(): void {
    this.showAddSlot = false;
  }

  onCaptainQueryChange(query: string): void {
    this.selectedCaptain = null;
    this.captainSearch$.next(query);
  }

  private searchCaptains(query: string): void {
    if (!query.trim()) {
      this.captainResults = [];
      return;
    }

    this.isSearchingCaptains = true;
    this.http.get<ApiResponse<CaptainLookupItem[]>>(
      `/api/captains/lookup?q=${encodeURIComponent(query.trim())}`)
      .subscribe({
        next: (res) => {
          this.isSearchingCaptains = false;
          this.captainResults = (res.success && res.data) ? res.data : [];
        },
        error: () => {
          this.isSearchingCaptains = false;
          this.captainResults = [];
        }
      });
  }

  selectCaptain(captain: CaptainLookupItem): void {
    this.selectedCaptain = captain;
    this.captainQuery = `${captain.fullNameAr} — ${captain.kamalCode}`;
    this.captainResults = [];
  }

  clearCaptain(): void {
    this.selectedCaptain = null;
    this.captainQuery = '';
    this.captainResults = [];
  }

  saveSlot(): void {
    if (!this.newSlotSupplierId) {
      this.slotError = this.translationService.translate('NINJA_IDS.SELECT_SUPPLIER_REQUIRED');
      return;
    }
    if (!this.newSlotExternalId.trim()) {
      this.slotError = this.translationService.translate('NINJA_IDS.ID_REQUIRED');
      return;
    }

    const payload: SupplierIdSlotRequest = {
      supplierId: this.newSlotSupplierId,
      externalId: this.newSlotExternalId.trim(),
      notes: this.newSlotNotes.trim() || null,
      captainId: this.selectedCaptain?.id ?? null,
      validFrom: this.selectedCaptain ? this.newSlotValidFrom : null,
      supplierSideName: this.selectedCaptain ? (this.newSlotSideName.trim() || null) : null,
      reason: this.selectedCaptain ? this.translationService.translate('NINJA_IDS.MANUAL_ADD_REASON') : null
    };

    this.isSavingSlot = true;
    this.slotError = '';

    this.http.post<ApiResponse<SupplierIdSlotResponse>>('/api/supplier-ids/slots', payload)
      .subscribe({
        next: (res) => {
          this.isSavingSlot = false;
          if (res.success) {
            this.showAddSlot = false;
            this.currentPage = 0;
            // Reload suppliers too so the slot counters on the tabs stay accurate.
            this.loadSuppliers();
          } else {
            this.slotError = res.message || this.translationService.translate('NINJA_IDS.CREATE_ID_FAILED');
          }
        },
        error: (err) => {
          this.isSavingSlot = false;
          this.slotError = err?.error?.message || this.translationService.translate('NINJA_IDS.CREATE_ID_FAILED');
        }
      });
  }

  nextPage(): void {
    if (this.currentPage < this.totalPages - 1) {
      this.currentPage++;
      this.loadSlots();
    }
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadSlots();
    }
  }
}
