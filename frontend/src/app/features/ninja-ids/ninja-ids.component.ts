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

@Component({
  selector: 'app-ninja-ids',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
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

  constructor(private http: HttpClient) {}

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
          if (res.success && res.data) {
            this.suppliers = res.data;
            if (this.activeSupplier) {
              // Keep the current tab selected (and refresh its slot count) across reloads.
              this.activeSupplier =
                this.suppliers.find(s => s.id === this.activeSupplier!.id) ?? this.activeSupplier;
            } else if (!this.hasLoadedSuppliers && this.suppliers.length > 0) {
              this.activeSupplier = this.suppliers[0];
            }
            this.hasLoadedSuppliers = true;
          }
          this.loadSlots();
        },
        error: () => this.loadSlots()
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

  // ---------- Multi-select deletion ----------

  toggleSlot(slotId: string): void {
    if (this.selectedSlotIds.has(slotId)) {
      this.selectedSlotIds.delete(slotId);
    } else {
      this.selectedSlotIds.add(slotId);
    }
    this.selectAll = this.slots.length > 0 && this.slots.every(s => this.selectedSlotIds.has(s.id));
  }

  toggleSelectAll(): void {
    if (this.selectAll) {
      this.slots.forEach(s => this.selectedSlotIds.delete(s.id));
      this.selectAll = false;
    } else {
      this.slots.forEach(s => this.selectedSlotIds.add(s.id));
      this.selectAll = true;
    }
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
    if (!confirm(`هل أنت متأكد من حذف ${count} معرف${count > 1 ? 'ات' : ''} مع سجلاتها؟`)) return;

    const ids = Array.from(this.selectedSlotIds);
    this.http.request<ApiResponse<void>>('DELETE', '/api/supplier-ids/slots/batch', { body: ids })
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.clearSelection();
            this.loadSuppliers();
          } else {
            alert(res.message || 'فشل حذف المعرفات');
          }
        },
        error: (err) => {
          alert(err?.error?.message || 'فشل حذف المعرفات');
        }
      });
  }

  deleteSingleSlot(slotId: string, event: Event): void {
    event.stopPropagation();
    if (!confirm('هل أنت متأكد من حذف هذا المعرف مع سجلاته؟')) return;

    this.http.delete<ApiResponse<void>>(`/api/supplier-ids/slots/${slotId}`)
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.selectedSlotIds.delete(slotId);
            this.loadSuppliers();
          } else {
            alert(res.message || 'فشل حذف المعرف');
          }
        },
        error: (err) => {
          alert(err?.error?.message || 'فشل حذف المعرف');
        }
      });
  }

  deleteSupplier(supplier: SupplierResponse, event: Event): void {
    event.stopPropagation();
    if (!confirm(`هل أنت تأكد من حذف المورد "${supplier.name}" وجميع المعرفات التابعة له؟`)) {
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
            alert(res.message || 'فشل حذف المورد');
          }
        },
        error: (err) => {
          alert(err?.error?.message || 'فشل حذف المورد');
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
      this.supplierError = 'اسم المورد والكود مطلوبان';
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
            this.supplierError = res.message || 'فشل إضافة المورد';
          }
        },
        error: (err) => {
          this.isSavingSupplier = false;
          this.supplierError = err?.error?.message || 'فشل إضافة المورد';
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
      this.slotError = 'يرجى اختيار المورد';
      return;
    }
    if (!this.newSlotExternalId.trim()) {
      this.slotError = 'رقم المعرف مطلوب';
      return;
    }

    const payload: SupplierIdSlotRequest = {
      supplierId: this.newSlotSupplierId,
      externalId: this.newSlotExternalId.trim(),
      notes: this.newSlotNotes.trim() || null,
      captainId: this.selectedCaptain?.id ?? null,
      validFrom: this.selectedCaptain ? this.newSlotValidFrom : null,
      supplierSideName: this.selectedCaptain ? (this.newSlotSideName.trim() || null) : null,
      reason: this.selectedCaptain ? 'إضافة يدوية من سجل المعرفات' : null
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
            this.slotError = res.message || 'فشل إضافة المعرف';
          }
        },
        error: (err) => {
          this.isSavingSlot = false;
          this.slotError = err?.error?.message || 'فشل إضافة المعرف';
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
