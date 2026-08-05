import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import {
  DeductionImportPreviewResponse,
  ImportBatchResponse, ImportBatchType, ImportPreviewResponse, ShiftImportPreviewResponse
} from '../../core/models/api.models';
import { ImportService } from '../../core/services/import.service';
import { downloadBlob, errorCode, errorMessage } from '../../core/services/api.util';

/**
 * Three import domains: captain roster, supplier shift export, and supplier deductions.
 */
type ImportTab = 'captains' | 'reports' | 'deductions';

@Component({
  selector: 'app-imports',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './imports.component.html',
  styleUrls: ['./imports.component.scss']
})
export class ImportsComponent implements OnInit {

  private importService = inject(ImportService);
  private router = inject(Router);

  activeTab: ImportTab = 'captains';

  // ------------------------------------------------- expected sheet layouts

  /** Mirrors TEMPLATE_HEADERS in ExcelTemplateService — the layout the parser expects. */
  readonly captainRequiredColumns = ['ID-KSLSC', 'ID - NINJA', 'اسم الكابتن'];
  readonly captainOptionalColumns = [
    'الهاتف', 'الجنسية', 'تاريخ الميلاد', 'البريد الالكتروني',
    'رقم الإقامة', 'انتهاء الإقامة', 'رقم الجواز', 'انتهاء الجواز',
    'رقم الرخصة', 'انتهاء الرخصة'
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
    this.loadHistory();
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
      this.errorMessage = 'يرجى رفع ملف Excel (.xlsx أو .xls)';
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

    if (this.activeTab === 'captains') {
      this.importService.previewCaptains(file).subscribe({
        next: (preview) => {
          this.isUploading = false;
          this.captainPreview = preview;
        },
        error: (err) => this.failPreview(err, file)
      });
    } else if (this.activeTab === 'reports') {
      this.importService.previewShifts(file).subscribe({
        next: (preview) => {
          this.isUploading = false;
          this.shiftPreview = preview;
        },
        error: (err) => this.failPreview(err, file)
      });
    } else {
      this.importService.previewDeductions(file).subscribe({
        next: (preview) => {
          this.isUploading = false;
          this.deductionPreview = preview;
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
    this.errorMessage = errorMessage(err, 'فشل في تحليل الملف');
  }

  // ----------------------------------------------------------------- commit

  confirmImport(): void {
    const file = this.selectedFile;
    const batchId = this.activeTab === 'captains'
      ? this.captainPreview?.batchId
      : this.activeTab === 'reports'
        ? this.shiftPreview?.batchId
        : this.deductionPreview?.batchId;
    if (!file || !batchId) return;

    this.isCommitting = true;
    this.clearMessages();

    if (this.activeTab === 'captains') {
      this.importService.commitCaptains(batchId, file).subscribe({
        next: (result) => {
          this.finishCommit();
          this.captainResult = result;
          this.successMessage = `تم استيراد ${result.newCaptains} كابتن بنجاح`;
        },
        error: (err) => this.failCommit(err)
      });
    } else if (this.activeTab === 'reports') {
      this.importService.commitShifts(batchId, file).subscribe({
        next: (result) => {
          this.finishCommit();
          this.shiftResult = result;
          this.successMessage = `تم استيراد ${result.committedRows} شفت بنجاح`;
        },
        error: (err) => this.failCommit(err)
      });
    } else {
      this.importService.commitDeductions(batchId, file).subscribe({
        next: (result) => {
          this.finishCommit();
          this.deductionResult = result;
          this.successMessage = `تم تطبيق خصومات المورد على ${result.matchedRows} شيفت بنجاح`;
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
    this.selectedFile = null;
    this.loadHistory();
  }

  private failCommit(err: unknown): void {
    this.isCommitting = false;
    this.errorMessage = errorMessage(err, 'فشل في تأكيد الاستيراد');
  }

  cancelImport(): void {
    this.selectedFile = null;
    this.captainPreview = null;
    this.shiftPreview = null;
    this.deductionPreview = null;
    this.captainResult = null;
    this.shiftResult = null;
    this.deductionResult = null;
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
        this.errorMessage = errorMessage(err, 'فشل في تحميل سجل الاستيراد');
      }
    });
  }

  get batchType(): ImportBatchType {
    if (this.activeTab === 'captains') return 'CAPTAINS';
    if (this.activeTab === 'reports') return 'SHIFTS';
    return 'SUPPLIER_DEDUCTIONS';
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
        : this.importService.downloadDeductionTemplate();

    const fileName = this.activeTab === 'captains'
      ? 'نموذج-استيراد-الكباتن.xlsx'
      : this.activeTab === 'reports'
        ? 'نموذج-استيراد-تقارير-الموردين.xlsx'
        : 'نموذج-استيراد-خصومات-المورد.xlsx';

    request.subscribe({
      next: (blob) => downloadBlob(blob, fileName),
      error: () => { this.errorMessage = 'فشل تحميل ملف القالب'; }
    });
  }

  getScopeLabel(scope: string): string {
    const map: Record<string, string> = {
      'NINJA_ID': 'ID نينجا',
      'KSLSC_ID': 'كود KSLSC',
      'PHONE_OR_NATIONAL_ID': 'الهاتف/الهوية',
      'UNMATCHED': 'غير مطابق'
    };
    return map[scope] || scope;
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
    const map: Record<string, string> = {
      'PREVIEW': 'معاينة',
      'COMMITTED': 'تم التأكيد',
      'CANCELLED': 'ملغي',
      'FAILED': 'فشل'
    };
    return map[status] || status;
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
      return new Date(dateStr).toLocaleDateString('ar-SA', {
        year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
      });
    } catch {
      return dateStr;
    }
  }
}
