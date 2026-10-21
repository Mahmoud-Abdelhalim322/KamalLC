import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { CustodyService } from '../../core/services/custody.service';
import { CaptainCustodySummary, CustodyRecordResponse } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api.util';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationService } from '../../core/i18n/translation.service';
import { InitialsPipe } from '../../shared/pipes/initials.pipe';

@Component({
  selector: 'app-custody',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, InitialsPipe],
  templateUrl: './custody.component.html',
  styleUrls: ['./custody.component.scss']
})
export class CustodyComponent implements OnInit {
  private readonly http = inject(HttpClient);
  summaries: CaptainCustodySummary[] = [];
  filteredSummaries: CaptainCustodySummary[] = [];
  searchQuery = '';
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
  historyCaptainId = '';

  error = '';

  constructor(
    private custodyService: CustodyService,
    public i18n: TranslationService
  ) {}

  ngOnInit(): void { this.loadSummaries(); }

  loadSummaries(): void {
    this.isLoading = true;
    this.custodyService.getSummaries().subscribe({
      next: data => {
        this.summaries = data;
        this.applyFilter();
        this.isLoading = false;
        if (!this.selected && data.length > 0) this.selectCaptain(data[0]);
      },
      error: e => { this.error = errorMessage(e, this.i18n.t('CUSTODY.ERR_LOAD_CUSTODY')); this.isLoading = false; }
    });
  }

  applyFilter(): void {
    if (!this.searchQuery) {
      this.filteredSummaries = this.summaries;
    } else {
      const q = this.searchQuery.toLowerCase();
      this.filteredSummaries = this.summaries.filter(s =>
        s.captainName.toLowerCase().includes(q) || s.captainCode.toLowerCase().includes(q)
      );
    }
  }

  get selected(): CaptainCustodySummary | null {
    return this.summaries.find(s => s.captainId === this.selectedCaptainId) ?? null;
  }

  selectCaptain(s: CaptainCustodySummary): void {
    this.selectedCaptainId = s.captainId;
    this.recentHistory = [];
    this.custodyService.getHistory(s.captainId, 0, 5).subscribe({
      next: p => this.recentHistory = p.content,
      error: () => this.recentHistory = []
    });
  }

  // KPI strip totals over all captains holding custody
  get totalActive(): number { return this.summaries.reduce((n, s) => n + s.activeCount, 0); }
  get totalVehicles(): number { return this.summaries.reduce((n, s) => n + s.vehicleCount, 0); }
  get totalSimsPhones(): number { return this.summaries.reduce((n, s) => n + s.simCount + s.phoneCount, 0); }
  get totalOther(): number { return this.summaries.reduce((n, s) => n + s.otherCount, 0); }

  openHistory(s: CaptainCustodySummary): void {
    this.historyCaptainId = s.captainId;
    this.historyCaptainName = s.captainName;
    this.historyPage = 0;
    this.loadHistory();
  }

  loadHistory(): void {
    this.custodyService.getHistory(this.historyCaptainId, this.historyPage).subscribe({
      next: p => { this.historyRecords = p.content; this.historyTotalPages = p.totalPages; this.showHistoryDialog = true; },
      error: e => this.error = errorMessage(e, this.i18n.t('CUSTODY.ERR_LOAD_HISTORY'))
    });
  }

  prevHistoryPage(): void { if (this.historyPage > 0) { this.historyPage--; this.loadHistory(); } }
  nextHistoryPage(): void { if (this.historyPage < this.historyTotalPages - 1) { this.historyPage++; this.loadHistory(); } }

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
