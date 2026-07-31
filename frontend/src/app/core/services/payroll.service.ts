import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  AdjustmentRequest, ApiResponse, PageResponse,
  PayrollLineResponse, PayrollRunResponse
} from '../models/api.models';
import { unwrap } from './api.util';

export interface GeneratePayrollRequest {
  periodFrom: string;
  periodTo: string;
  title: string | null;
}

@Injectable({ providedIn: 'root' })
export class PayrollService {

  private http = inject(HttpClient);
  private readonly base = '/api/payroll/runs';

  list(page = 0, size = 24): Observable<PageResponse<PayrollRunResponse>> {
    return this.http
      .get<ApiResponse<PageResponse<PayrollRunResponse>>>(
        this.base, { params: { page, size } })
      .pipe(map(unwrap));
  }

  get(runId: string): Observable<PayrollRunResponse> {
    return this.http
      .get<ApiResponse<PayrollRunResponse>>(`${this.base}/${runId}`)
      .pipe(map(unwrap));
  }

  getLines(runId: string): Observable<PayrollLineResponse[]> {
    return this.http
      .get<ApiResponse<PayrollLineResponse[]>>(`${this.base}/${runId}/lines`)
      .pipe(map(res => res.data ?? []));
  }

  /** Builds the run for a period, or rebuilds it if it is still a draft. */
  generate(request: GeneratePayrollRequest): Observable<PayrollRunResponse> {
    return this.http
      .post<ApiResponse<PayrollRunResponse>>(`${this.base}/generate`, request)
      .pipe(map(unwrap));
  }

  /** Approving freezes the numbers — later shift edits no longer move this run. */
  approve(runId: string): Observable<PayrollRunResponse> {
    return this.http
      .post<ApiResponse<PayrollRunResponse>>(`${this.base}/${runId}/approve`, null)
      .pipe(map(unwrap));
  }

  markPaid(runId: string): Observable<PayrollRunResponse> {
    return this.http
      .post<ApiResponse<PayrollRunResponse>>(`${this.base}/${runId}/pay`, null)
      .pipe(map(unwrap));
  }

  reopen(runId: string, reason: string): Observable<PayrollRunResponse> {
    return this.http
      .post<ApiResponse<PayrollRunResponse>>(`${this.base}/${runId}/reopen`, { reason })
      .pipe(map(unwrap));
  }

  delete(runId: string): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.base}/${runId}`);
  }

  /** Both adjustment calls return the captain's re-totalled line. */
  addAdjustment(runId: string, request: AdjustmentRequest): Observable<PayrollLineResponse> {
    return this.http
      .post<ApiResponse<PayrollLineResponse>>(`${this.base}/${runId}/adjustments`, request)
      .pipe(map(unwrap));
  }

  deleteAdjustment(runId: string, adjustmentId: string): Observable<PayrollLineResponse> {
    return this.http
      .delete<ApiResponse<PayrollLineResponse>>(`${this.base}/${runId}/adjustments/${adjustmentId}`)
      .pipe(map(unwrap));
  }

  getStaffLines(runId: string, staffType?: string): Observable<import('../models/api.models').StaffPayrollLineResponse[]> {
    let params = new HttpParams();
    if (staffType) {
      params = params.set('staffType', staffType);
    }
    return this.http
      .get<ApiResponse<import('../models/api.models').StaffPayrollLineResponse[]>>(`${this.base}/${runId}/staff-lines`, { params })
      .pipe(map(res => res.data ?? []));
  }

  addStaffAdjustment(runId: string, request: import('../models/api.models').StaffAdjustmentRequest): Observable<import('../models/api.models').StaffPayrollLineResponse> {
    return this.http
      .post<ApiResponse<import('../models/api.models').StaffPayrollLineResponse>>(`${this.base}/${runId}/staff-adjustments`, request)
      .pipe(map(unwrap));
  }

  deleteStaffAdjustment(runId: string, adjustmentId: string): Observable<import('../models/api.models').StaffPayrollLineResponse> {
    return this.http
      .delete<ApiResponse<import('../models/api.models').StaffPayrollLineResponse>>(`${this.base}/${runId}/staff-adjustments/${adjustmentId}`)
      .pipe(map(unwrap));
  }

  exportWorkbook(runId: string, staffType?: string): Observable<Blob> {
    let params = new HttpParams();
    if (staffType) {
      params = params.set('staffType', staffType);
    }
    return this.http.get(`${this.base}/${runId}/export`, { params, responseType: 'blob' });
  }
}
