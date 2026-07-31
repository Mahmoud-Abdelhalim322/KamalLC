import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  ApiResponse, PageResponse, AssetCategoryResponse, AssetCategoryRequest,
  AssetItemResponse, AssetItemRequest
} from '../models/api.models';
import { unwrap, toHttpParams } from './api.util';

@Injectable({ providedIn: 'root' })
export class InventoryService {

  private http = inject(HttpClient);
  private readonly base = '/api/inventory';

  // ── Categories ──

  getCategories(): Observable<AssetCategoryResponse[]> {
    return this.http.get<ApiResponse<AssetCategoryResponse[]>>(`${this.base}/categories`).pipe(map(unwrap));
  }

  createCategory(req: AssetCategoryRequest): Observable<AssetCategoryResponse> {
    return this.http.post<ApiResponse<AssetCategoryResponse>>(`${this.base}/categories`, req).pipe(map(unwrap));
  }

  deleteCategory(id: string): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.base}/categories/${id}`).pipe(map(() => void 0));
  }

  // ── Items ──

  listItems(params: { categoryId: string; status?: string; search?: string; page?: number; size?: number }): Observable<PageResponse<AssetItemResponse>> {
    return this.http.get<ApiResponse<PageResponse<AssetItemResponse>>>(`${this.base}/items`, { params: toHttpParams(params) })
      .pipe(map(unwrap));
  }

  createItem(req: AssetItemRequest): Observable<AssetItemResponse> {
    return this.http.post<ApiResponse<AssetItemResponse>>(`${this.base}/items`, req).pipe(map(unwrap));
  }

  updateItem(id: string, req: AssetItemRequest): Observable<AssetItemResponse> {
    return this.http.put<ApiResponse<AssetItemResponse>>(`${this.base}/items/${id}`, req).pipe(map(unwrap));
  }

  updateStatus(id: string, status: string): Observable<AssetItemResponse> {
    return this.http.patch<ApiResponse<AssetItemResponse>>(`${this.base}/items/${id}/status`, { status }).pipe(map(unwrap));
  }

  assignItem(id: string, captainId: string, notes?: string): Observable<AssetItemResponse> {
    return this.http.post<ApiResponse<AssetItemResponse>>(`${this.base}/items/${id}/assign`, { captainId, notes }).pipe(map(unwrap));
  }

  returnItem(id: string): Observable<AssetItemResponse> {
    return this.http.post<ApiResponse<AssetItemResponse>>(`${this.base}/items/${id}/return`, {}).pipe(map(unwrap));
  }
}
