import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ApiResponse,
  SupplierAdjustmentRequest,
  SupplierAdjustmentResponse,
  SupplierRevenueSummaryResponse,
  SupplierStatementResponse
} from '../models/api.models';

@Injectable({
  providedIn: 'root'
})
export class SupplierRevenueService {
  private http = inject(HttpClient);
  private readonly baseUrl = '/api/supplier-revenue';

  getSummary(from: string, to: string): Observable<ApiResponse<SupplierRevenueSummaryResponse>> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<ApiResponse<SupplierRevenueSummaryResponse>>(`${this.baseUrl}/summary`, { params });
  }

  getStatement(supplierId: string, from: string, to: string): Observable<ApiResponse<SupplierStatementResponse>> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<ApiResponse<SupplierStatementResponse>>(`${this.baseUrl}/statement/${supplierId}`, { params });
  }

  addAdjustment(request: SupplierAdjustmentRequest): Observable<ApiResponse<SupplierAdjustmentResponse>> {
    return this.http.post<ApiResponse<SupplierAdjustmentResponse>>(`${this.baseUrl}/adjustments`, request);
  }

  deleteAdjustment(id: string): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/adjustments/${id}`);
  }

  exportExcel(from: string, to: string): Observable<Blob> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get(`${this.baseUrl}/export`, {
      params,
      responseType: 'blob'
    });
  }
}
