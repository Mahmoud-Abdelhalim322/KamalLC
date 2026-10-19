import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  SupplierAdjustmentResponse,
  SupplierRevenueSummaryResponse,
  SupplierRowSummary,
  SupplierStatementResponse
} from '../../core/models/api.models';
import { SupplierRevenueService } from '../../core/services/supplier-revenue.service';
import { downloadBlob, errorMessage } from '../../core/services/api.util';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationService } from '../../core/i18n/translation.service';

@Component({
  selector: 'app-supplier-revenue',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './supplier-revenue.component.html',
  styleUrls: ['./supplier-revenue.component.scss']
})
export class SupplierRevenueComponent implements OnInit {
  private revenueService = inject(SupplierRevenueService);
  public i18n = inject(TranslationService);

  // Filter State
  fromDate = '';
  toDate = '';
  activePreset = 'current-month';

  // Data State
  summary: SupplierRevenueSummaryResponse | null = null;
  statement: SupplierStatementResponse | null = null;
  isLoading = false;
  isLoadingStatement = false;
  isExporting = false;
  errorMessage = '';
  successMessage = '';

  // Modals State
  showAdjustmentModal = false;
  showStatementModal = false;
  selectedSupplier: SupplierRowSummary | null = null;

  // Categories list for adjustment
  categories = [
    { key: 'SUPPLIER_REVENUE.CAT_ORDER_CALC_ERROR', value: 'خطأ في حساب الطلبات' },
    { key: 'SUPPLIER_REVENUE.CAT_PRICING_ADJUSTMENT', value: 'تعديل تسعيرة / عمولة' },
    { key: 'SUPPLIER_REVENUE.CAT_SHIFT_COMPENSATION', value: 'تعويض شيفتات' },
    { key: 'SUPPLIER_REVENUE.CAT_OPERATIONAL_FINE', value: 'غرامة تشغيلية' },
    { key: 'SUPPLIER_REVENUE.CAT_OPERATIONAL_BONUS', value: 'مكافأة تشغيلية' },
    { key: 'SUPPLIER_REVENUE.CAT_ADMIN_SETTLEMENT', value: 'تسوية إدارية/يدوية' }
  ];

  // Form State
  adjSupplierId = '';
  adjType: 'ADDITION' | 'DEDUCTION' = 'DEDUCTION';
  adjCategory = 'خطأ في حساب الطلبات';
  customCategory = '';
  adjAmount: number | null = null;
  adjDate = new Date().toISOString().split('T')[0];
  adjNotes = '';
  isSavingAdjustment = false;

  ngOnInit(): void {
    this.setPreset('current-month');
  }

  getCategoryLabel(category: string): string {
    const found = this.categories.find(c => c.value === category);
    return found ? this.i18n.get(found.key) : category;
  }

  setPreset(preset: 'current-month' | 'last-month' | 'last-30'): void {
    this.activePreset = preset;
    const now = new Date();
    let from: Date;
    let to: Date = new Date();

    if (preset === 'current-month') {
      from = new Date(now.getFullYear(), now.getMonth(), 1);
      to = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    } else if (preset === 'last-month') {
      from = new Date(now.getFullYear(), now.getMonth() - 1, 1);
      to = new Date(now.getFullYear(), now.getMonth(), 0);
    } else {
      to = new Date();
      from = new Date();
      from.setDate(now.getDate() - 30);
    }

    this.fromDate = this.formatDateIso(from);
    this.toDate = this.formatDateIso(to);
    this.loadData();
  }

  onDateChange(): void {
    this.activePreset = 'custom';
    this.loadData();
  }

  loadData(): void {
    if (!this.fromDate || !this.toDate) return;
    this.isLoading = true;
    this.clearMessages();

    this.revenueService.getSummary(this.fromDate, this.toDate).subscribe({
      next: (res) => {
        this.isLoading = false;
        if (res.success && res.data) {
          this.summary = res.data;
        } else {
          this.errorMessage = res.message || this.i18n.get('SUPPLIER_REVENUE.LOAD_FAILED');
        }
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = errorMessage(err, this.i18n.get('SUPPLIER_REVENUE.SERVER_ERROR'));
      }
    });
  }

  openStatement(row: SupplierRowSummary): void {
    if (!row.supplierId) return;
    this.selectedSupplier = row;
    this.showStatementModal = true;
    this.isLoadingStatement = true;

    this.revenueService.getStatement(row.supplierId, this.fromDate, this.toDate).subscribe({
      next: (res) => {
        this.isLoadingStatement = false;
        if (res.success && res.data) {
          this.statement = res.data;
        } else {
          this.errorMessage = res.message || this.i18n.get('SUPPLIER_REVENUE.LOAD_STATEMENT_FAILED');
        }
      },
      error: (err) => {
        this.isLoadingStatement = false;
        this.errorMessage = errorMessage(err, this.i18n.get('SUPPLIER_REVENUE.LOAD_STATEMENT_FAILED'));
      }
    });
  }

  closeStatementModal(): void {
    this.showStatementModal = false;
    this.statement = null;
  }

  openAddAdjustmentModal(row?: SupplierRowSummary): void {
    this.adjSupplierId = row?.supplierId || (this.summary?.rows?.[0]?.supplierId ?? '');
    this.adjType = 'DEDUCTION';
    this.adjCategory = 'خطأ في حساب الطلبات';
    this.customCategory = '';
    this.adjAmount = null;
    this.adjDate = new Date().toISOString().split('T')[0];
    this.adjNotes = '';
    this.showAdjustmentModal = true;
  }

  closeAdjustmentModal(): void {
    this.showAdjustmentModal = false;
  }

  saveAdjustment(): void {
    if (!this.adjSupplierId) {
      this.errorMessage = this.i18n.get('SUPPLIER_REVENUE.SELECT_SUPPLIER_REQ');
      return;
    }
    if (!this.adjAmount || this.adjAmount <= 0) {
      this.errorMessage = this.i18n.get('SUPPLIER_REVENUE.VALID_AMOUNT_REQ');
      return;
    }

    const category = this.adjCategory === 'أخرى' ? this.customCategory.trim() : this.adjCategory;
    if (!category) {
      this.errorMessage = this.i18n.get('SUPPLIER_REVENUE.SPECIFY_CATEGORY_REQ');
      return;
    }

    this.isSavingAdjustment = true;
    this.clearMessages();

    this.revenueService.addAdjustment({
      supplierId: this.adjSupplierId,
      type: this.adjType,
      category,
      amount: this.adjAmount,
      adjustmentDate: this.adjDate,
      notes: this.adjNotes
    }).subscribe({
      next: (res) => {
        this.isSavingAdjustment = false;
        if (res.success) {
          this.successMessage = this.i18n.get('SUPPLIER_REVENUE.ADJUSTMENT_SAVED');
          this.closeAdjustmentModal();
          this.loadData();
          if (this.showStatementModal && this.selectedSupplier?.supplierId === this.adjSupplierId) {
            this.openStatement(this.selectedSupplier);
          }
        } else {
          this.errorMessage = res.message || this.i18n.get('SUPPLIER_REVENUE.SAVE_ADJUSTMENT_FAILED');
        }
      },
      error: (err) => {
        this.isSavingAdjustment = false;
        this.errorMessage = errorMessage(err, this.i18n.get('SUPPLIER_REVENUE.SAVE_ADJUSTMENT_FAILED'));
      }
    });
  }

  deleteAdjustment(adj: SupplierAdjustmentResponse): void {
    const confirmMsg = this.i18n.get('SUPPLIER_REVENUE.CONFIRM_DELETE_ADJUSTMENT', {
      category: this.getCategoryLabel(adj.category),
      amount: adj.amount
    });
    if (!confirm(confirmMsg)) return;

    this.revenueService.deleteAdjustment(adj.id).subscribe({
      next: (res) => {
        if (res.success) {
          this.successMessage = this.i18n.get('SUPPLIER_REVENUE.ADJUSTMENT_DELETED');
          this.loadData();
          if (this.selectedSupplier) {
            this.openStatement(this.selectedSupplier);
          }
        } else {
          this.errorMessage = res.message || this.i18n.get('SUPPLIER_REVENUE.DELETE_ADJUSTMENT_FAILED');
        }
      },
      error: (err) => {
        this.errorMessage = errorMessage(err, this.i18n.get('SUPPLIER_REVENUE.DELETE_ADJUSTMENT_FAILED'));
      }
    });
  }

  exportExcel(): void {
    if (!this.fromDate || !this.toDate) return;
    this.isExporting = true;
    this.clearMessages();

    this.revenueService.exportExcel(this.fromDate, this.toDate).subscribe({
      next: (blob) => {
        this.isExporting = false;
        const filename = this.i18n.get('SUPPLIER_REVENUE.EXPORT_FILE_NAME', {
          from: this.fromDate,
          to: this.toDate
        });
        downloadBlob(blob, filename);
      },
      error: (err) => {
        this.isExporting = false;
        this.errorMessage = errorMessage(err, this.i18n.get('SUPPLIER_REVENUE.EXPORT_FAILED'));
      }
    });
  }

  clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }

  private formatDateIso(d: Date): string {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
