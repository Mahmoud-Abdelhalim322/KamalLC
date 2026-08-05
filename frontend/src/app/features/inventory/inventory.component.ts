import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InventoryService } from '../../core/services/inventory.service';
import { CaptainService } from '../../core/services/captain.service';
import { CustodyService } from '../../core/services/custody.service';
import {
  AssetCategoryResponse, AssetItemResponse, FieldTemplateResponse,
  CaptainLookupItem, CustodyRecordResponse
} from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api.util';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './inventory.component.html',
  styleUrls: ['./inventory.component.scss']
})
export class InventoryComponent implements OnInit {
  readonly saudiSimProviders = [
    'زين (Zain)',
    'STC (الاتصالات السعودية)',
    'موبايلي (Mobily)',
    'سلام (Salam)',
    'فيرجن موفابل (Virgin Mobile)',
    'ليبارا (Lebara)',
    'أخرى'
  ];

  categories: AssetCategoryResponse[] = [];
  selectedCategory: AssetCategoryResponse | null = null;
  items: AssetItemResponse[] = [];
  itemPage = 0;
  itemTotalPages = 0;
  itemSearch = '';
  itemStatusFilter = '';
  isLoading = false;

  // Dialogs
  showCategoryDialog = false;
  showItemDialog = false;
  showAssignDialog = false;

  // History dialog
  showHistoryDialog = false;
  historyItemLabel = '';
  historyRecords: CustodyRecordResponse[] = [];
  historyPage = 0;
  historyTotalPages = 0;
  historyReferenceId = '';

  // Category form
  catForm = { labelAr: '', fieldTemplates: [] as { fieldKey: string; labelAr: string; fieldOrder: number; required: boolean }[] };

  // Item form
  itemFields: Record<string, string> = {};
  itemNotes = '';
  editingItemId: string | null = null;

  // Assign form
  assignItemId = '';
  assignItemLabel = '';
  assignCaptainId = '';
  captainResults: CaptainLookupItem[] = [];

  error = '';
  successMsg = '';

  constructor(
    private inventoryService: InventoryService,
    private captainService: CaptainService,
    private custodyService: CustodyService,
    public authService: AuthService
  ) {}

  get canManage(): boolean {
    return this.authService.hasPermission('INVENTORY_MANAGE');
  }

  ngOnInit(): void { this.loadCategories(); }

  loadCategories(): void {
    this.inventoryService.getCategories().subscribe({
      next: cats => {
        this.categories = cats;
        if (!this.selectedCategory && cats.length > 0) this.selectCategory(cats[0]);
      },
      error: e => this.error = errorMessage(e, 'فشل تحميل الفئات')
    });
  }

  selectCategory(cat: AssetCategoryResponse): void {
    this.selectedCategory = cat;
    this.itemPage = 0;
    this.loadItems();
  }

  loadItems(): void {
    if (!this.selectedCategory) return;
    this.isLoading = true;
    this.inventoryService.listItems({
      categoryId: this.selectedCategory.id,
      status: this.itemStatusFilter || undefined,
      search: this.itemSearch || undefined,
      page: this.itemPage, size: 15
    }).subscribe({
      next: p => { this.items = p.content; this.itemTotalPages = p.totalPages; this.isLoading = false; },
      error: e => { this.error = errorMessage(e, 'فشل تحميل العناصر'); this.isLoading = false; }
    });
  }

  searchItems(): void { this.itemPage = 0; this.loadItems(); }

  // ── Category Dialog ──

  openAddCategory(): void {
    this.catForm = { labelAr: '', fieldTemplates: [] };
    this.showCategoryDialog = true;
  }

  addFieldTemplate(): void {
    const order = this.catForm.fieldTemplates.length + 1;
    this.catForm.fieldTemplates.push({ fieldKey: '', labelAr: '', fieldOrder: order, required: false });
  }

  removeFieldTemplate(i: number): void { this.catForm.fieldTemplates.splice(i, 1); }

  saveCategory(): void {
    if (!this.catForm.labelAr) { this.error = 'اسم الفئة مطلوب'; return; }
    // Auto-generate fieldKeys from labelAr if empty
    this.catForm.fieldTemplates.forEach(ft => {
      if (!ft.fieldKey) ft.fieldKey = ft.labelAr.replace(/\s+/g, '_').toLowerCase();
    });
    this.inventoryService.createCategory({
      labelAr: this.catForm.labelAr, name: null, fieldTemplates: this.catForm.fieldTemplates
    }).subscribe({
      next: () => { this.showCategoryDialog = false; this.successMsg = 'تم إضافة الفئة'; this.loadCategories(); },
      error: e => this.error = errorMessage(e, 'فشل إضافة الفئة')
    });
  }

  deleteCategory(cat: AssetCategoryResponse): void {
    if (cat.systemDefined) { this.error = 'لا يمكن حذف فئة نظامية'; return; }
    if (!confirm(`حذف فئة "${cat.labelAr}"؟`)) return;
    this.inventoryService.deleteCategory(cat.id).subscribe({
      next: () => { this.successMsg = 'تم حذف الفئة'; this.selectedCategory = null; this.loadCategories(); },
      error: e => this.error = errorMessage(e, 'فشل حذف الفئة')
    });
  }

  // ── Item Dialog ──

  openAddItem(): void {
    if (!this.selectedCategory) return;
    this.editingItemId = null;
    this.itemFields = {};
    this.itemNotes = '';
    this.selectedCategory.fieldTemplates.forEach(ft => this.itemFields[ft.fieldKey] = '');
    this.showItemDialog = true;
  }

  openEditItem(item: AssetItemResponse): void {
    if (!this.selectedCategory) return;
    this.editingItemId = item.id;
    this.itemFields = { ...item.fields };
    this.itemNotes = item.notes || '';
    this.showItemDialog = true;
  }

  saveItem(): void {
    if (!this.selectedCategory) return;
    const req = { categoryId: this.selectedCategory.id, fields: this.itemFields, notes: this.itemNotes || null };

    const obs = this.editingItemId
      ? this.inventoryService.updateItem(this.editingItemId, req)
      : this.inventoryService.createItem(req);

    obs.subscribe({
      next: () => { this.showItemDialog = false; this.successMsg = 'تم الحفظ'; this.loadItems(); this.loadCategories(); },
      error: e => this.error = errorMessage(e, 'فشل حفظ العنصر')
    });
  }

  // ── Assign / Return ──

  openAssignDialog(item: AssetItemResponse): void {
    this.assignItemId = item.id;
    this.assignItemLabel = `${item.categoryLabelAr} ${item.serialCode}`;
    this.assignCaptainId = '';
    this.captainResults = [];
    this.showAssignDialog = true;
  }

  searchCaptains(query: string): void {
    this.captainService.lookup(query).subscribe(r => this.captainResults = r);
  }

  selectCaptain(c: CaptainLookupItem): void {
    this.assignCaptainId = c.id;
    this.captainResults = [];
  }

  submitAssign(): void {
    if (!this.assignCaptainId) { this.error = 'اختر الكابتن'; return; }
    this.inventoryService.assignItem(this.assignItemId, this.assignCaptainId).subscribe({
      next: () => { this.showAssignDialog = false; this.successMsg = 'تم الإسناد'; this.loadItems(); },
      error: e => this.error = errorMessage(e, 'فشل الإسناد')
    });
  }

  returnItem(item: AssetItemResponse): void {
    if (!confirm('تأكيد استرجاع العنصر؟')) return;
    this.inventoryService.returnItem(item.id).subscribe({
      next: () => { this.successMsg = 'تم الاسترجاع'; this.loadItems(); },
      error: e => this.error = errorMessage(e, 'فشل الاسترجاع')
    });
  }

  statusLabel(s: string): string {
    return { AVAILABLE: 'متاح', ASSIGNED: 'مُسند', INACTIVE: 'غير فعال' }[s] || s;
  }

  prevPage(): void { if (this.itemPage > 0) { this.itemPage--; this.loadItems(); } }
  nextPage(): void { if (this.itemPage < this.itemTotalPages - 1) { this.itemPage++; this.loadItems(); } }

  dismissMsg(): void { this.error = ''; this.successMsg = ''; }

  getFieldTemplates(): FieldTemplateResponse[] {
    return this.selectedCategory?.fieldTemplates || [];
  }

  // ── Custody History ──

  openItemHistory(item: AssetItemResponse): void {
    this.historyReferenceId = item.id;
    this.historyItemLabel = `${item.categoryLabelAr} (${item.serialCode})`;
    this.historyPage = 0;
    this.loadItemHistory();
  }

  loadItemHistory(): void {
    this.custodyService.getItemHistory(this.historyReferenceId, this.historyPage).subscribe({
      next: p => {
        this.historyRecords = p.content;
        this.historyTotalPages = p.totalPages;
        this.showHistoryDialog = true;
      },
      error: e => this.error = errorMessage(e, 'فشل تحميل تاريخ العهدة')
    });
  }

  prevHistoryPage(): void { if (this.historyPage > 0) { this.historyPage--; this.loadItemHistory(); } }
  nextHistoryPage(): void { if (this.historyPage < this.historyTotalPages - 1) { this.historyPage++; this.loadItemHistory(); } }
}
