import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { ApiResponse, SettingResponse, SettingsByGroup } from '../models/api.models';
import { unwrap } from './api.util';

@Injectable({ providedIn: 'root' })
export class SettingsService {

  private http = inject(HttpClient);
  private readonly base = '/api/settings';

  /** Currently-active settings, already grouped by section for the screen. */
  current(): Observable<SettingsByGroup> {
    return this.http
      .get<ApiResponse<SettingsByGroup>>(this.base)
      .pipe(map(res => res.data ?? {}));
  }

  /**
   * Every past and future value for one key. Settings are effective-dated so a rate
   * change never rewrites an already-computed payroll — the history shows that trail.
   */
  history(key: string): Observable<SettingResponse[]> {
    return this.http
      .get<ApiResponse<SettingResponse[]>>(`${this.base}/${key}/history`)
      .pipe(map(res => res.data ?? []));
  }

  /**
   * Closes the current row and opens a new one from `effectiveFrom`, so shifts
   * already priced under the old value keep it.
   */
  update(key: string, value: string, effectiveFrom: string): Observable<SettingResponse> {
    return this.http
      .put<ApiResponse<SettingResponse>>(`${this.base}/${key}`, { value, effectiveFrom })
      .pipe(map(unwrap));
  }

  uploadLogo(file: File): Observable<SettingResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http
      .post<ApiResponse<SettingResponse>>(`${this.base}/logo`, formData)
      .pipe(map(unwrap));
  }
}
