import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { ApiResponse, CaptainLookupItem } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class CaptainService {

  private http = inject(HttpClient);
  private readonly base = '/api/captains';

  /**
   * Search-as-you-type picker source. The server caps this at 10 rows, so it is a
   * suggestion list — never treat the empty-query result as the full roster.
   */
  lookup(query = ''): Observable<CaptainLookupItem[]> {
    return this.http
      .get<ApiResponse<CaptainLookupItem[]>>(`${this.base}/lookup`, { params: { q: query } })
      .pipe(map(res => res.data ?? []));
  }
}
