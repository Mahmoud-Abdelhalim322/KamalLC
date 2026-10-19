import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, DocumentInfo, StaffDetailResponse, StaffRequest } from '../../core/models/api.models';
import { DocumentGalleryComponent } from '../../shared/components/document-gallery/document-gallery.component';
import { TranslationService, TranslatePipe } from '../../core/i18n';

@Component({
  selector: 'app-staff-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DocumentGalleryComponent, TranslatePipe],
  templateUrl: './staff-form.component.html',
  styleUrls: ['./staff-form.component.scss']
})
export class StaffFormComponent implements OnInit {
  /** Determined from route: '/workers/new' → WORKER, '/managers/new' → MANAGER */
  staffType: 'WORKER' | 'MANAGER' = 'WORKER';
  isEdit = false;
  staffId: string | null = null;
  isSaving = false;
  isLoading = false;
  nextCode = '';

  form: StaffRequest = this.emptyForm();

  // Document state
  documents: DocumentInfo[] = [];
  documentTypes: string[] = [];
  selectedDocType = 'PERSONAL_PHOTO';
  selectedFile: File | null = null;
  documentNote = '';
  isUploading = false;
  documentError = '';

  constructor(
    private http: HttpClient,
    private route: ActivatedRoute,
    private router: Router,
    private translationService: TranslationService
  ) {}

  ngOnInit(): void {
    const url = this.router.url;
    this.staffType = url.startsWith('/managers') ? 'MANAGER' : 'WORKER';
    this.form.staffType = this.staffType;

    this.staffId = this.route.snapshot.paramMap.get('id');
    this.isEdit = !!this.staffId;

    this.loadDocumentTypes();

    if (this.isEdit) {
      this.loadStaff();
    } else {
      this.loadNextCode();
    }
  }

  get pageTitle(): string {
    if (this.isEdit) return this.staffType === 'MANAGER'
      ? this.translationService.translate('STAFF_FORM.EDIT_MANAGER')
      : this.translationService.translate('STAFF_FORM.EDIT_WORKER');
    return this.staffType === 'MANAGER'
      ? this.translationService.translate('STAFF_FORM.ADD_MANAGER')
      : this.translationService.translate('STAFF_FORM.ADD_WORKER');
  }

  get backRoute(): string {
    return this.staffType === 'MANAGER' ? '/managers' : '/workers';
  }

  loadNextCode(): void {
    this.http.get<ApiResponse<{ staffCode: string }>>(`/api/staff/next-code?type=${this.staffType}`)
      .subscribe(res => {
        if (res.success && res.data) this.nextCode = res.data.staffCode;
      });
  }

  loadDocumentTypes(): void {
    this.http.get<ApiResponse<string[]>>('/api/staff/document-types')
      .subscribe(res => {
        if (res.success && res.data) {
          this.documentTypes = res.data;
          if (!this.documentTypes.includes(this.selectedDocType) && this.documentTypes.length > 0) {
            this.selectedDocType = this.documentTypes[0];
          }
        }
      });
  }

  loadStaff(): void {
    this.isLoading = true;
    this.http.get<ApiResponse<StaffDetailResponse>>(`/api/staff/${this.staffId}`)
      .subscribe({
        next: (res) => {
          this.isLoading = false;
          if (res.success && res.data) {
            this.nextCode = res.data.staffCode;
            this.documents = res.data.documents || [];
            this.mapDetailToForm(res.data);
          }
        },
        error: () => { this.isLoading = false; }
      });
  }

  save(): void {
    if (!this.form.fullNameAr?.trim()) return;
    this.isSaving = true;

    const obs = this.isEdit
      ? this.http.put<ApiResponse<StaffDetailResponse>>(`/api/staff/${this.staffId}`, this.form)
      : this.http.post<ApiResponse<StaffDetailResponse>>('/api/staff', this.form);

    obs.subscribe({
      next: (res) => {
        this.isSaving = false;
        if (res.success && res.data) {
          this.router.navigate([this.backRoute, res.data.id]);
        }
      },
      error: () => { this.isSaving = false; }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files && input.files.length ? input.files[0] : null;
    this.documentError = '';
  }

  uploadDocument(): void {
    if (!this.selectedFile || !this.staffId) {
      this.documentError = this.translationService.translate('STAFF_FORM.SELECT_FILE_FIRST');
      return;
    }

    const formData = new FormData();
    formData.append('file', this.selectedFile);
    formData.append('documentType', this.selectedDocType);
    if (this.documentNote.trim()) {
      formData.append('note', this.documentNote.trim());
    }

    this.isUploading = true;
    this.documentError = '';

    this.http.post<ApiResponse<DocumentInfo>>(`/api/staff/${this.staffId}/documents`, formData)
      .subscribe({
        next: (res) => {
          this.isUploading = false;
          if (res.success && res.data) {
            this.documents = [...this.documents, res.data];
            this.selectedFile = null;
            this.documentNote = '';
            const input = document.getElementById('staffDocumentFile') as HTMLInputElement | null;
            if (input) { input.value = ''; }
          } else {
            this.documentError = res.message || this.translationService.translate('STAFF_FORM.UPLOAD_FAILED');
          }
        },
        error: (err) => {
          this.isUploading = false;
          this.documentError = err?.error?.message || this.translationService.translate('STAFF_FORM.UPLOAD_FAILED');
        }
      });
  }

  onDocumentDeleted(doc: DocumentInfo): void {
    this.documents = this.documents.filter(d => d.id !== doc.id);
  }

  getDocTypeLabel(type: string): string {
    return this.translationService.translate('DOC_TYPES.' + type) || type;
  }

  private mapDetailToForm(d: StaffDetailResponse): void {
    this.form = {
      staffType: d.staffType,
      jobTitle: d.jobTitle,
      fullNameAr: d.fullNameAr,
      fullNameEn: d.fullNameEn,
      gender: d.gender,
      nationality: d.nationality,
      dateOfBirth: d.dateOfBirth,
      phone: d.phone,
      placeOfResidence: d.placeOfResidence,
      homeAddress: d.homeAddress,
      email: d.email,
      passportNumber: d.passportNumber,
      passportIssueDate: d.passportIssueDate,
      passportExpiry: d.passportExpiry,
      passportStatus: d.passportStatus,
      iqamaNumber: d.iqamaNumber,
      iqamaOccupation: d.iqamaOccupation,
      iqamaExpiry: d.iqamaExpiry,
      iqamaExpiryRenewed: d.iqamaExpiryRenewed,
      iqamaStatus: d.iqamaStatus,
      qiwaContractNumber: d.qiwaContractNumber,
      establishmentNumber: d.establishmentNumber,
      qiwaContractStart: d.qiwaContractStart,
      qiwaContractEnd: d.qiwaContractEnd,
      qiwaContractStatus: d.qiwaContractStatus,
      workPermitExpiry: d.workPermitExpiry,
      workPermitStatus: d.workPermitStatus,
      affiliatedCompany: d.affiliatedCompany,
      crNumber: d.crNumber,
      insuranceNumber: d.insuranceNumber,
      insuranceCategory: d.insuranceCategory,
      insuranceIssueDate: d.insuranceIssueDate,
      insuranceExpiry: d.insuranceExpiry,
      insuranceCompany: d.insuranceCompany,
      insuranceStatus: d.insuranceStatus,
      trialPeriodDays: d.trialPeriodDays,
      hasLicence: d.hasLicence,
      licenceExpiry: d.licenceExpiry,
      licenceStatus: d.licenceStatus,
      bloodType: d.bloodType,
      bankAccountNumber: d.bankAccountNumber,
      bankName: d.bankName,
      basicSalary: d.basicSalary,
      housingAllowance: d.housingAllowance,
      transportationAllowance: d.transportationAllowance ?? null,
      fuelAllowanceAmount: d.fuelAllowanceAmount ?? null,
      callInternetAllowance: d.callInternetAllowance ?? null,
      rewardAllowance: d.rewardAllowance ?? null,
      otherAllowances: d.otherAllowances,
      status: d.status,
      joinDate: d.joinDate,
      leaveDate: d.leaveDate,
      notes: d.notes,
    };
  }

  private emptyForm(): StaffRequest {
    return {
      staffType: 'WORKER', jobTitle: null, fullNameAr: '', fullNameEn: null,
      gender: null, nationality: null, dateOfBirth: null, phone: null,
      placeOfResidence: null, homeAddress: null, email: null,
      passportNumber: null, passportIssueDate: null, passportExpiry: null, passportStatus: null,
      iqamaNumber: null, iqamaOccupation: null, iqamaExpiry: null, iqamaExpiryRenewed: null, iqamaStatus: null,
      qiwaContractNumber: null, establishmentNumber: null, qiwaContractStart: null, qiwaContractEnd: null, qiwaContractStatus: null,
      workPermitExpiry: null, workPermitStatus: null,
      affiliatedCompany: null, crNumber: null,
      insuranceNumber: null, insuranceCategory: null, insuranceIssueDate: null, insuranceExpiry: null, insuranceCompany: null, insuranceStatus: null,
      trialPeriodDays: null, hasLicence: false, licenceExpiry: null, licenceStatus: null,
      bloodType: null, bankAccountNumber: null, bankName: null,
      basicSalary: null, housingAllowance: null, transportationAllowance: null,
      fuelAllowanceAmount: null, callInternetAllowance: null, rewardAllowance: null,
      otherAllowances: null,
      status: 'ACTIVE', joinDate: null, leaveDate: null, notes: null,
    };
  }
}
