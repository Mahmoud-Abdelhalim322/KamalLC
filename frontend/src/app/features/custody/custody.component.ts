import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CustodyService } from '../../core/services/custody.service';
import { CaptainCustodySummary, CustodyRecordResponse } from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api.util';

@Component({
  selector: 'app-custody',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './custody.component.html',
  styleUrls: ['./custody.component.scss']
})
export class CustodyComponent implements OnInit {
  summaries: CaptainCustodySummary[] = [];
  filteredSummaries: CaptainCustodySummary[] = [];
  searchQuery = '';
  isLoading = false;
  expandedCaptainId: string | null = null;

  // History dialog
  showHistoryDialog = false;
  historyCaptainName = '';
  historyRecords: CustodyRecordResponse[] = [];
  historyPage = 0;
  historyTotalPages = 0;
  historyCaptainId = '';

  error = '';

  constructor(private custodyService: CustodyService) {}

  ngOnInit(): void { this.loadSummaries(); }

  loadSummaries(): void {
    this.isLoading = true;
    this.custodyService.getSummaries().subscribe({
      next: data => { this.summaries = data; this.applyFilter(); this.isLoading = false; },
      error: e => { this.error = errorMessage(e, 'فشل تحميل العهد'); this.isLoading = false; }
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

  toggleCaptain(id: string): void {
    this.expandedCaptainId = this.expandedCaptainId === id ? null : id;
  }

  openHistory(s: CaptainCustodySummary): void {
    this.historyCaptainId = s.captainId;
    this.historyCaptainName = s.captainName;
    this.historyPage = 0;
    this.loadHistory();
  }

  loadHistory(): void {
    this.custodyService.getHistory(this.historyCaptainId, this.historyPage).subscribe({
      next: p => { this.historyRecords = p.content; this.historyTotalPages = p.totalPages; this.showHistoryDialog = true; },
      error: e => this.error = errorMessage(e, 'فشل تحميل التاريخ')
    });
  }

  prevHistoryPage(): void { if (this.historyPage > 0) { this.historyPage--; this.loadHistory(); } }
  nextHistoryPage(): void { if (this.historyPage < this.historyTotalPages - 1) { this.historyPage++; this.loadHistory(); } }

  assetTypeIcon(type: string): string {
    return { VEHICLE: 'directions_car', SIM_CARD: 'sim_card', PHONE: 'phone_iphone' }[type] || 'category';
  }

  dismissMsg(): void { this.error = ''; }
}
