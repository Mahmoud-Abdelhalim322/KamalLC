import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import {
  ApiResponse, CaptainDetailResponse, CaptainRequest, DocumentInfo
} from '../../../core/models/api.models';
import { DocumentGalleryComponent } from '../../../shared/components/document-gallery/document-gallery.component';
import { isoDate } from '../../../core/services/api.util';
import { TranslationService } from '../../../core/i18n/translation.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/** Fallback labels for the document_type values defined in V3__create_captain_tables.sql. */
const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  PERSONAL_PHOTO: 'Personal Photo',
  ID_CARD: 'ID Card',
  IQAMA: 'Iqama',
  PASSPORT: 'Passport',
  DRIVING_LICENCE: 'Driving Licence',
  VEHICLE_REGISTRATION: 'Vehicle Registration',
  CONTRACT: 'Contract',
  OTHER: 'Other'
};

@Component({
  selector: 'app-captain-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink, DocumentGalleryComponent, TranslatePipe],
  templateUrl: './captain-form.component.html',
  styleUrls: ['./captain-form.component.scss']
})
export class CaptainFormComponent implements OnInit {
  form!: FormGroup;
  captainId: string | null = null;
  kamalCode = '';
  isLoading = false;
  isSaving = false;
  errorMessage = '';
  successMessage = '';

  // Documents (edit mode only — a captain must exist before files can be attached)
  documents: DocumentInfo[] = [];
  documentTypes = Object.keys(DOCUMENT_TYPE_LABELS);
  selectedDocType = 'IQAMA';
  selectedFile: File | null = null;
  documentNote = '';
  isUploading = false;
  documentError = '';

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private route: ActivatedRoute,
    private router: Router,
    public translationService: TranslationService
  ) {}

  get isEditMode(): boolean {
    return this.captainId !== null;
  }

  ngOnInit(): void {
    this.form = this.fb.group({
      captainType: ['EMPLOYEE', Validators.required],
      fullNameAr: ['', [Validators.required, Validators.maxLength(255)]],
      fullNameEn: [''],
      gender: [null],
      nationality: [''],
      dateOfBirth: [''],
      phone: [''],
      email: ['', Validators.email],
      placeOfResidence: [''],
      homeAddress: [''],
      jobTitle: [''],
      appId: [''],
      // Passport
      passportNumber: [''],
      passportIssueDate: [''],
      passportExpiry: [''],
      passportStatus: [null],
      // Iqama
      iqamaNumber: [''],
      iqamaOccupation: [''],
      idExpiryDate: [''],
      iqamaExpiry: [''],
      iqamaExpiryRenewed: [''],
      borderNumber: [''],
      iqamaStatus: [null],
      // Qiwa
      qiwaContractNumber: [''],
      establishmentNumber: [''],
      qiwaContractStart: [''],
      qiwaContractEnd: [''],
      qiwaContractStatus: [null],
      // Work Permit
      workPermitExpiry: [''],
      workPermitStatus: [null],
      // Company
      affiliatedCompany: [''],
      crNumber: [''],
      // Driving Licence
      drivingLicenceNo: [''],
      licenceType: [''],
      licenceExpiry: [''],
      hasLicence: [false],
      licenceStatus: [null],
      // Insurance
      insuranceNumber: [''],
      insuranceCategory: [''],
      insuranceIssueDate: [''],
      insuranceCompany: [''],
      insuranceExpiry: [''],
      insuranceStatus: [null],
      // Other
      bloodType: [null],
      trialPeriodDays: [null],
      // Bank
      bankAccountNumber: [''],
      bankName: [''],
      // Salary & Allowances
      basicSalary: [null],
      housingAllowance: [null],
      transportationAllowance: [null],
      fuelAllowanceAmount: [null],
      callInternetAllowance: [null],
      rewardAllowance: [null],
      salaryDeductions: [null],
      netSalary: [null],
      // Vehicle
      usesOwnVehicle: [false],
      fuelAllowanceEligible: [false],
      // Work Info
      workPhone: [''],
      department: [''],
      section: [''],
      supervisor: [''],
      workLocation: [''],
      workWith: [''],
      // Status
      status: ['ACTIVE'],
      joinDate: [this.today()],
      leaveDate: [''],
      notes: ['']
    });

    const salaryFields = [
      'basicSalary', 'housingAllowance', 'transportationAllowance',
      'fuelAllowanceAmount', 'callInternetAllowance', 'rewardAllowance', 'salaryDeductions'
    ];
    salaryFields.forEach(field => {
      this.form.get(field)?.valueChanges.subscribe(() => {
        this.updateNetSalary();
      });
    });

    this.captainId = this.route.snapshot.paramMap.get('id');

    if (this.isEditMode) {
      if (this.route.snapshot.queryParamMap.get('created') === '1') {
        this.successMessage = this.translationService.translate('CAPTAIN_FORM.CREATED_SUCCESS');
      }
      this.loadCaptain();
    } else {
      this.refreshNextCode();
    }
  }

  /**
   * The fuel allowance exists to cover the captain's own petrol, so it follows the
   * vehicle flag — the user can still untick it for an exception.
   */
  onOwnVehicleChange(): void {
    this.form.patchValue({ fuelAllowanceEligible: !!this.form.get('usesOwnVehicle')!.value });
  }

  /** In create mode the suggested code follows the selected type. */
  onTypeChange(): void {
    if (!this.isEditMode) {
      this.refreshNextCode();
    }
  }

  private refreshNextCode(): void {
    const type = this.form.get('captainType')!.value;
    this.http.get<ApiResponse<{ kamalCode: string }>>(`/api/captains/next-code?type=${type}`)
      .subscribe({
        next: (res) => {
          if (res.success && res.data) {
            this.kamalCode = res.data.kamalCode;
          }
        },
        error: () => { this.kamalCode = ''; }
      });
  }

  private updateNetSalary(): void {
    const basic = Number(this.form.get('basicSalary')?.value) || 0;
    const housing = Number(this.form.get('housingAllowance')?.value) || 0;
    const transport = Number(this.form.get('transportationAllowance')?.value) || 0;
    const fuel = Number(this.form.get('fuelAllowanceAmount')?.value) || 0;
    const comm = Number(this.form.get('callInternetAllowance')?.value) || 0;
    const reward = Number(this.form.get('rewardAllowance')?.value) || 0;
    const deductions = Number(this.form.get('salaryDeductions')?.value) || 0;
    const gross = basic + housing + transport + fuel + comm + reward;
    if (gross > 0 || deductions > 0) {
      const net = Math.round((gross - deductions) * 100) / 100;
      this.form.patchValue({ netSalary: net }, { emitEvent: false });
    }
  }

  private loadCaptain(): void {
    this.isLoading = true;
    this.http.get<ApiResponse<CaptainDetailResponse>>(`/api/captains/${this.captainId}`)
      .subscribe({
        next: (res) => {
          this.isLoading = false;
          if (res.success && res.data) {
            const c = res.data;
            this.kamalCode = c.kamalCode;
            this.documents = c.documents ?? [];
            this.form.patchValue({
              captainType: c.captainType,
              fullNameAr: c.fullNameAr,
              fullNameEn: c.fullNameEn ?? '',
              gender: c.gender ?? null,
              nationality: c.nationality ?? '',
              dateOfBirth: c.dateOfBirth ?? '',
              phone: c.phone ?? '',
              email: c.email ?? '',
              placeOfResidence: c.placeOfResidence ?? '',
              homeAddress: c.homeAddress ?? '',
              jobTitle: c.jobTitle ?? '',
              appId: c.appId ?? '',
              passportNumber: c.passportNumber ?? '',
              passportIssueDate: c.passportIssueDate ?? '',
              passportExpiry: c.passportExpiry ?? '',
              passportStatus: c.passportStatus ?? null,
              iqamaNumber: c.iqamaNumber ?? '',
              iqamaOccupation: c.iqamaOccupation ?? '',
              idExpiryDate: c.idExpiryDate ?? '',
              iqamaExpiry: c.iqamaExpiry ?? '',
              iqamaExpiryRenewed: c.iqamaExpiryRenewed ?? '',
              borderNumber: c.borderNumber ?? '',
              iqamaStatus: c.iqamaStatus ?? null,
              qiwaContractNumber: c.qiwaContractNumber ?? '',
              establishmentNumber: c.establishmentNumber ?? '',
              qiwaContractStart: c.qiwaContractStart ?? '',
              qiwaContractEnd: c.qiwaContractEnd ?? '',
              qiwaContractStatus: c.qiwaContractStatus ?? null,
              workPermitExpiry: c.workPermitExpiry ?? '',
              workPermitStatus: c.workPermitStatus ?? null,
              affiliatedCompany: c.affiliatedCompany ?? '',
              crNumber: c.crNumber ?? '',
              drivingLicenceNo: c.drivingLicenceNo ?? '',
              licenceType: c.licenceType ?? '',
              licenceExpiry: c.licenceExpiry ?? '',
              hasLicence: c.hasLicence ?? false,
              licenceStatus: c.licenceStatus ?? null,
              insuranceNumber: c.insuranceNumber ?? '',
              insuranceCategory: c.insuranceCategory ?? '',
              insuranceIssueDate: c.insuranceIssueDate ?? '',
              insuranceCompany: c.insuranceCompany ?? '',
              insuranceExpiry: c.insuranceExpiry ?? '',
              insuranceStatus: c.insuranceStatus ?? null,
              bloodType: c.bloodType ?? null,
              trialPeriodDays: c.trialPeriodDays ?? null,
              bankAccountNumber: c.bankAccountNumber ?? '',
              bankName: c.bankName ?? '',
              basicSalary: c.basicSalary ?? null,
              housingAllowance: c.housingAllowance ?? null,
              transportationAllowance: c.transportationAllowance ?? null,
              fuelAllowanceAmount: c.fuelAllowanceAmount ?? null,
              callInternetAllowance: c.callInternetAllowance ?? null,
              rewardAllowance: c.rewardAllowance ?? null,
              salaryDeductions: c.salaryDeductions ?? null,
              netSalary: c.netSalary ?? null,
              usesOwnVehicle: c.usesOwnVehicle,
              fuelAllowanceEligible: c.fuelAllowanceEligible,
              workPhone: c.workPhone ?? '',
              department: c.department ?? '',
              section: c.section ?? '',
              supervisor: c.supervisor ?? '',
              workLocation: c.workLocation ?? '',
              workWith: c.workWith ?? '',
              status: c.status,
              joinDate: c.joinDate ?? '',
              leaveDate: c.leaveDate ?? '',
              notes: c.notes ?? ''
            });
          } else {
            this.errorMessage = this.translationService.translate('CAPTAIN_FORM.LOAD_FAILED');
          }
        },
        error: () => {
          this.isLoading = false;
          this.errorMessage = this.translationService.translate('CAPTAIN_FORM.LOAD_FAILED');
        }
      });
  }

  save(): void {
    this.errorMessage = '';
    this.successMessage = '';

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage = this.translationService.translate('CAPTAIN_FORM.FILL_REQUIRED');
      return;
    }

    this.isSaving = true;
    const payload = this.toRequest();

    const request$ = this.isEditMode
      ? this.http.put<ApiResponse<CaptainDetailResponse>>(`/api/captains/${this.captainId}`, payload)
      : this.http.post<ApiResponse<CaptainDetailResponse>>('/api/captains', payload);

    request$.subscribe({
      next: (res) => {
        this.isSaving = false;
        if (!res.success || !res.data) {
          this.errorMessage = res.message || this.translationService.translate('CAPTAIN_FORM.SAVE_FAILED');
          return;
        }

        if (this.isEditMode) {
          this.kamalCode = res.data.kamalCode;
          this.documents = res.data.documents ?? [];
          this.successMessage = res.message || this.translationService.translate('CAPTAIN_FORM.SAVE_SUCCESS');
        } else {
          // Documents can only be attached once the captain exists — continue in edit mode.
          this.router.navigate(['/captains', res.data.id, 'edit'], {
            queryParams: { created: '1' }
          });
        }
      },
      error: (err) => {
        this.isSaving = false;
        this.errorMessage = err?.error?.message || this.translationService.translate('CAPTAIN_FORM.SAVE_FAILED');
      }
    });
  }

  private toRequest(): CaptainRequest {
    const v = this.form.value;
    const orNull = (value: string) => (value && value.trim() ? value.trim() : null);

    return {
      captainType: v.captainType,
      fullNameAr: v.fullNameAr.trim(),
      fullNameEn: orNull(v.fullNameEn),
      gender: v.gender || null,
      nationality: orNull(v.nationality),
      dateOfBirth: orNull(v.dateOfBirth),
      phone: orNull(v.phone),
      email: orNull(v.email),
      placeOfResidence: orNull(v.placeOfResidence),
      homeAddress: orNull(v.homeAddress),
      jobTitle: orNull(v.jobTitle),
      appId: orNull(v.appId),
      passportNumber: orNull(v.passportNumber),
      passportIssueDate: orNull(v.passportIssueDate),
      passportExpiry: orNull(v.passportExpiry),
      passportStatus: v.passportStatus || null,
      iqamaNumber: orNull(v.iqamaNumber),
      iqamaOccupation: orNull(v.iqamaOccupation),
      idExpiryDate: orNull(v.idExpiryDate),
      iqamaExpiry: orNull(v.iqamaExpiry),
      iqamaExpiryRenewed: orNull(v.iqamaExpiryRenewed),
      borderNumber: orNull(v.borderNumber),
      iqamaStatus: v.iqamaStatus || null,
      qiwaContractNumber: orNull(v.qiwaContractNumber),
      establishmentNumber: orNull(v.establishmentNumber),
      qiwaContractStart: orNull(v.qiwaContractStart),
      qiwaContractEnd: orNull(v.qiwaContractEnd),
      qiwaContractStatus: v.qiwaContractStatus || null,
      workPermitExpiry: orNull(v.workPermitExpiry),
      workPermitStatus: v.workPermitStatus || null,
      affiliatedCompany: orNull(v.affiliatedCompany),
      crNumber: orNull(v.crNumber),
      drivingLicenceNo: orNull(v.drivingLicenceNo),
      licenceType: orNull(v.licenceType),
      licenceExpiry: orNull(v.licenceExpiry),
      hasLicence: !!v.hasLicence,
      licenceStatus: v.licenceStatus || null,
      insuranceNumber: orNull(v.insuranceNumber),
      insuranceCategory: orNull(v.insuranceCategory),
      insuranceIssueDate: orNull(v.insuranceIssueDate),
      insuranceCompany: orNull(v.insuranceCompany),
      insuranceExpiry: orNull(v.insuranceExpiry),
      insuranceStatus: v.insuranceStatus || null,
      bloodType: v.bloodType || null,
      trialPeriodDays: v.trialPeriodDays ?? null,
      bankAccountNumber: orNull(v.bankAccountNumber),
      bankName: orNull(v.bankName),
      basicSalary: v.basicSalary ?? null,
      housingAllowance: v.housingAllowance ?? null,
      transportationAllowance: v.transportationAllowance ?? null,
      fuelAllowanceAmount: v.fuelAllowanceAmount ?? null,
      callInternetAllowance: v.callInternetAllowance ?? null,
      rewardAllowance: v.rewardAllowance ?? null,
      salaryDeductions: v.salaryDeductions ?? null,
      netSalary: v.netSalary ?? null,
      usesOwnVehicle: !!v.usesOwnVehicle,
      fuelAllowanceEligible: !!v.fuelAllowanceEligible,
      workPhone: orNull(v.workPhone),
      department: orNull(v.department),
      section: orNull(v.section),
      supervisor: orNull(v.supervisor),
      workLocation: orNull(v.workLocation),
      workWith: orNull(v.workWith),
      status: v.status,
      joinDate: orNull(v.joinDate),
      leaveDate: orNull(v.leaveDate),
      notes: orNull(v.notes)
    };
  }

  // ---------- Documents ----------

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files && input.files.length ? input.files[0] : null;
    this.documentError = '';
  }

  uploadDocument(): void {
    if (!this.selectedFile || !this.captainId) {
      this.documentError = this.translationService.translate('CAPTAIN_FORM.SELECT_FILE_FIRST');
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

    this.http.post<ApiResponse<DocumentInfo>>(`/api/captains/${this.captainId}/documents`, formData)
      .subscribe({
        next: (res) => {
          this.isUploading = false;
          if (res.success && res.data) {
            this.documents = [...this.documents, res.data];
            this.selectedFile = null;
            this.documentNote = '';
            // Clear the native input so the same file can be picked again.
            const input = document.getElementById('documentFile') as HTMLInputElement | null;
            if (input) { input.value = ''; }
          } else {
            this.documentError = res.message || this.translationService.translate('CAPTAIN_FORM.UPLOAD_FAILED');
          }
        },
        error: (err) => {
          this.isUploading = false;
          this.documentError = err?.error?.message || this.translationService.translate('CAPTAIN_FORM.UPLOAD_FAILED');
        }
      });
  }

  /** The gallery performs the delete itself; keep our copy of the list in step. */
  onDocumentDeleted(doc: DocumentInfo): void {
    this.documents = this.documents.filter(d => d.id !== doc.id);
  }

  // ---------- Display helpers ----------

  getDocTypeLabel(type: string): string {
    const key = 'DOC_TYPES.' + type;
    const trans = this.translationService.translate(key);
    return trans !== key ? trans : (DOCUMENT_TYPE_LABELS[type] || type);
  }

  isInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!control && control.invalid && (control.dirty || control.touched);
  }

  private today(): string {
    return isoDate();
  }
}
