import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { ChangeLogService, ChangeLogEntry, ChangeLogFilter } from '../../core/services/changelog.service';
import { downloadBlob, errorMessage, isoDate } from '../../core/services/api.util';
import { AuthService } from '../../core/auth/auth.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { TranslationService } from '../../core/i18n/translation.service';

type EntityTab = 'ALL' | 'SHIFT' | 'SUPPLIER_ID' | 'VEHICLE' | 'ASSET_ITEM' | 'CAPTAIN';

@Component({
  selector: 'app-changelog',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './changelog.component.html',
  styleUrls: ['./changelog.component.scss']
})
export class ChangeLogComponent implements OnInit, OnDestroy {

  private service = inject(ChangeLogService);
  public authService = inject(AuthService);
  public i18n = inject(TranslationService);

  tabs: { key: EntityTab; icon: string }[] = [
    { key: 'ALL',         icon: 'history' },
    { key: 'SHIFT',       icon: 'event_note' },
    { key: 'SUPPLIER_ID', icon: 'badge' },
    { key: 'VEHICLE',     icon: 'directions_car' },
    { key: 'ASSET_ITEM',  icon: 'inventory_2' },
    { key: 'CAPTAIN',     icon: 'local_shipping' },
  ];
  activeTab: EntityTab = 'ALL';

  from = '';
  to = '';
  search = '';
  changedBy = '';
  action = '';

  users: string[] = [];

  entries: ChangeLogEntry[] = [];
  page = 0;
  size = 50;
  totalElements = 0;
  totalPages = 0;
  isLoading = true;

  isExporting = false;
  errorMsg = '';
  successMsg = '';

  private searchInput$ = new Subject<string>();
  private destroy$ = new Subject<void>();

  ngOnInit(): void {
    const today = new Date();
    this.from = isoDate(new Date(today.getFullYear(), today.getMonth(), 1));
    this.to = isoDate(today);

    this.searchInput$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe(() => this.load());

    this.service.getUsers().subscribe(u => this.users = u);
    this.load();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ────────────────────── Data Loading ──────────────────────

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.isLoading = true;
    this.errorMsg = '';

    this.service.search(this.buildFilter(), this.page, this.size).subscribe({
      next: res => {
        this.entries = res.content;
        this.totalElements = res.totalElements;
        this.totalPages = res.totalPages;
        this.isLoading = false;
      },
      error: err => {
        this.errorMsg = errorMessage(err, this.i18n.t('CHANGELOG.ERR_LOAD'));
        this.isLoading = false;
      }
    });
  }

  private buildFilter(): ChangeLogFilter {
    return {
      entityType: this.activeTab === 'ALL' ? undefined : this.activeTab,
      changedBy: this.changedBy || undefined,
      action: this.action || undefined,
      from: this.from || undefined,
      to: this.to || undefined,
      search: this.search || undefined,
    };
  }

  // ────────────────────── Tabs ──────────────────────

  switchTab(tab: EntityTab): void {
    this.activeTab = tab;
    this.load();
  }

  // ────────────────────── Pagination ──────────────────────

  goToPage(p: number): void {
    if (p < 0 || p >= this.totalPages) return;
    this.page = p;
    this.load(false);
  }

  get paginationRange(): number[] {
    const pages: number[] = [];
    const start = Math.max(0, this.page - 2);
    const end = Math.min(this.totalPages - 1, this.page + 2);
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  }

  // ────────────────────── Search ──────────────────────

  onSearch(): void {
    this.searchInput$.next(this.search);
  }

  clearFilters(): void {
    const today = new Date();
    this.from = isoDate(new Date(today.getFullYear(), today.getMonth(), 1));
    this.to = isoDate(today);
    this.search = '';
    this.changedBy = '';
    this.action = '';
    this.activeTab = 'ALL';
    this.load();
  }

  // ────────────────────── Export ──────────────────────

  exportExcel(): void {
    this.isExporting = true;
    this.service.exportExcel(this.buildFilter()).subscribe({
      next: blob => {
        downloadBlob(blob, this.i18n.t('CHANGELOG.EXPORT_FILENAME_EXCEL'));
        this.isExporting = false;
      },
      error: err => {
        this.errorMsg = errorMessage(err, this.i18n.t('CHANGELOG.ERR_EXPORT_EXCEL'));
        this.isExporting = false;
      }
    });
  }

  exportPdf(): void {
    this.isExporting = true;
    this.service.exportPdf(this.buildFilter()).subscribe({
      next: blob => {
        downloadBlob(blob, this.i18n.t('CHANGELOG.EXPORT_FILENAME_PDF'));
        this.isExporting = false;
      },
      error: err => {
        this.errorMsg = errorMessage(err, this.i18n.t('CHANGELOG.ERR_EXPORT_PDF'));
        this.isExporting = false;
      }
    });
  }

  // ────────────────────── Helpers ──────────────────────

  getActionLabel(action: string): string {
    return this.i18n.t('CHANGELOG.ACTION_' + action) || action;
  }

  getEntityTypeLabel(type: string): string {
    return this.i18n.t('CHANGELOG.TAB_' + type) || type;
  }

  getActionClass(action: string): string {
    switch (action) {
      case 'UPDATE': return 'badge-update';
      case 'EXCLUDE': case 'EXCLUDE_DEDUCTION': return 'badge-exclude';
      case 'RESTORE': case 'RESTORE_DEDUCTION': return 'badge-restore';
      case 'LINK': return 'badge-link';
      case 'UNLINK': return 'badge-unlink';
      case 'CREATE': return 'badge-create';
      case 'DELETE': return 'badge-delete';
      default: return '';
    }
  }

  getEntityTypeIcon(type: string): string {
    const tab = this.tabs.find(t => t.key === type);
    return tab?.icon || 'history';
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return '';
    try {
      const d = new Date(dateStr);
      if (isNaN(d.getTime())) return dateStr;
      const isAr = this.i18n.currentLang() === 'ar';
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      let hours = d.getHours();
      const minutes = String(d.getMinutes()).padStart(2, '0');
      const period = isAr ? (hours >= 12 ? 'م' : 'ص') : (hours >= 12 ? 'PM' : 'AM');
      hours = hours % 12 || 12;
      const h = String(hours).padStart(2, '0');
      return `${y}/${m}/${day} ${h}:${minutes} ${period}`;
    } catch {
      return dateStr;
    }
  }
}
