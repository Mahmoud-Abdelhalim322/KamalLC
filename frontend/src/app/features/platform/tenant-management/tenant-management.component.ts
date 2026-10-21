import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, TenantResponse, CreateTenantRequest, UpdateTenantRequest, PlanResponse, SummaryPage, TenantListSummary } from '../../../core/models/api.models';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { TranslationService } from '../../../core/i18n/translation.service';
import { InitialsPipe } from '../../../shared/pipes/initials.pipe';
import { PagerComponent } from '../../../shared/components/pager/pager.component';
import { PAGE_SIZE } from '../../../core/services/api.util';


type TenantFilter = 'ALL' | 'ACTIVE' | 'TRIAL' | 'SUSPENDED';

@Component({
  selector: 'app-tenant-management',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, InitialsPipe, PagerComponent],
  templateUrl: './tenant-management.component.html',
  styleUrl: './tenant-management.component.scss'
})
export class TenantManagementComponent implements OnInit {
  tenants: TenantResponse[] = [];
  summary: TenantListSummary | null = null;
  filter: TenantFilter = 'ALL';
  search = '';

  readonly pageSize = PAGE_SIZE;
  page = 0;
  totalElements = 0;
  totalPages = 0;
  private readonly search$ = new Subject<void>();
  plans: PlanResponse[] = [];
  loading = true;
  showCreateModal = false;
  showEditModal = false;
  showPasswordModal = false;
  submitting = false;
  selectedTenant: TenantResponse | null = null;
  successMessage: string | null = null;
  errorMessage: string | null = null;

  newTenant: CreateTenantRequest = {
    name: '',
    slug: '',
    contactEmail: '',
    contactPhone: '',
    address: '',
    logo: '',
    primaryColor: '#2563EB',
    secondaryColor: '#475569',
    adminUsername: '',
    adminPassword: '',
    adminDisplayName: '',
    planId: null,
    billingCycle: 'MONTHLY'
  };

  editForm: UpdateTenantRequest = {
    name: '',
    contactEmail: '',
    contactPhone: '',
    address: '',
    logo: '',
    primaryColor: '#2563EB',
    secondaryColor: '#475569',
    newPassword: ''
  };

  passwordForm = {
    newPassword: '',
    confirmPassword: ''
  };

  constructor(private http: HttpClient, public i18n: TranslationService) {
    this.search$.pipe(debounceTime(300), takeUntilDestroyed(inject(DestroyRef))).subscribe(() => {
      this.page = 0;
      this.fetchTenants();
    });
  }

  ngOnInit(): void {
    this.fetchTenants();
    this.fetchPlans();
  }
  setFilter(filter: typeof this.filter): void {
    this.filter = filter;
    this.page = 0;
    this.fetchTenants();
  }

  onSearchInput(): void {
    this.search$.next();
  }

  goToPage(page: number): void {
    this.page = page;
    this.fetchTenants();
  }


  fetchTenants(): void {
    this.loading = true;
    this.http.get<ApiResponse<SummaryPage<TenantResponse, TenantListSummary>>>('/api/platform/tenants', {
      params: { page: this.page, size: this.pageSize, filter: this.filter, search: this.search.trim() }
    })
      .subscribe({
        next: (res) => {
          if (res.success && res.data) {
            this.tenants = res.data.content;
            this.summary = res.data.summary;
            this.totalElements = res.data.totalElements;
            this.totalPages = res.data.totalPages;
          }
          this.loading = false;
        },
        error: () => this.loading = false
      });
  }

  fetchPlans(): void {
    this.http.get<ApiResponse<PlanResponse[]>>('/api/platform/plans')
      .subscribe(res => {
        if (res.success && res.data) this.plans = res.data;
      });
  }

  openCreateModal(): void {
    this.showCreateModal = true;
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
  }

  submitCreateTenant(): void {
    this.submitting = true;
    this.http.post<ApiResponse<TenantResponse>>('/api/platform/tenants', this.newTenant)
      .subscribe({
        next: (res) => {
          this.submitting = false;
          if (res.success) {
            this.closeCreateModal();
            this.fetchTenants();
            this.showFeedback(this.i18n.t('PLATFORM.TENANT_CREATED_SUCCESS'));
          }
        },
        error: (err) => {
          this.submitting = false;
          this.showError(err.error?.message || this.i18n.t('PLATFORM.CREATE_FAILED'));
        }
      });
  }

  openEditModal(tenant: TenantResponse): void {
    this.selectedTenant = tenant;
    this.editForm = {
      name: tenant.name,
      contactEmail: tenant.contactEmail || '',
      contactPhone: tenant.contactPhone || '',
      address: tenant.address || '',
      logo: tenant.logo || '',
      primaryColor: tenant.primaryColor || '#2563EB',
      secondaryColor: tenant.secondaryColor || '#475569',
      newPassword: ''
    };
    this.showEditModal = true;
  }

  closeEditModal(): void {
    this.showEditModal = false;
    this.selectedTenant = null;
  }

  submitEditTenant(): void {
    if (!this.selectedTenant) return;
    this.submitting = true;
    this.http.put<ApiResponse<TenantResponse>>(`/api/platform/tenants/${this.selectedTenant.id}`, this.editForm)
      .subscribe({
        next: (res) => {
          this.submitting = false;
          if (res.success) {
            this.closeEditModal();
            this.fetchTenants();
            this.showFeedback(this.i18n.t('PLATFORM.TENANT_UPDATED_SUCCESS'));
          }
        },
        error: (err) => {
          this.submitting = false;
          this.showError(err.error?.message || this.i18n.t('PLATFORM.UPDATE_FAILED'));
        }
      });
  }

  openPasswordModal(tenant: TenantResponse): void {
    this.selectedTenant = tenant;
    this.passwordForm = { newPassword: '', confirmPassword: '' };
    this.showPasswordModal = true;
  }

  closePasswordModal(): void {
    this.showPasswordModal = false;
    this.selectedTenant = null;
  }

  submitChangePassword(): void {
    if (!this.selectedTenant) return;
    if (this.passwordForm.newPassword !== this.passwordForm.confirmPassword) {
      this.showError(this.i18n.t('PLATFORM.PWD_MISMATCH'));
      return;
    }
    if (this.passwordForm.newPassword.length < 4) {
      this.showError(this.i18n.t('PLATFORM.PWD_MIN_LENGTH'));
      return;
    }

    this.submitting = true;
    this.http.put<ApiResponse<void>>(`/api/platform/tenants/${this.selectedTenant.id}/password`, {
      newPassword: this.passwordForm.newPassword
    }).subscribe({
      next: (res) => {
        this.submitting = false;
        if (res.success) {
          this.closePasswordModal();
          this.showFeedback(this.i18n.t('PLATFORM.PWD_CHANGED_SUCCESS'));
        }
      },
      error: (err) => {
        this.submitting = false;
        this.showError(err.error?.message || this.i18n.t('PLATFORM.PWD_CHANGE_FAILED'));
      }
    });
  }

  toggleTenantStatus(tenant: TenantResponse): void {
    const newStatus = tenant.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    this.http.patch<ApiResponse<TenantResponse>>(`/api/platform/tenants/${tenant.id}/status`, { status: newStatus })
      .subscribe(res => {
        if (res.success) {
          this.fetchTenants();
          this.showFeedback(newStatus === 'ACTIVE' ? this.i18n.t('PLATFORM.TENANT_ACTIVATED') : this.i18n.t('PLATFORM.TENANT_SUSPENDED_MSG'));
        }
      });
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE': return this.i18n.t('PLATFORM.STATUS_ACTIVE');
      case 'SUSPENDED': return this.i18n.t('PLATFORM.STATUS_SUSPENDED');
      case 'TRIAL': return this.i18n.t('PLATFORM.STATUS_TRIAL');
      default: return status;
    }
  }

  getSubscriptionStatusLabel(status: string | null): string {
    if (!status) return this.i18n.t('PLATFORM.UNREGISTERED');
    switch (status) {
      case 'ACTIVE': return this.i18n.t('PLATFORM.STATUS_ACTIVE');
      case 'EXPIRED': return this.i18n.t('PLATFORM.STATUS_EXPIRED');
      case 'SUSPENDED': return this.i18n.t('PLATFORM.STATUS_SUSPENDED');
      case 'TRIAL': return this.i18n.t('PLATFORM.STATUS_TRIAL');
      default: return status;
    }
  }

  getStatusBadgeClass(status: string): string {
    return status === 'ACTIVE' ? 'badge-active' : 'badge-suspended';
  }

  getSubscriptionBadgeClass(status: string | null): string {
    return status === 'ACTIVE' ? 'badge-active' : 'badge-suspended';
  }

  subTone(status: string | null): string {
    return status === 'ACTIVE' ? 'success' : status === 'TRIAL' ? 'warning' : status ? 'danger' : '';
  }


  private showFeedback(msg: string): void {
    this.successMessage = msg;
    setTimeout(() => this.successMessage = null, 4000);
  }

  private showError(msg: string): void {
    this.errorMessage = msg;
    setTimeout(() => this.errorMessage = null, 4000);
  }
}

