import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  ApiResponse, PageResponse, RecalculationResult, ShiftFilter, ShiftResponse
} from '../models/api.models';
import { unwrap, toHttpParams } from './api.util';

@Injectable({ providedIn: 'root' })
export class ShiftService {

  private http = inject(HttpClient);
  private readonly base = '/api/shifts';

  search(filter: ShiftFilter, page = 0, size = 50): Observable<PageResponse<ShiftResponse>> {
    const params = toHttpParams(filter).set('page', page).set('size', size);
    return this.http
      .get<ApiResponse<PageResponse<ShiftResponse>>>(this.base, { params })
      .pipe(map(unwrap));
  }

  getById(id: string): Observable<ShiftResponse> {
    return this.http
      .get<ApiResponse<ShiftResponse>>(`${this.base}/${id}`)
      .pipe(map(unwrap));
  }

  /** The whole filtered list as an Excel file — not just the page on screen. */
  exportWorkbook(filter: ShiftFilter): Observable<Blob> {
    return this.http.get(`${this.base}/export`, {
      params: toHttpParams(filter), responseType: 'blob'
    });
  }

  /** The whole filtered list as a PDF document. */
  exportPdf(filter: ShiftFilter): Observable<Blob> {
    return this.http.get(`${this.base}/export-pdf`, {
      params: toHttpParams(filter), responseType: 'blob'
    });
  }

  /** Distinct zones across imported shifts — populates the zone filter. */
  getZones(): Observable<string[]> {
    return this.http
      .get<ApiResponse<string[]>>(`${this.base}/zones`)
      .pipe(map(res => res.data ?? []));
  }

  /**
   * Excluding a shift keeps it visible but drops it from pay and revenue.
   * The server returns the re-priced row, so callers can swap it in directly.
   */
  setExcluded(id: string, excluded: boolean, reason: string | null): Observable<ShiftResponse> {
    return this.http
      .patch<ApiResponse<ShiftResponse>>(`${this.base}/${id}/exclude`, { excluded, reason })
      .pipe(map(unwrap));
  }

  /** Re-matches and re-prices a period — run after linking a supplier ID or changing a rate. */
  recalculate(from: string, to: string): Observable<RecalculationResult> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http
      .post<ApiResponse<RecalculationResult>>(`${this.base}/recalculate`, null, { params })
      .pipe(map(unwrap));
  }

  /** Import supplier (Ninja) deductions from an Excel file. */
  importSupplierDeductions(file: File): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http
      .post<ApiResponse<any>>(`${this.base}/import-supplier-deductions`, formData)
      .pipe(map(unwrap));
  }

  /** Exclude a supplier deduction — zeroes the value and requires a reason. */
  excludeDeduction(id: string, reason: string): Observable<ShiftResponse> {
    return this.http
      .patch<ApiResponse<ShiftResponse>>(`${this.base}/${id}/exclude-deduction`, { reason })
      .pipe(map(unwrap));
  }

  /** Restore a previously excluded supplier deduction to its original value. */
  restoreDeduction(id: string): Observable<ShiftResponse> {
    return this.http
      .patch<ApiResponse<ShiftResponse>>(`${this.base}/${id}/restore-deduction`, {})
      .pipe(map(unwrap));
  }

  getVacationCommissions(params: { from?: string; to?: string; supplierId?: string; search?: string; page?: number; size?: number }): Observable<PageResponse<import('../models/api.models').VacationCommissionResponse>> {
    let httpParams = new HttpParams();
    if (params.from) httpParams = httpParams.set('from', params.from);
    if (params.to) httpParams = httpParams.set('to', params.to);
    if (params.supplierId) httpParams = httpParams.set('supplierId', params.supplierId);
    if (params.search) httpParams = httpParams.set('search', params.search);
    httpParams = httpParams.set('page', params.page ?? 0).set('size', params.size ?? 50);

    return this.http
      .get<ApiResponse<PageResponse<import('../models/api.models').VacationCommissionResponse>>>('/api/vacation-commissions', { params: httpParams })
      .pipe(map(unwrap));
  }

  deleteVacationCommission(id: string): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`/api/vacation-commissions/${id}`)
      .pipe(map(unwrap));
  }
}
