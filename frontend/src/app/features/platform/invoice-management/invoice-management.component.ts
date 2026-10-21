import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, InvoiceResponse } from '../../../core/models/api.models';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { SarPipe } from '../../../shared/pipes/sar.pipe';
import { InitialsPipe } from '../../../shared/pipes/initials.pipe';

type InvoiceFilter = 'ALL' | 'PAID' | 'PENDING' | 'OVERDUE';

@Component({
  selector: 'app-invoice-management',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, SarPipe, InitialsPipe],
  templateUrl: './invoice-management.component.html',
  styleUrl: './invoice-management.component.scss'
})
export class InvoiceManagementComponent implements OnInit {
  invoices: InvoiceResponse[] = [];
  loading = true;
  filter: InvoiceFilter = 'ALL';
  search = '';

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.fetchInvoices();
  }

  fetchInvoices(): void {
    this.loading = true;
    this.http.get<ApiResponse<InvoiceResponse[]>>('/api/platform/invoices')
      .subscribe({
        next: (res) => {
          if (res.success && res.data) this.invoices = res.data;
          this.loading = false;
        },
        error: () => this.loading = false
      });
  }

  /** Unpaid and past its due date. */
  isOverdue(inv: InvoiceResponse): boolean {
    return inv.status !== 'PAID' && !!inv.dueDate && new Date(inv.dueDate).getTime() < Date.now();
  }

  private sum(list: InvoiceResponse[]): number { return list.reduce((n, i) => n + (i.totalAmount || 0), 0); }
  get paid(): InvoiceResponse[] { return this.invoices.filter(i => i.status === 'PAID'); }
  get pending(): InvoiceResponse[] { return this.invoices.filter(i => i.status !== 'PAID' && !this.isOverdue(i)); }
  get overdue(): InvoiceResponse[] { return this.invoices.filter(i => this.isOverdue(i)); }
  get paidTotal(): number { return this.sum(this.paid); }
  get pendingTotal(): number { return this.sum(this.pending); }
  get overdueTotal(): number { return this.sum(this.overdue); }

  get visible(): InvoiceResponse[] {
    const q = this.search.trim().toLowerCase();
    return this.invoices.filter(i =>
      (this.filter === 'ALL'
        || (this.filter === 'PAID' && i.status === 'PAID')
        || (this.filter === 'PENDING' && i.status !== 'PAID' && !this.isOverdue(i))
        || (this.filter === 'OVERDUE' && this.isOverdue(i)))
      && (!q || i.invoiceNumber.toLowerCase().includes(q) || i.tenantName.toLowerCase().includes(q)));
  }
}
