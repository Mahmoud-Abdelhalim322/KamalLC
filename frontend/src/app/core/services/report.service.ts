import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { ApiResponse, CaptainReportResponse, TeamReportResponse } from '../models/api.models';
import { unwrap } from './api.util';

@Injectable({ providedIn: 'root' })
export class ReportService {

  private http = inject(HttpClient);
  private readonly base = '/api/reports';

  /** One row per captain for the period, plus the totals strip above the table. */
  team(from: string, to: string): Observable<TeamReportResponse> {
    return this.http
      .get<ApiResponse<TeamReportResponse>>(`${this.base}/team`, { params: this.period(from, to) })
      .pipe(map(unwrap));
  }

  /** Full breakdown for one captain: daily series, deductions, and team comparison. */
  captain(captainId: string, from: string, to: string): Observable<CaptainReportResponse> {
    return this.http
      .get<ApiResponse<CaptainReportResponse>>(
        `${this.base}/captain/${captainId}`, { params: this.period(from, to) })
      .pipe(map(unwrap));
  }

  /** The individual report as an Excel file — without the shift list or the profitability. */
  exportCaptain(captainId: string, from: string, to: string): Observable<Blob> {
    return this.http.get(`${this.base}/captain/${captainId}/export`, {
      params: this.period(from, to), responseType: 'blob'
    });
  }

  private period(from: string, to: string): HttpParams {
    return new HttpParams().set('from', from).set('to', to);
  }
}
