import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, TenantResponse, CreateTenantRequest, UpdateTenantRequest, PlanResponse } from '../../../core/models/api.models';

@Component({
  selector: 'app-tenant-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tenant-management.component.html',
  styleUrl: './tenant-management.component.scss'
})
export class TenantManagementComponent implements OnInit {
  tenants: TenantResponse[] = [];
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

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchTenants();
    this.fetchPlans();
  }

  fetchTenants(): void {
    this.loading = true;
    this.http.get<ApiResponse<TenantResponse[]>>('/api/platform/tenants')
      .subscribe({
        next: (res) => {
          if (res.success && res.data) this.tenants = res.data;
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
            this.showFeedback('تم إنشاء حساب الشركة بنجاح');
          }
        },
        error: (err) => {
          this.submitting = false;
          this.showError(err.error?.message || 'حدث خطأ أثناء إنشاء الشركة');
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
            this.showFeedback('تم تحديث بيانات الشركة بنجاح');
          }
        },
        error: (err) => {
          this.submitting = false;
          this.showError(err.error?.message || 'حدث خطأ أثناء تحديث بيانات الشركة');
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
      this.showError('كلمتا المرور غير متطابقتين');
      return;
    }
    if (this.passwordForm.newPassword.length < 4) {
      this.showError('كلمة المرور يجب أن تكون 4 أحرف على الأقل');
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
          this.showFeedback('تم تغيير كلمة المرور لمدير الشركة بنجاح');
        }
      },
      error: (err) => {
        this.submitting = false;
        this.showError(err.error?.message || 'حدث خطأ أثناء تغيير كلمة المرور');
      }
    });
  }

  toggleTenantStatus(tenant: TenantResponse): void {
    const newStatus = tenant.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    this.http.patch<ApiResponse<TenantResponse>>(`/api/platform/tenants/${tenant.id}/status`, { status: newStatus })
      .subscribe(res => {
        if (res.success) {
          this.fetchTenants();
          this.showFeedback(newStatus === 'ACTIVE' ? 'تم تفعيل الشركة' : 'تم تعطيل الشركة');
        }
      });
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'ACTIVE': return 'نشط';
      case 'SUSPENDED': return 'معطل';
      case 'TRIAL': return 'تجريبي';
      default: return status;
    }
  }

  getStatusBadgeClass(status: string): string {
    return status === 'ACTIVE' ? 'badge-active' : 'badge-suspended';
  }

  getSubscriptionBadgeClass(status: string | null): string {
    return status === 'ACTIVE' ? 'badge-active' : 'badge-suspended';
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

