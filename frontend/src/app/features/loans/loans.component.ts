import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LoanService } from '../../core/services/loan.service';
import { MemberLoanSummaryResponse } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api.util';
import { AuthService } from '../../core/auth/auth.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationService } from '../../core/i18n/translation.service';

@Component({
  selector: 'app-loans',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './loans.component.html',
  styleUrls: ['./loans.component.scss']
})
export class LoansComponent implements OnInit {

  private loanService = inject(LoanService);
  private router = inject(Router);
  public authService = inject(AuthService);
  private i18n = inject(TranslationService);

  get canManage(): boolean {
    return this.authService.hasPermission('LOANS_MANAGE');
  }

  members: MemberLoanSummaryResponse[] = [];
  filteredMembers: MemberLoanSummaryResponse[] = [];

  typeFilter: 'ALL' | 'CAPTAIN' | 'WORKER' | 'MANAGER' = 'ALL';
  searchTerm = '';

  isLoading = true;
  errorMsg = '';

  ngOnInit(): void {
    this.loadMembers();
  }

  loadMembers(): void {
    this.isLoading = true;
    this.errorMsg = '';
    this.loanService.getMembers().subscribe({
      next: (data) => {
        this.isLoading = false;
        this.members = data;
        this.applyFilters();
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMsg = errorMessage(err, this.i18n.get('LOANS.LOAD_FAILED'));
      }
    });
  }

  applyFilters(): void {
    let result = this.members;

    if (this.typeFilter !== 'ALL') {
      result = result.filter(m => m.memberType === this.typeFilter);
    }

    if (this.searchTerm.trim()) {
      const term = this.searchTerm.trim().toLowerCase();
      result = result.filter(m =>
        m.memberName.toLowerCase().includes(term) ||
        m.memberCode.toLowerCase().includes(term) ||
        (m.memberNameEn && m.memberNameEn.toLowerCase().includes(term))
      );
    }

    this.filteredMembers = result;
  }

  openMember(member: MemberLoanSummaryResponse): void {
    this.router.navigate(['/loans', member.memberType, member.memberId]);
  }

  get totals() {
    return this.filteredMembers.reduce((acc, m) => ({
      activeLoanCount: acc.activeLoanCount + m.activeLoanCount,
      totalRemaining: acc.totalRemaining + m.totalRemaining,
      totalOriginal: acc.totalOriginal + m.totalOriginal,
    }), { activeLoanCount: 0, totalRemaining: 0, totalOriginal: 0 });
  }

  get membersWithLoans(): number {
    return this.filteredMembers.filter(m => m.activeLoanCount > 0).length;
  }

  memberTypeLabel(type: string): string {
    const map: Record<string, string> = {
      CAPTAIN: 'LOANS.TYPE_CAPTAIN',
      WORKER: 'LOANS.TYPE_WORKER',
      MANAGER: 'LOANS.TYPE_MANAGER'
    };
    return map[type] ? this.i18n.get(map[type]) : type;
  }

  money(value: number | null | undefined): string {
    if (value == null) return '—';
    return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
}
