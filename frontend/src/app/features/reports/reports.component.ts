import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  CaptainReportResponse, TeamReportResponse, TeamReportRow
} from '../../core/models/api.models';
import { ReportService } from '../../core/services/report.service';
import { downloadBlob, errorMessage, isoDate } from '../../core/services/api.util';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationService } from '../../core/i18n/translation.service';

type ReportTab = 'team' | 'individual' | 'profitability';
type SortKey = keyof Pick<TeamReportRow,
  'totalOrders' | 'attendedShifts' | 'attendanceRate' | 'totalHours' |
  'avgOrdersPerShift' | 'avgBusyUtilization' | 'avgHourlyUtr' | 'lateDeductions' | 'net' | 'margin'>;

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './reports.component.html',
  styleUrls: ['./reports.component.scss']
})
export class ReportsComponent implements OnInit {

  private reportService = inject(ReportService);
  public i18n = inject(TranslationService);

  /** Captains this close to the monthly target are still worth a push before month end. */
  readonly nearTargetWindow = 60;

  activeTab: ReportTab = 'team';

  from = '';
  to = '';

  teamReport: TeamReportResponse | null = null;
  isLoadingTeam = false;
  sortKey: SortKey = 'totalOrders';
  sortAsc = false;

  selectedCaptainId = '';
  captainReport: CaptainReportResponse | null = null;
  isLoadingCaptain = false;
  isExporting = false;

  errorMessage = '';

  ngOnInit(): void {
    const today = new Date();
    this.from = isoDate(new Date(today.getFullYear(), today.getMonth(), 1));
    this.to = isoDate(today);
    this.loadTeam();
  }

  // ---------------------------------------------------------------- data

  setTab(tab: ReportTab): void {
    this.activeTab = tab;
    if (tab === 'individual' && this.selectedCaptainId && !this.captainReport) {
      this.loadCaptainReport();
    }
  }

  onPeriodChange(): void {
    this.captainReport = null;
    this.loadTeam();
    if (this.activeTab === 'individual' && this.selectedCaptainId) {
      this.loadCaptainReport();
    }
  }

  loadTeam(): void {
    this.isLoadingTeam = true;
    this.errorMessage = '';

    this.reportService.team(this.from, this.to).subscribe({
      next: (report) => {
        this.isLoadingTeam = false;
        this.teamReport = report;
        // A captain picked in a previous period may have no data in this one.
        if (this.selectedCaptainId && !report.rows.some(r => r.captainId === this.selectedCaptainId)) {
          this.selectedCaptainId = '';
          this.captainReport = null;
        }
      },
      error: (err) => {
        this.isLoadingTeam = false;
        this.teamReport = null;
        this.errorMessage = errorMessage(err, this.i18n.get('REPORTS.TEAM_LOAD_FAILED'));
      }
    });
  }

  loadCaptainReport(): void {
    if (!this.selectedCaptainId) { this.captainReport = null; return; }
    this.isLoadingCaptain = true;
    this.errorMessage = '';

    this.reportService.captain(this.selectedCaptainId, this.from, this.to).subscribe({
      next: (report) => {
        this.isLoadingCaptain = false;
        this.captainReport = report;
      },
      error: (err) => {
        this.isLoadingCaptain = false;
        this.captainReport = null;
        this.errorMessage = errorMessage(err, this.i18n.get('REPORTS.CAPTAIN_LOAD_FAILED'));
      }
    });
  }

  /** The individual report as an Excel file — without the shift list or the profitability. */
  exportCaptainReport(): void {
    const report = this.captainReport;
    if (!report) return;

    this.isExporting = true;
    this.errorMessage = '';

    this.reportService.exportCaptain(report.captainId, this.from, this.to).subscribe({
      next: (blob) => {
        this.isExporting = false;
        const filename = this.i18n.get('REPORTS.EXPORT_FILENAME', {
          code: report.captainCode,
          from: this.from
        });
        downloadBlob(blob, filename);
      },
      error: () => {
        this.isExporting = false;
        this.errorMessage = this.i18n.get('REPORTS.EXPORT_FAILED');
      }
    });
  }

  /** Jumps from any team row straight to that captain's individual report. */
  openCaptain(row: TeamReportRow): void {
    this.selectedCaptainId = row.captainId;
    this.activeTab = 'individual';
    this.loadCaptainReport();
  }

  // ---------------------------------------------------------------- team table

  sortBy(key: SortKey): void {
    if (this.sortKey === key) this.sortAsc = !this.sortAsc;
    else { this.sortKey = key; this.sortAsc = false; }
  }

  get sortedRows(): TeamReportRow[] {
    const key = this.sortKey;
    const direction = this.sortAsc ? 1 : -1;
    return [...(this.teamReport?.rows ?? [])]
      .sort((a, b) => ((a[key] ?? 0) - (b[key] ?? 0)) * direction);
  }

  /** Short of the target but within reach — where a nudge is worth 1000 SAR to the captain. */
  get nearTargetRows(): TeamReportRow[] {
    return (this.teamReport?.rows ?? [])
      .filter(r => !r.targetAchieved && r.ordersToTarget > 0 && r.ordersToTarget <= this.nearTargetWindow)
      .sort((a, b) => a.ordersToTarget - b.ordersToTarget);
  }

  get profitabilityRows(): TeamReportRow[] {
    return [...(this.teamReport?.rows ?? [])].sort((a, b) => b.margin - a.margin);
  }

  // ---------------------------------------------------------------- chart

  /** Bar height as a share of the period's own peak, so the shape is readable at any scale. */
  barHeight(value: number | null, series: 'orders' | 'hours'): string {
    const points = this.captainReport?.daily ?? [];
    const max = Math.max(...points.map(p => (series === 'orders' ? p.orders : p.hours) ?? 0), 1);
    return `${Math.round(((value ?? 0) / max) * 100)}%`;
  }

  // ---------------------------------------------------------------- display

  /** Whether a captain's figure sits above or below the team average for the same period. */
  compare(value: number, teamValue: number): string {
    if (Math.abs(value - teamValue) < 0.005) return 'same';
    return value > teamValue ? 'above' : 'below';
  }

  typeLabel(captainType: string): string {
    return captainType === 'FREELANCE' ? 'KSCFL' : 'KSC';
  }

  money(value: number | null | undefined): string {
    if (value == null) return '—';
    return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  percent(value: number | null | undefined): string {
    return value == null ? '—' : `${(value * 100).toFixed(1)}%`;
  }

  num(value: number | null | undefined, digits = 2): string {
    return value == null ? '—' : value.toFixed(digits);
  }

}
