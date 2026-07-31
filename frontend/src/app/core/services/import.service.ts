import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  ApiResponse, ImportBatchResponse, ImportBatchType, ImportPreviewResponse,
  PageResponse, ShiftImportPreviewResponse
} from '../models/api.models';
import { unwrap } from './api.util';

@Injectable({ providedIn: 'root' })
export class ImportService {

  private http = inject(HttpClient);
  private readonly base = '/api/imports';

  // -------------------------------------------------------------- captains

  previewCaptains(file: File): Observable<ImportPreviewResponse> {
    return this.http
      .post<ApiResponse<ImportPreviewResponse>>(`${this.base}/preview`, this.body(file))
      .pipe(map(unwrap));
  }

  /**
   * The file is uploaded a second time rather than cached server-side, so the
   * batch id from the preview is what ties the two calls together.
   */
  commitCaptains(batchId: string, file: File): Observable<ImportPreviewResponse> {
    return this.http
      .post<ApiResponse<ImportPreviewResponse>>(`${this.base}/${batchId}/commit`, this.body(file))
      .pipe(map(unwrap));
  }

  downloadTemplate(): Observable<Blob> {
    return this.http.get(`${this.base}/template`, { responseType: 'blob' });
  }

  // ---------------------------------------------------------------- shifts

  previewShifts(file: File): Observable<ShiftImportPreviewResponse> {
    return this.http
      .post<ApiResponse<ShiftImportPreviewResponse>>(`${this.base}/shifts/preview`, this.body(file))
      .pipe(map(unwrap));
  }

  commitShifts(batchId: string, file: File): Observable<ShiftImportPreviewResponse> {
    return this.http
      .post<ApiResponse<ShiftImportPreviewResponse>>(
        `${this.base}/shifts/${batchId}/commit`, this.body(file))
      .pipe(map(unwrap));
  }

  downloadShiftTemplate(): Observable<Blob> {
    return this.http.get(`${this.base}/shifts/template`, { responseType: 'blob' });
  }

  // --------------------------------------------------------------- history

  /** Each import screen shows only its own batches — omit `type` for the combined list. */
  history(type?: ImportBatchType, page = 0, size = 20): Observable<PageResponse<ImportBatchResponse>> {
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
