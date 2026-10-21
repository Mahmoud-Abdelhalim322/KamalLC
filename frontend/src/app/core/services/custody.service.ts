import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  ApiResponse, PageResponse, CustodyRecordResponse, CaptainCustodySummary, CustodySummaryTotals, SummaryPage
} from '../models/api.models';
import { unwrap, toHttpParams, PAGE_SIZE } from './api.util';

@Injectable({ providedIn: 'root' })
export class CustodyService {

  private http = inject(HttpClient);
  private readonly base = '/api/custody';

  /** One server page of captains holding custody, plus totals over every match. */
  getSummaries(search?: string, page = 0, size = PAGE_SIZE): Observable<SummaryPage<CaptainCustodySummary, CustodySummaryTotals>> {
    return this.http.get<ApiResponse<SummaryPage<CaptainCustodySummary, CustodySummaryTotals>>>(`${this.base}/summaries`,
      { params: toHttpParams({ search, page, size }) }).pipe(map(unwrap));
  }

  getActiveByCaptain(captainId: string): Observable<CustodyRecordResponse[]> {
    return this.http.get<ApiResponse<CustodyRecordResponse[]>>(`${this.base}/active`, { params: { captainId } })
      .pipe(map(unwrap));
  }

  getHistory(captainId: string, page = 0, size = PAGE_SIZE): Observable<PageResponse<CustodyRecordResponse>> {
    return this.http.get<ApiResponse<PageResponse<CustodyRecordResponse>>>(`${this.base}/history`,
      { params: toHttpParams({ captainId, page, size }) }).pipe(map(unwrap));
  }

  getAll(params: { assetType?: string; page?: number; size?: number }): Observable<PageResponse<CustodyRecordResponse>> {
    return this.http.get<ApiResponse<PageResponse<CustodyRecordResponse>>>(this.base,
      { params: toHttpParams(params) }).pipe(map(unwrap));
  }

  getItemHistory(referenceId: string, page = 0, size = PAGE_SIZE): Observable<PageResponse<CustodyRecordResponse>> {
    return this.http.get<ApiResponse<PageResponse<CustodyRecordResponse>>>(`${this.base}/item-history`,
      { params: toHttpParams({ referenceId, page, size }) }).pipe(map(unwrap));
  }
}
