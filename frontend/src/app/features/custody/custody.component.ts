import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CustodyService } from '../../core/services/custody.service';
import { CaptainCustodySummary, CustodyRecordResponse, CustodySummaryTotals } from '../../core/models/api.models';
import { PAGE_SIZE, errorMessage } from '../../core/services/api.util';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationService } from '../../core/i18n/translation.service';
import { InitialsPipe } from '../../shared/pipes/initials.pipe';
import { PagerComponent } from '../../shared/components/pager/pager.component';

@Component({
  selector: 'app-custody',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, InitialsPipe, PagerComponent],
  templateUrl: './custody.component.html',
  styleUrls: ['./custody.component.scss']
})
export class CustodyComponent implements OnInit {
  private readonly http = inject(HttpClient);
  summaries: CaptainCustodySummary[] = [];
  totals: CustodySummaryTotals | null = null;
  searchQuery = '';

  readonly pageSize = PAGE_SIZE;
  page = 0;
  totalElements = 0;
  totalPages = 0;
  private readonly search$ = new Subject<void>();
  isLoading = false;
  selectedCaptainId: string | null = null;

  // Recent custody history of the selected captain (inline card)
  recentHistory: CustodyRecordResponse[] = [];

  // History dialog
  showHistoryDialog = false;
  historyCaptainName = '';
  historyRecords: CustodyRecordResponse[] = [];
  historyPage = 0;
  historyTotalPages = 0;
  historyTotalElements = 0;
  historyCaptainId = '';

  error = '';

  constructor(
    private custodyService: CustodyService,
    public i18n: TranslationService
  ) {
    this.search$.pipe(debounceTime(300), takeUntilDestroyed(inject(DestroyRef))).subscribe(() => {
      this.page = 0;
      this.loadSummaries();
    });
  }

  ngOnInit(): void { this.loadSummaries(); }

  loadSummaries(): void {
    this.isLoading = true;
    this.custodyService.getSummaries(this.searchQuery.trim() || undefined, this.page, this.pageSize).subscribe({
      next: res => {
        this.summaries = res.content;
        this.totals = res.summary;
        this.totalElements = res.totalElements;
        this.totalPages = res.totalPages;
        this.isLoading = false;
        if (!this.selected && res.content.length > 0) this.selectCaptain(res.content[0]);
      },
      error: e => { this.error = errorMessage(e, this.i18n.t('CUSTODY.ERR_LOAD_CUSTODY')); this.isLoading = false; }
    });
  }

  /** Search typed: wait for a pause, then reload from the first page. */
  applyFilter(): void {
    this.search$.next();
  }

  goToPage(page: number): void {
    this.page = page;
    this.loadSummaries();
  }

  /** Kept as its own object so the detail stays open while the list pages. */
  selected: CaptainCustodySummary | null = null;

  selectCaptain(s: CaptainCustodySummary): void {
    this.selectedCaptainId = s.captainId;
    this.selected = s;
    this.recentHistory = [];
    this.custodyService.getHistory(s.captainId, 0, 5).subscribe({
      next: p => this.recentHistory = p.content,
      error: () => this.recentHistory = []
    });
  }


  openHistory(s: CaptainCustodySummary): void {
    this.historyCaptainId = s.captainId;
    this.historyCaptainName = s.captainName;
    this.historyPage = 0;
    this.loadHistory();
  }

  loadHistory(): void {
    this.custodyService.getHistory(this.historyCaptainId, this.historyPage).subscribe({
      next: p => { this.historyRecords = p.content; this.historyTotalPages = p.totalPages; this.historyTotalElements = p.totalElements; this.showHistoryDialog = true; },
      error: e => this.error = errorMessage(e, this.i18n.t('CUSTODY.ERR_LOAD_HISTORY'))
    });
  }

  goToHistoryPage(page: number): void { this.historyPage = page; this.loadHistory(); }

  assetTypeIcon(type: string): string {
    return { VEHICLE: 'directions_car', SIM_CARD: 'sim_card', PHONE: 'phone_iphone' }[type] || 'category';
  }

  dismissMsg(): void { this.error = ''; }

  // ── Export ──

  isExporting = false;

  exportExcel(): void {
    this.isExporting = true;
    this.http.get('/api/custody/export/excel', { responseType: 'blob' }).subscribe({
      next: (blob: Blob) => {
        this.isExporting = false;
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = this.i18n.t('CUSTODY.EXPORT_FILENAME');
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => {
        this.isExporting = false;
        this.error = this.i18n.t('CUSTODY.ERR_EXPORT_EXCEL');
      }
    });
  }

  // ── Handover PDF ──

  exportingCaptainId: string | null = null;

  exportHandoverPdf(summary: CaptainCustodySummary): void {
    this.exportingCaptainId = summary.captainId;
    this.http.get(`/api/custody/handover/pdf/${summary.captainId}`, { responseType: 'blob' }).subscribe({
      next: (blob: Blob) => {
        this.exportingCaptainId = null;
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = this.i18n.t('CUSTODY.HANDOVER_FILENAME', { name: summary.captainName });
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => {
        this.exportingCaptainId = null;
        this.error = this.i18n.t('CUSTODY.ERR_EXPORT_PDF');
      }
    });
  }
}
