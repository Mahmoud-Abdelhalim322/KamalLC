import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LoanService } from '../../core/services/loan.service';
import { LoanMembersSummary, MemberLoanSummaryResponse } from '../../core/models/api.models';
import { PAGE_SIZE, errorMessage } from '../../core/services/api.util';
import { AuthService } from '../../core/auth/auth.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationService } from '../../core/i18n/translation.service';
import { InitialsPipe } from '../../shared/pipes/initials.pipe';
import { SarPipe } from '../../shared/pipes/sar.pipe';
import { PagerComponent } from '../../shared/components/pager/pager.component';

@Component({
  selector: 'app-loans',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, InitialsPipe, SarPipe, PagerComponent],
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
  summary: LoanMembersSummary | null = null;

  typeFilter: 'ALL' | 'CAPTAIN' | 'WORKER' | 'MANAGER' = 'ALL';
  searchTerm = '';

  readonly pageSize = PAGE_SIZE;
  page = 0;
  totalElements = 0;
  totalPages = 0;

  isLoading = true;
  errorMsg = '';

  private readonly search$ = new Subject<void>();

  constructor() {
    this.search$.pipe(debounceTime(300), takeUntilDestroyed(inject(DestroyRef))).subscribe(() => this.applyFilters());
  }

  ngOnInit(): void {
    this.loadMembers();
  }

  loadMembers(): void {
    this.isLoading = true;
    this.errorMsg = '';
    const type = this.typeFilter === 'ALL' ? undefined : this.typeFilter;
    this.loanService.getMembers({ type, search: this.searchTerm.trim() || undefined }, this.page, this.pageSize).subscribe({
      next: (res) => {
        this.isLoading = false;
        this.members = res.content;
        this.summary = res.summary;
        this.totalElements = res.totalElements;
        this.totalPages = res.totalPages;
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMsg = errorMessage(err, this.i18n.get('LOANS.LOAD_FAILED'));
      }
    });
  }

  /** Filters changed: back to the first page. */
  applyFilters(): void {
    this.page = 0;
    this.loadMembers();
  }

  onSearchInput(): void {
    this.search$.next();
  }

  goToPage(page: number): void {
    this.page = page;
    this.loadMembers();
  }

  openMember(member: MemberLoanSummaryResponse): void {
    this.router.navigate(['/loans', member.memberType, member.memberId]);
  }


  /** Share of the original amount already repaid, 0–100. */
  repaidPct(member: { totalOriginal: number; totalRemaining: number }): number {
    if (!member.totalOriginal) return 0;
    return Math.round(((member.totalOriginal - member.totalRemaining) / member.totalOriginal) * 100);
  }

  /** k-badge tone per member type. */
  typeTone(type: string): string {
    return type === 'CAPTAIN' ? 'brand' : type === 'WORKER' ? 'info' : 'purple';
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
