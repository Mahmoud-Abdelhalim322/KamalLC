import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { ApiResponse, PageResponse } from '../models/api.models';
import { unwrap, toHttpParams } from './api.util';

export interface ChangeLogEntry {
  id: string;
  entityType: string;
  entityId: string;
  entityLabel: string;
  action: string;
  fieldName: string;
  fieldLabel: string;
  oldValue: string;
  newValue: string;
  changeReason: string;
  changedBy: string;
  changedAt: string;
}

export interface ChangeLogFilter {
  entityType?: string;
  entityId?: string;
  action?: string;
  changedBy?: string;
  from?: string;
  to?: string;
  search?: string;
}

@Injectable({ providedIn: 'root' })
export class ChangeLogService {

  private http = inject(HttpClient);
  private readonly base = '/api/changelog';

  search(filter: ChangeLogFilter, page = 0, size = 50): Observable<PageResponse<ChangeLogEntry>> {
    const params = toHttpParams(filter).set('page', page).set('size', size);
    return this.http
      .get<ApiResponse<PageResponse<ChangeLogEntry>>>(this.base, { params })
      .pipe(map(unwrap));
  }

  getUsers(): Observable<string[]> {
    return this.http
      .get<ApiResponse<string[]>>(`${this.base}/users`)
      .pipe(map(res => res.data ?? []));
  }

  exportExcel(filter: ChangeLogFilter): Observable<Blob> {
    return this.http.get(`${this.base}/export/excel`, {
      params: toHttpParams(filter), responseType: 'blob'
    });
  }

  exportPdf(filter: ChangeLogFilter): Observable<Blob> {
    return this.http.get(`${this.base}/export/pdf`, {
      params: toHttpParams(filter), responseType: 'blob'
    });
  }
}
