import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { PAGE_SIZE } from '../../../core/services/api.util';

/**
 * Footer pager for server-paged lists (Figma kit "Pagination"): "Showing a–b of n", previous / next,
 * and the page numbers around the current page. Pages are 0-based, like the API.
 */
@Component({
  selector: 'app-pager',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  template: `
    <div class="k-pager" [class.compact]="compact" *ngIf="totalElements > 0">
      <span class="k-pager-info">{{ 'COMMON.SHOWING_RANGE' | translate:{ from: from, to: to, total: totalElements } }}</span>
      <ng-container *ngIf="totalPages > 1">
        <button class="btn btn-outline btn-sm" type="button" [disabled]="page === 0" (click)="go(page - 1)">
          <span class="material-icons-round k-flip">chevron_left</span>{{ 'COMMON.PREVIOUS' | translate }}
        </button>
        <button *ngFor="let p of pages" type="button" class="k-page-btn" [class.active]="p === page" (click)="go(p)">{{ p + 1 }}</button>
        <button class="btn btn-outline btn-sm" type="button" [disabled]="page >= totalPages - 1" (click)="go(page + 1)">
          {{ 'COMMON.NEXT' | translate }}<span class="material-icons-round k-flip">chevron_right</span>
        </button>
      </ng-container>
    </div>
  `
})
export class PagerComponent {
  @Input() page = 0;
  @Input() size = PAGE_SIZE;
  @Input() totalElements = 0;
  @Input() totalPages = 0;
  /** Stacked and centred, for narrow side cards. */
  @Input() compact = false;
  @Output() pageChange = new EventEmitter<number>();

  get from(): number { return this.totalElements === 0 ? 0 : this.page * this.size + 1; }
  get to(): number { return Math.min((this.page + 1) * this.size, this.totalElements); }

  /** Up to five page numbers centred on the current page. */
  get pages(): number[] {
    const start = Math.max(0, Math.min(this.page - 2, this.totalPages - 5));
    return Array.from({ length: Math.min(5, this.totalPages) }, (_, i) => start + i);
  }

  go(p: number): void {
    if (p >= 0 && p < this.totalPages && p !== this.page) this.pageChange.emit(p);
  }
}
