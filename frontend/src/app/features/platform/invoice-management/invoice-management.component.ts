import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { Subject, debounceTime } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, InvoiceResponse, SummaryPage, InvoiceListSummary } from '../../../core/models/api.models';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { SarPipe } from '../../../shared/pipes/sar.pipe';
import { InitialsPipe } from '../../../shared/pipes/initials.pipe';
import { PagerComponent } from '../../../shared/components/pager/pager.component';
import { PAGE_SIZE } from '../../../core/services/api.util';


type InvoiceFilter = 'ALL' | 'PAID' | 'PENDING' | 'OVERDUE';

@Component({
  selector: 'app-invoice-management',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, SarPipe, InitialsPipe, PagerComponent],
  templateUrl: './invoice-management.component.html',
  styleUrl: './invoice-management.component.scss'
})
export class InvoiceManagementComponent implements OnInit {
  invoices: InvoiceResponse[] = [];
  summary: InvoiceListSummary | null = null;
  loading = true;

  readonly pageSize = PAGE_SIZE;
  page = 0;
  totalElements = 0;
  totalPages = 0;
  private readonly search$ = new Subject<void>();
  filter: InvoiceFilter = 'ALL';
  search = '';

  constructor(private http: HttpClient) {
    this.search$.pipe(debounceTime(300), takeUntilDestroyed(inject(DestroyRef))).subscribe(() => {
      this.page = 0;
      this.fetchInvoices();
    });
  }

  ngOnInit(): void {
    this.fetchInvoices();
  }
  setFilter(filter: typeof this.filter): void {
    this.filter = filter;
    this.page = 0;
    this.fetchInvoices();
  }

  onSearchInput(): void {
    this.search$.next();
  }

  goToPage(page: number): void {
    this.page = page;
    this.fetchInvoices();
  }


  fetchInvoices(): void {
    this.loading = true;
    this.http.get<ApiResponse<SummaryPage<InvoiceResponse, InvoiceListSummary>>>('/api/platform/invoices', {
      params: { page: this.page, size: this.pageSize, filter: this.filter, search: this.search.trim() }
    })
      .subscribe({
        next: (res) => {
          if (res.success && res.data) {
            this.invoices = res.data.content;
            this.summary = res.data.summary;
            this.totalElements = res.data.totalElements;
            this.totalPages = res.data.totalPages;
          }
          this.loading = false;
        },
        error: () => this.loading = false
      });
  }

  /** Unpaid and past its due date. */
  isOverdue(inv: InvoiceResponse): boolean {
    return inv.status !== 'PAID' && !!inv.dueDate && new Date(inv.dueDate).getTime() < Date.now();
  }

}
