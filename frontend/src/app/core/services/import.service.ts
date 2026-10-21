import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  ApiResponse, DeductionImportPreviewResponse, ImportBatchResponse, ImportBatchType, ImportPreviewResponse,
  PageResponse, ShiftImportPreviewResponse
} from '../models/api.models';
import { unwrap, PAGE_SIZE } from './api.util';

@Injectable({ providedIn: 'root' })
export class ImportService {

  private http = inject(HttpClient);
  private readonly base = '/api/imports';

  // -------------------------------------------------------------- captains

  previewCaptains(file: File, supplierId?: string): Observable<ImportPreviewResponse> {
    const url = supplierId ? `${this.base}/preview?supplierId=${encodeURIComponent(supplierId)}` : `${this.base}/preview`;
    return this.http
      .post<ApiResponse<ImportPreviewResponse>>(url, this.body(file))
      .pipe(map(unwrap));
  }

  /**
   * The file is uploaded a second time rather than cached server-side, so the
   * batch id from the preview is what ties the two calls together.
   */
  commitCaptains(batchId: string, file: File, supplierId?: string): Observable<ImportPreviewResponse> {
    const url = supplierId ? `${this.base}/${batchId}/commit?supplierId=${encodeURIComponent(supplierId)}` : `${this.base}/${batchId}/commit`;
    return this.http
      .post<ApiResponse<ImportPreviewResponse>>(url, this.body(file))
      .pipe(map(unwrap));
  }

  downloadTemplate(): Observable<Blob> {
    return this.http.get(`${this.base}/template`, { responseType: 'blob' });
  }

  // ---------------------------------------------------------------- shifts

  previewShifts(file: File, supplierId?: string): Observable<ShiftImportPreviewResponse> {
    const url = supplierId ? `${this.base}/shifts/preview?supplierId=${encodeURIComponent(supplierId)}` : `${this.base}/shifts/preview`;
    return this.http
      .post<ApiResponse<ShiftImportPreviewResponse>>(url, this.body(file))
      .pipe(map(unwrap));
  }

  commitShifts(batchId: string, file: File, supplierId?: string): Observable<ShiftImportPreviewResponse> {
    const url = supplierId ? `${this.base}/shifts/${batchId}/commit?supplierId=${encodeURIComponent(supplierId)}` : `${this.base}/shifts/${batchId}/commit`;
    return this.http
      .post<ApiResponse<ShiftImportPreviewResponse>>(url, this.body(file))
      .pipe(map(unwrap));
  }

  downloadShiftTemplate(): Observable<Blob> {
    return this.http.get(`${this.base}/shifts/template`, { responseType: 'blob' });
  }

  // ---------------------------------------------------- supplier deductions

  previewDeductions(file: File, supplierId?: string): Observable<DeductionImportPreviewResponse> {
    const url = supplierId ? `${this.base}/deductions/preview?supplierId=${encodeURIComponent(supplierId)}` : `${this.base}/deductions/preview`;
    return this.http
      .post<ApiResponse<DeductionImportPreviewResponse>>(url, this.body(file))
      .pipe(map(unwrap));
  }

  commitDeductions(batchId: string, file: File, supplierId?: string): Observable<DeductionImportPreviewResponse> {
    const url = supplierId ? `${this.base}/deductions/${batchId}/commit?supplierId=${encodeURIComponent(supplierId)}` : `${this.base}/deductions/${batchId}/commit`;
    return this.http
      .post<ApiResponse<DeductionImportPreviewResponse>>(url, this.body(file))
      .pipe(map(unwrap));
  }

  downloadDeductionTemplate(): Observable<Blob> {
    return this.http.get(`${this.base}/deductions/template`, { responseType: 'blob' });
  }

  // ---------------------------------------------------- vacation commission

  previewVacationCommission(file: File, supplierId?: string): Observable<import('../models/api.models').VacationCommissionImportPreviewResponse> {
    const url = supplierId ? `${this.base}/vacation-commission/preview?supplierId=${encodeURIComponent(supplierId)}` : `${this.base}/vacation-commission/preview`;
    return this.http
      .post<ApiResponse<import('../models/api.models').VacationCommissionImportPreviewResponse>>(url, this.body(file))
      .pipe(map(unwrap));
  }

  commitVacationCommission(batchId: string, file: File, supplierId?: string): Observable<import('../models/api.models').VacationCommissionImportPreviewResponse> {
    const url = supplierId ? `${this.base}/vacation-commission/${batchId}/commit?supplierId=${encodeURIComponent(supplierId)}` : `${this.base}/vacation-commission/${batchId}/commit`;
    return this.http
      .post<ApiResponse<import('../models/api.models').VacationCommissionImportPreviewResponse>>(url, this.body(file))
      .pipe(map(unwrap));
  }

  downloadVacationCommissionTemplate(): Observable<Blob> {
    return this.http.get(`${this.base}/vacation-commission/template`, { responseType: 'blob' });
  }

  // --------------------------------------------------------------- history

  /** Each import screen shows only its own batches — omit `type` for the combined list. */
  history(type?: ImportBatchType, page = 0, size = PAGE_SIZE): Observable<PageResponse<ImportBatchResponse>> {
    const params: Record<string, string | number> = { page, size };
    if (type) { params['type'] = type; }
    return this.http
      .get<ApiResponse<PageResponse<ImportBatchResponse>>>(this.base, { params })
      .pipe(map(unwrap));
  }

  /** Multipart body — the browser sets the boundary, so no Content-Type header here. */
  private body(file: File): FormData {
    const form = new FormData();
    form.append('file', file);
    return form;
  }
}
