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

/** Arabic labels for the document_type values defined in V3__create_captain_tables.sql. */
const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  PERSONAL_PHOTO: 'صورة شخصية',
  ID_CARD: 'بطاقة هوية',
  IQAMA: 'إقامة',
  PASSPORT: 'جواز سفر',
  DRIVING_LICENCE: 'رخصة قيادة',
  VEHICLE_REGISTRATION: 'استمارة مركبة',
  CONTRACT: 'عقد عمل',
  OTHER: 'أخرى'
};

@Component({
  selector: 'app-captain-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink, DocumentGalleryComponent],
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
    private router: Router
  ) {}

  get isEditMode(): boolean {
    return this.captainId !== null;
  }

  ngOnInit(): void {
    this.form = this.fb.group({
      captainType: ['EMPLOYEE', Validators.required],
      fullNameAr: ['', [Validators.required, Validators.maxLength(255)]],
      fullNameEn: [''],
      nationality: [''],
      dateOfBirth: [''],
      phone: [''],
      email: ['', Validators.email],
      iqamaNumber: [''],
      iqamaExpiry: [''],
      passportNumber: [''],
      passportExpiry: [''],
      drivingLicenceNo: [''],
      licenceExpiry: [''],
      workPermitExpiry: [''],
      insuranceNumber: [''],
      insuranceCompany: [''],
      insuranceExpiry: [''],
      usesOwnVehicle: [false],
      fuelAllowanceEligible: [false],
      status: ['ACTIVE'],
      joinDate: [this.today()],
      leaveDate: [''],
      notes: ['']
    });

    this.captainId = this.route.snapshot.paramMap.get('id');

    if (this.isEditMode) {
      if (this.route.snapshot.queryParamMap.get('created') === '1') {
        this.successMessage = 'تم إضافة الكابتن بنجاح — يمكنك الآن رفع مستنداته';
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
              nationality: c.nationality ?? '',
              dateOfBirth: c.dateOfBirth ?? '',
              phone: c.phone ?? '',
              email: c.email ?? '',
              iqamaNumber: c.iqamaNumber ?? '',
              iqamaExpiry: c.iqamaExpiry ?? '',
              passportNumber: c.passportNumber ?? '',
              passportExpiry: c.passportExpiry ?? '',
              drivingLicenceNo: c.drivingLicenceNo ?? '',
              licenceExpiry: c.licenceExpiry ?? '',
              workPermitExpiry: c.workPermitExpiry ?? '',
              insuranceNumber: c.insuranceNumber ?? '',
              insuranceCompany: c.insuranceCompany ?? '',
              insuranceExpiry: c.insuranceExpiry ?? '',
              usesOwnVehicle: c.usesOwnVehicle,
              fuelAllowanceEligible: c.fuelAllowanceEligible,
              status: c.status,
              joinDate: c.joinDate ?? '',
              leaveDate: c.leaveDate ?? '',
              notes: c.notes ?? ''
            });
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

  save(): void {
    this.errorMessage = '';
    this.successMessage = '';

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.errorMessage = 'يرجى تعبئة الحقول المطلوبة بشكل صحيح';
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
          this.errorMessage = res.message || 'فشل حفظ البيانات';
          return;
        }

        if (this.isEditMode) {
          this.kamalCode = res.data.kamalCode;
          this.documents = res.data.documents ?? [];
          this.successMessage = res.message || 'تم حفظ البيانات بنجاح';
        } else {
          // Documents can only be attached once the captain exists — continue in edit mode.
          this.router.navigate(['/captains', res.data.id, 'edit'], {
            queryParams: { created: '1' }
          });
        }
      },
      error: (err) => {
        this.isSaving = false;
        this.errorMessage = err?.error?.message || 'فشل حفظ البيانات';
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
      nationality: orNull(v.nationality),
      dateOfBirth: orNull(v.dateOfBirth),
      phone: orNull(v.phone),
      email: orNull(v.email),
      iqamaNumber: orNull(v.iqamaNumber),
      iqamaExpiry: orNull(v.iqamaExpiry),
      passportNumber: orNull(v.passportNumber),
      passportExpiry: orNull(v.passportExpiry),
      drivingLicenceNo: orNull(v.drivingLicenceNo),
      licenceExpiry: orNull(v.licenceExpiry),
      workPermitExpiry: orNull(v.workPermitExpiry),
      insuranceNumber: orNull(v.insuranceNumber),
      insuranceCompany: orNull(v.insuranceCompany),
      insuranceExpiry: orNull(v.insuranceExpiry),
      usesOwnVehicle: !!v.usesOwnVehicle,
      fuelAllowanceEligible: !!v.fuelAllowanceEligible,
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
      this.documentError = 'يرجى اختيار ملف أولاً';
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
            this.documentError = res.message || 'فشل رفع المستند';
          }
        },
        error: (err) => {
          this.isUploading = false;
          this.documentError = err?.error?.message || 'فشل رفع المستند';
        }
      });
  }

  /** The gallery performs the delete itself; keep our copy of the list in step. */
  onDocumentDeleted(doc: DocumentInfo): void {
    this.documents = this.documents.filter(d => d.id !== doc.id);
  }

  // ---------- Display helpers ----------

  getDocTypeLabel(type: string): string {
    return DOCUMENT_TYPE_LABELS[type] || type;
  }

  isInvalid(field: string): boolean {
    const control = this.form.get(field);
    return !!control && control.invalid && (control.dirty || control.touched);
  }

  private today(): string {
    return isoDate();
  }
}
