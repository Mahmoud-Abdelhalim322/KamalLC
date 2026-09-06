import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, PlanResponse, PlanRequest } from '../../../core/models/api.models';

@Component({
  selector: 'app-plan-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './plan-management.component.html',
  styleUrl: './plan-management.component.scss'
})
export class PlanManagementComponent implements OnInit {
  plans: PlanResponse[] = [];
  showModal = false;
  editingPlanId: string | null = null;
  submitting = false;
  successMessage: string | null = null;
  errorMessage: string | null = null;

  newPlan: PlanRequest = {
    name: '',
    nameAr: '',
    description: '',
    descriptionAr: '',
    monthlyPrice: 0,
    yearlyPrice: null,
    features: null,
    sortOrder: 1
  };

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchPlans();
  }

  fetchPlans(): void {
    this.http.get<ApiResponse<PlanResponse[]>>('/api/platform/plans')
      .subscribe(res => {
        if (res.success && res.data) this.plans = res.data;
      });
  }

  openCreateModal(): void {
    this.editingPlanId = null;
    this.newPlan = {
      name: '',
      nameAr: '',
      description: '',
      descriptionAr: '',
      monthlyPrice: 0,
      yearlyPrice: null,
      features: null,
      sortOrder: 1
    };
    this.showModal = true;
  }

  openEditModal(plan: PlanResponse): void {
    this.editingPlanId = plan.id;
    this.newPlan = {
      name: plan.name,
      nameAr: plan.nameAr,
      description: plan.description || '',
      descriptionAr: plan.descriptionAr || '',
      monthlyPrice: plan.monthlyPrice,
      yearlyPrice: plan.yearlyPrice,
      features: plan.features,
      sortOrder: plan.sortOrder || 1
    };
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
    this.editingPlanId = null;
  }

  submitPlan(): void {
    if (!this.newPlan.nameAr || !this.newPlan.name || this.newPlan.monthlyPrice == null) {
      this.showError('يرجى إدخال جميع البيانات المطلوبة للخطة');
      return;
    }

    this.submitting = true;
    if (this.editingPlanId) {
      this.http.put<ApiResponse<PlanResponse>>(`/api/platform/plans/${this.editingPlanId}`, this.newPlan)
        .subscribe({
          next: (res) => {
            this.submitting = false;
            if (res.success) {
              this.closeModal();
              this.fetchPlans();
              this.showFeedback('تم تحديث الخطة بنجاح');
            } else {
              this.showError(res.message || 'حدث خطأ أثناء تحديث الخطة');
            }
          },
          error: (err) => {
            this.submitting = false;
            this.showError(err.error?.message || 'حدث خطأ أثناء تحديث الخطة');
          }
        });
    } else {
      this.http.post<ApiResponse<PlanResponse>>('/api/platform/plans', this.newPlan)
        .subscribe({
          next: (res) => {
            this.submitting = false;
            if (res.success) {
              this.closeModal();
              this.fetchPlans();
              this.showFeedback('تم إنشاء الخطة بنجاح');
            } else {
              this.showError(res.message || 'حدث خطأ أثناء إنشاء الخطة');
            }
          },
          error: (err) => {
            this.submitting = false;
            this.showError(err.error?.message || 'حدث خطأ أثناء إنشاء الخطة');
          }
        });
    }
  }

  deletePlan(plan: PlanResponse): void {
    if (!confirm(`هل أنت تأكد من تعطيل الخطة "${plan.nameAr}"؟`)) return;
    this.http.delete<ApiResponse<void>>(`/api/platform/plans/${plan.id}`)
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.fetchPlans();
            this.showFeedback('تم تعطيل الخطة بنجاح');
          }
        },
        error: (err) => {
          this.showError(err.error?.message || 'حدث خطأ أثناء تعطيل الخطة');
        }
      });
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

