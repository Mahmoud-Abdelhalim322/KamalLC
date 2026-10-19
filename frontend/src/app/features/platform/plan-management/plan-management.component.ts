import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, PlanResponse, PlanRequest } from '../../../core/models/api.models';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { TranslationService } from '../../../core/i18n/translation.service';

@Component({
  selector: 'app-plan-management',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
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

  constructor(private http: HttpClient, public i18n: TranslationService) {}

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
      this.showError(this.i18n.t('PLATFORM.FILL_REQUIRED'));
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
              this.showFeedback(this.i18n.t('PLATFORM.PLAN_UPDATED'));
            } else {
              this.showError(res.message || this.i18n.t('PLATFORM.PLAN_UPDATE_FAILED'));
            }
          },
          error: (err) => {
            this.submitting = false;
            this.showError(err.error?.message || this.i18n.t('PLATFORM.PLAN_UPDATE_FAILED'));
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
              this.showFeedback(this.i18n.t('PLATFORM.PLAN_CREATED'));
            } else {
              this.showError(res.message || this.i18n.t('PLATFORM.PLAN_CREATE_FAILED'));
            }
          },
          error: (err) => {
            this.submitting = false;
            this.showError(err.error?.message || this.i18n.t('PLATFORM.PLAN_CREATE_FAILED'));
          }
        });
    }
  }

  deletePlan(plan: PlanResponse): void {
    const planName = this.i18n.currentLang() === 'ar' ? (plan.nameAr || plan.name) : (plan.name || plan.nameAr);
    const confirmMsg = this.i18n.t('PLATFORM.CONFIRM_DISABLE_PLAN', { name: planName });
    if (!confirm(confirmMsg)) return;

    this.http.delete<ApiResponse<void>>(`/api/platform/plans/${plan.id}`)
      .subscribe({
        next: (res) => {
          if (res.success) {
            this.fetchPlans();
            this.showFeedback(this.i18n.t('PLATFORM.PLAN_DISABLED'));
          }
        },
        error: (err) => {
          this.showError(err.error?.message || this.i18n.t('PLATFORM.PLAN_DISABLE_FAILED'));
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

