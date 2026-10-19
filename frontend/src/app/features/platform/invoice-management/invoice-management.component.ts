import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ApiResponse, InvoiceResponse } from '../../../core/models/api.models';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

@Component({
  selector: 'app-invoice-management',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './invoice-management.component.html',
  styleUrl: './invoice-management.component.scss'
})
export class InvoiceManagementComponent implements OnInit {
  invoices: InvoiceResponse[] = [];
  loading = true;

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
}
