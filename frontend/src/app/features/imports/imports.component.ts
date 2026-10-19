import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import {
  ApiResponse, DeductionImportPreviewResponse,
  ImportBatchResponse, ImportBatchType, ImportPreviewResponse, ShiftImportPreviewResponse,
  SupplierResponse, VacationCommissionImportPreviewResponse
} from '../../core/models/api.models';
import { ImportService } from '../../core/services/import.service';
import { downloadBlob, errorCode, errorMessage } from '../../core/services/api.util';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationService } from '../../core/i18n/translation.service';

/**
 * Four import domains: captain roster, supplier shift export, supplier deductions, and vacation commission.
 */
type ImportTab = 'captains' | 'reports' | 'deductions' | 'vacation-commission';

@Component({
  selector: 'app-imports',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './imports.component.html',
  styleUrls: ['./imports.component.scss']
})
export class ImportsComponent implements OnInit {

  private importService = inject(ImportService);
  private http = inject(HttpClient);
  private router = inject(Router);
  public i18n = inject(TranslationService);

  activeTab: ImportTab = 'captains';

  // ------------------------------------------------------------- suppliers

  suppliers: SupplierResponse[] = [];
  selectedSupplierId = '';
  isLoadingSuppliers = false;

  // ------------------------------------------------- expected sheet layouts

  /** Mirrors TEMPLATE_HEADERS in ExcelTemplateService — the bilingual layout. Only Job ID is required. */
  readonly captainRequiredColumns = [
    'الرقم الوظيفي / Job ID'
  ];
  readonly captainOptionalColumns = [
    'معرف التطبيق / ID-APP',
    'الاسم / Name (Ar)',
    'Name (En)',
    'الجنس / Gender',
    'الجنسية / Nationality',
    'تاريخ الميلاد / Date of birth',
    'جوال / Phone no',
    'مدينة / City',
    'عنوان السكن / Home Address',
    'بريد الكتروني / Email',
    'رقم الجواز / Passport No',
    'تاريخ الاصدار / Issue Date',
    'تاريخ الانتهاء / Expir Date',
    'حالة الجواز / Passport status',
    'رقم الهوية / الاقامة / Identity / residence no',
    'تاريخ انتهاء الهوية / ID Expiry Date',
    'تاريخ انتهاء الاقامة / Expir Date',
    'تاريخ الانتهاء بعد التجديد / Expiry date after renewal',
    'رقم الحدود / Border no',
    'المهنة في الاقامة / Occupation on residence',
    'حالة الاقامة / Residence status',
    'رقم عقد قوى / Contract no',
    'رقم المنشأة / Establishment ID',
    'تاريخ بداية العقد / Contract Start Date',
    'تاريخ نهاية العقد / Contract Expir Date',
    'حالة العقد / Contract status',
    'تاريخ انتهاء رخصة العمل / Work Permit Expir Date',
    'حالة رخصة العمل / Work Permit status',
    'الشركة التابع لها / Affiliated company',
    'رقم السجل التجاري / CR no',
    'رقم التأمين / Insurance no',
    'فئة التأمين / Insurance category',
    'تاريخ اصدار التأمين / Insurance Issue Date',
    'تاريخ انتهاء التأمين / Insurance Expir Date',
    'حالة التأمين / Insurance status',
    'شركة التأمين الطبي / Medical Insurance company',
    'يحمل رخصة قيادة / Has Driving License',
    'نوع الرخصة / License Type',
    'رقم رخصة القيادة / Driving license no',
    'تاريخ انتهاء الرخصة / Expir Date',
    'حالة الرخصة / License status',
    'فصيلة الدم / Blood type',
    'اسم البنك / Bank Name',
    'رقم الحساب البنكي / Bank account no',
    'الراتب الاساسي / Basic salary',
    'بدل سكن / Housing Allowance',
    'بدل مواصلات / Transport Allowance',
    'بدل محروقات / Fuel Allow',
    'بدل اتصال / Call Allow',
    'مكافأة / Reward',
    'خصومات / Deduction',
    'صافي الراتب / Net Salry',
    'فترة التجربة / Trial Period'
  ];

  /** Mirrors NinjaShiftParser's column map — the headers Ninja ships in its export. */
  readonly reportRequiredColumns = ['shift_id', 'captain_id', 'Shift Start At'];
  readonly reportOptionalColumns = [
    'supplier_id', 'supplier_name', 'City', 'shiftName', 'captain_name',
    'Shift End At', 'Captain Checked-in At', 'Flag - attendance',
    'Delay Time (Minutes)', 'Working Hour (Hour)', 'Shift Type', 'Total Delivered',
    'Offline Time', 'Free Time', 'Busy Time', 'Away from Zone',
    'Busy Utilization %', 'Delivery Ontime %', 'Hourly UTR'
  ];

  /** Supplier deductions import column layout. */
  readonly deductionRequiredColumns = ['ID - NINJA أو ID-KSLSC', 'تاريخ الشيفت', 'سعر الخصم'];
  readonly deductionOptionalColumns = [
    'اسم الكابتن', 'سبب الخصم / البيان', 'الهاتف/الهوية'
  ];

  /** Vacation commission import column layout. */
  readonly vacationCommissionRequiredColumns = ['Days (ksa start)', 'CAPTAIN ID', 'Amount'];
  readonly vacationCommissionOptionalColumns = [
    'SUPPLIER NAME', 'ZONE NAME', 'FLAG', 'NOTE'
  ];

  // ------------------------------------------------------------ upload state

  selectedFile: File | null = null;
  isDragging = false;
  isUploading = false;
  isCommitting = false;

  captainPreview: ImportPreviewResponse | null = null;
  captainResult: ImportPreviewResponse | null = null;

  shiftPreview: ShiftImportPreviewResponse | null = null;
  shiftResult: ShiftImportPreviewResponse | null = null;

  deductionPreview: DeductionImportPreviewResponse | null = null;
  deductionResult: DeductionImportPreviewResponse | null = null;

  vacationCommissionPreview: VacationCommissionImportPreviewResponse | null = null;
  vacationCommissionResult: VacationCommissionImportPreviewResponse | null = null;

  /**
   * The file the server identified as belonging to the other tab. Held so the user
   * can be moved there with the same file rather than having to pick it again.
   */
  misroutedFile: File | null = null;

  // ---------------------------------------------------------------- history

  history: ImportBatchResponse[] = [];
  isLoadingHistory = true;

  errorMessage = '';
  successMessage = '';

  ngOnInit(): void {
    this.loadSuppliers();
    this.loadHistory();
  }

  loadSuppliers(): void {
    this.isLoadingSuppliers = true;
    this.http.get<ApiResponse<SupplierResponse[]>>('/api/supplier-ids/suppliers')
      .subscribe({
        next: (res) => {
          this.isLoadingSuppliers = false;
          if (res.success && res.data) {
            this.suppliers = res.data;
            const savedSupplierId = localStorage.getItem('selectedImportSupplierId');
            if (savedSupplierId && this.suppliers.some(s => s.id === savedSupplierId)) {
              this.selectedSupplierId = savedSupplierId;
            } else {
              const ninja = this.suppliers.find(s => s.code === 'NINJA');
              this.selectedSupplierId = ninja ? ninja.id : (this.suppliers[0]?.id ?? '');
            }
          }
        },
        error: () => {
          this.isLoadingSuppliers = false;
        }
      });
  }

  onSupplierChange(supplierId: string): void {
    this.selectedSupplierId = supplierId;
    localStorage.setItem('selectedImportSupplierId', supplierId);
    if (this.selectedFile && (this.captainPreview || this.shiftPreview || this.deductionPreview || this.vacationCommissionPreview)) {
      this.uploadPreview();
    }
  }

  get selectedSupplier(): SupplierResponse | undefined {
    return this.suppliers.find(s => s.id === this.selectedSupplierId);
  }

  setTab(tab: ImportTab): void {
    if (this.activeTab === tab) return;
    this.activeTab = tab;
    // A pending preview belongs to the tab that produced it.
    this.cancelImport();
    this.loadHistory();
  }

  /** Moves to the tab the server said the file belongs to, carrying the file along. */
  openCorrectTab(): void {
    const file = this.misroutedFile;
    if (!file) return;
    this.setTab(this.activeTab === 'captains' ? 'reports' : 'captains');
    this.handleFile(file);
  }

  // ----------------------------------------------------------- file picking

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragging = false;
    const file = event.dataTransfer?.files?.[0];
    if (file) this.handleFile(file);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.handleFile(file);
    // Lets the same file be picked again after a reset.
    input.value = '';
  }

  handleFile(file: File): void {
    if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
      this.errorMessage = this.i18n.currentLang() === 'ar' ? 'يرجى رفع ملف Excel (.xlsx أو .xls)' : 'Please upload an Excel file (.xlsx or .xls)';
      return;
    }
    this.clearMessages();
    this.selectedFile = file;
    this.captainPreview = null;
    this.shiftPreview = null;
    this.deductionPreview = null;
    this.captainResult = null;
    this.shiftResult = null;
    this.deductionResult = null;
    this.uploadPreview();
  }

  // ---------------------------------------------------------------- preview

  /** Preview parses and validates without writing — nothing is saved until commit. */
  uploadPreview(): void {
    const file = this.selectedFile;
    if (!file) return;

    this.isUploading = true;
    this.clearMessages();

    const supplierId = this.selectedSupplierId;

    if (this.activeTab === 'captains') {
      this.importService.previewCaptains(file, supplierId).subscribe({
        next: (preview) => {
          this.isUploading = false;
          this.captainPreview = preview;
        },
        error: (err) => this.failPreview(err, file)
      });
    } else if (this.activeTab === 'reports') {
      this.importService.previewShifts(file, supplierId).subscribe({
        next: (preview) => {
          this.isUploading = false;
          this.shiftPreview = preview;
        },
        error: (err) => this.failPreview(err, file)
      });
    } else if (this.activeTab === 'deductions') {
      this.importService.previewDeductions(file, supplierId).subscribe({
        next: (preview) => {
          this.isUploading = false;
          this.deductionPreview = preview;
        },
        error: (err) => this.failPreview(err, file)
      });
    } else {
      this.importService.previewVacationCommission(file, supplierId).subscribe({
        next: (preview) => {
          this.isUploading = false;
          this.vacationCommissionPreview = preview;
        },
        error: (err) => this.failPreview(err, file)
      });
    }
  }

  private failPreview(err: unknown, file: File): void {
    this.isUploading = false;
    // Keep the file only when it can be re-sent to the other tab.
    this.misroutedFile = errorCode(err) === 'WRONG_IMPORT_TYPE' ? file : null;
    this.selectedFile = null;
    this.errorMessage = errorMessage(err, this.i18n.get('IMPORTS.IMPORT_FAILED'));
  }

  // ----------------------------------------------------------------- commit

  confirmImport(): void {
    const file = this.selectedFile;
    const batchId = this.activeTab === 'captains'
      ? this.captainPreview?.batchId
      : this.activeTab === 'reports'
        ? this.shiftPreview?.batchId
        : this.activeTab === 'deductions'
          ? this.deductionPreview?.batchId
          : this.vacationCommissionPreview?.batchId;
    if (!file || !batchId) return;

    this.isCommitting = true;
    this.clearMessages();

    const supplierId = this.selectedSupplierId;

    if (this.activeTab === 'captains') {
      this.importService.commitCaptains(batchId, file, supplierId).subscribe({
        next: (result) => {
          this.finishCommit();
          this.captainResult = result;
          this.successMessage = this.i18n.currentLang() === 'ar'
            ? `تم استيراد ${result.newCaptains} كابتن بنجاح للمورد ${this.selectedSupplier?.name || ''}`
            : `Successfully imported ${result.newCaptains} captains for supplier ${this.selectedSupplier?.name || ''}`;
        },
        error: (err) => this.failCommit(err)
      });
    } else if (this.activeTab === 'reports') {
      this.importService.commitShifts(batchId, file, supplierId).subscribe({
        next: (result) => {
          this.finishCommit();
          this.shiftResult = result;
          this.successMessage = this.i18n.currentLang() === 'ar'
            ? `تم استيراد ${result.committedRows} شفت بنجاح للمورد ${this.selectedSupplier?.name || ''}`
            : `Successfully imported ${result.committedRows} shifts for supplier ${this.selectedSupplier?.name || ''}`;
        },
        error: (err) => this.failCommit(err)
      });
    } else if (this.activeTab === 'deductions') {
      this.importService.commitDeductions(batchId, file, supplierId).subscribe({
        next: (result) => {
          this.finishCommit();
          this.deductionResult = result;
          this.successMessage = this.i18n.currentLang() === 'ar'
            ? `تم تطبيق خصومات المورد على ${result.matchedRows} شيفت بنجاح`
            : `Successfully applied supplier deductions to ${result.matchedRows} shifts`;
        },
        error: (err) => this.failCommit(err)
      });
    } else {
      this.importService.commitVacationCommission(batchId, file, supplierId).subscribe({
        next: (result) => {
          this.finishCommit();
          this.vacationCommissionResult = result;
          this.successMessage = this.i18n.currentLang() === 'ar'
            ? `تم استيراد عمولة أوقات الإجازة لـ ${result.matchedRows} كابتن بنجاح`
            : `Successfully imported vacation commissions for ${result.matchedRows} captains`;
        },
        error: (err) => this.failCommit(err)
      });
    }
  }

  private finishCommit(): void {
    this.isCommitting = false;
    this.captainPreview = null;
    this.shiftPreview = null;
    this.deductionPreview = null;
    this.vacationCommissionPreview = null;
    this.selectedFile = null;
    this.loadHistory();
  }

  private failCommit(err: unknown): void {
    this.isCommitting = false;
    this.errorMessage = errorMessage(err, this.i18n.currentLang() === 'ar' ? 'فشل في تأكيد الاستيراد' : 'Failed to confirm import');
  }

  cancelImport(): void {
    this.selectedFile = null;
    this.captainPreview = null;
    this.shiftPreview = null;
    this.deductionPreview = null;
    this.vacationCommissionPreview = null;
    this.captainResult = null;
    this.shiftResult = null;
    this.deductionResult = null;
    this.vacationCommissionResult = null;
    this.clearMessages();
  }

  // ---------------------------------------------------------------- history

  loadHistory(): void {
    this.isLoadingHistory = true;
    this.importService.history(this.batchType).subscribe({
      next: (page) => {
        this.isLoadingHistory = false;
        this.history = page.content;
      },
      error: (err) => {
        this.isLoadingHistory = false;
        this.errorMessage = errorMessage(err, this.i18n.currentLang() === 'ar' ? 'فشل في تحميل سجل الاستيراد' : 'Failed to load import history');
      }
    });
  }

  get batchType(): ImportBatchType {
    if (this.activeTab === 'captains') return 'CAPTAINS';
    if (this.activeTab === 'reports') return 'SHIFTS';
    if (this.activeTab === 'deductions') return 'SUPPLIER_DEDUCTIONS';
    return 'VACATION_COMMISSION';
  }

  // --------------------------------------------------------------- template

  /**
   * The JWT is attached by the HttpClient interceptor only, so a plain <a href> would hit
   * the API unauthenticated. Fetch as a blob and save it client-side instead.
   */
  downloadTemplate(): void {
    const request = this.activeTab === 'captains'
      ? this.importService.downloadTemplate()
      : this.activeTab === 'reports'
        ? this.importService.downloadShiftTemplate()
        : this.activeTab === 'deductions'
          ? this.importService.downloadDeductionTemplate()
          : this.importService.downloadVacationCommissionTemplate();

    const fileName = this.activeTab === 'captains'
      ? 'نموذج-استيراد-الكباتن.xlsx'
      : this.activeTab === 'reports'
        ? 'نموذج-استيراد-تقارير-الموردين.xlsx'
        : this.activeTab === 'deductions'
          ? 'نموذج-استيراد-خصومات-المورد.xlsx'
          : 'نموذج-استيراد-عمولة-أوقات-الإجازة.xlsx';

    request.subscribe({
      next: (blob) => downloadBlob(blob, fileName),
      error: () => { this.errorMessage = this.i18n.currentLang() === 'ar' ? 'فشل تحميل ملف القالب' : 'Failed to download template file'; }
    });
  }

  getScopeLabel(scope: string): string {
    const isAr = this.i18n.currentLang() === 'ar';
    const map: Record<string, { ar: string; en: string }> = {
      'NINJA_ID': { ar: 'ID نينجا', en: 'Ninja ID' },
      'KSLSC_ID': { ar: 'كود KSLSC', en: 'KSLSC Code' },
      'PHONE_OR_NATIONAL_ID': { ar: 'الهاتف/الهوية', en: 'Phone / ID' },
      'UNMATCHED': { ar: 'غير مطابق', en: 'Unmatched' }
    };
    return map[scope] ? (isAr ? map[scope].ar : map[scope].en) : scope;
  }

  // ----------------------------------------------------------- issue splits

  /** Captains tab: any row error blocks the commit, since nothing would be created. */
  get hasCaptainErrors(): boolean {
    return !!this.captainPreview && this.captainPreview.issues.some(i => i.severity === 'ERROR');
  }

  /**
   * Reports tab: a locked period is the only thing the server refuses outright.
   * Row-level errors just make it skip those rows, so they must not block the commit.
   */
  get blockingIssues() {
    return (this.shiftPreview?.issues ?? []).filter(i => i.issueCode === 'PERIOD_LOCKED');
  }

  /** Rows that will be skipped on commit — everything else still imports. */
  get rowErrors() {
    return (this.shiftPreview?.issues ?? [])
      .filter(i => i.severity === 'ERROR' && i.issueCode !== 'PERIOD_LOCKED');
  }

  get warningIssues() {
    return (this.shiftPreview?.issues ?? []).filter(i => i.severity !== 'ERROR');
  }

  get canCommitShifts(): boolean {
    return !!this.shiftPreview && this.blockingIssues.length === 0 && !this.isCommitting;
  }

  // ------------------------------------------------------------- navigation

  goToShifts(): void {
    this.router.navigate(['/shifts']);
  }

  goToNinjaIds(): void {
    this.router.navigate(['/ninja-ids']);
  }

  // ----------------------------------------------------------------- shared

  clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.misroutedFile = null;
  }

  getStatusLabel(status: string): string {
    const isAr = this.i18n.currentLang() === 'ar';
    const map: Record<string, { ar: string; en: string }> = {
      'PREVIEW': { ar: 'معاينة', en: 'Preview' },
      'COMMITTED': { ar: 'تم التأكيد', en: 'Committed' },
      'CANCELLED': { ar: 'ملغي', en: 'Cancelled' },
      'FAILED': { ar: 'فشل', en: 'Failed' }
    };
    return map[status] ? (isAr ? map[status].ar : map[status].en) : status;
  }

  getStatusClass(status: string): string {
    const map: Record<string, string> = {
      'PREVIEW': 'status-preview',
      'COMMITTED': 'status-committed',
      'CANCELLED': 'status-cancelled',
      'FAILED': 'status-failed'
    };
    return map[status] || '';
  }

  formatDate(dateStr: string): string {
    try {
      const locale = this.i18n.currentLang() === 'ar' ? 'ar-SA' : 'en-US';
      return new Date(dateStr).toLocaleDateString(locale, {
        year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
      });
    } catch {
      return dateStr;
    }
  }
}
