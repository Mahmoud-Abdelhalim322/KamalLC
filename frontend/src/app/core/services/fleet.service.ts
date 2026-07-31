import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  ApiResponse, PageResponse, VehicleResponse, VehicleRequest,
  VehicleAssignmentResponse, VehicleAssignmentRequest, FleetStats,
  MaintenanceOpenRequest, MaintenanceCloseRequest, MaintenanceResponse
} from '../models/api.models';
import { unwrap, toHttpParams } from './api.util';

@Injectable({ providedIn: 'root' })
export class FleetService {

  private http = inject(HttpClient);
  private readonly base = '/api/fleet';

  // ── Vehicles ──

  listVehicles(params: { search?: string; status?: string; ownership?: string; agent?: string; page?: number; size?: number }): Observable<PageResponse<VehicleResponse>> {
    return this.http.get<ApiResponse<PageResponse<VehicleResponse>>>(`${this.base}/vehicles`, { params: toHttpParams(params) })
      .pipe(map(unwrap));
  }

  getVehicle(id: string): Observable<VehicleResponse> {
    return this.http.get<ApiResponse<VehicleResponse>>(`${this.base}/vehicles/${id}`).pipe(map(unwrap));
  }

  createVehicle(req: VehicleRequest): Observable<VehicleResponse> {
    return this.http.post<ApiResponse<VehicleResponse>>(`${this.base}/vehicles`, req).pipe(map(unwrap));
  }

  updateVehicle(id: string, req: VehicleRequest): Observable<VehicleResponse> {
    return this.http.put<ApiResponse<VehicleResponse>>(`${this.base}/vehicles/${id}`, req).pipe(map(unwrap));
  }

  deleteVehicle(id: string): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.base}/vehicles/${id}`).pipe(map(() => void 0));
  }

  getRentalAgents(): Observable<string[]> {
    return this.http.get<ApiResponse<string[]>>(`${this.base}/rental-agents`).pipe(map(unwrap));
  }

  // ── Assignments ──

  assignVehicle(vehicleId: string, req: VehicleAssignmentRequest): Observable<VehicleAssignmentResponse> {
    return this.http.post<ApiResponse<VehicleAssignmentResponse>>(`${this.base}/vehicles/${vehicleId}/assign`, req).pipe(map(unwrap));
  }

  returnVehicle(assignmentId: string, notes?: string): Observable<VehicleAssignmentResponse> {
    return this.http.post<ApiResponse<VehicleAssignmentResponse>>(`${this.base}/assignments/${assignmentId}/return`, { notes })
      .pipe(map(unwrap));
  }

  listAssignments(params: { status?: string; captainId?: string; page?: number; size?: number }): Observable<PageResponse<VehicleAssignmentResponse>> {
    return this.http.get<ApiResponse<PageResponse<VehicleAssignmentResponse>>>(`${this.base}/assignments`, { params: toHttpParams(params) })
      .pipe(map(unwrap));
  }

  getVehicleHistory(vehicleId: string): Observable<VehicleAssignmentResponse[]> {
    return this.http.get<ApiResponse<VehicleAssignmentResponse[]>>(`${this.base}/vehicles/${vehicleId}/history`).pipe(map(unwrap));
  }

  getStats(): Observable<FleetStats> {
    return this.http.get<ApiResponse<FleetStats>>(`${this.base}/stats`).pipe(map(unwrap));
  }

  // ── Maintenance ──

  openMaintenance(req: MaintenanceOpenRequest): Observable<MaintenanceResponse> {
    return this.http.post<ApiResponse<MaintenanceResponse>>(`${this.base}/maintenance`, req).pipe(map(unwrap));
  }

  updateMaintenance(id: string, req: MaintenanceOpenRequest): Observable<MaintenanceResponse> {
    return this.http.put<ApiResponse<MaintenanceResponse>>(`${this.base}/maintenance/${id}`, req).pipe(map(unwrap));
  }

  closeMaintenance(id: string, req: MaintenanceCloseRequest): Observable<MaintenanceResponse> {
    return this.http.post<ApiResponse<MaintenanceResponse>>(`${this.base}/maintenance/${id}/close`, req).pipe(map(unwrap));
  }

  cancelMaintenance(id: string): Observable<void> {
    return this.http.post<ApiResponse<void>>(`${this.base}/maintenance/${id}/cancel`, {}).pipe(map(() => void 0));
  }

  listMaintenance(params: { status?: string; vehicleId?: string; page?: number; size?: number }): Observable<PageResponse<MaintenanceResponse>> {
    return this.http.get<ApiResponse<PageResponse<MaintenanceResponse>>>(`${this.base}/maintenance`, { params: toHttpParams(params) })
      .pipe(map(unwrap));
  }

  getMaintenanceHistory(vehicleId: string): Observable<MaintenanceResponse[]> {
    return this.http.get<ApiResponse<MaintenanceResponse[]>>(`${this.base}/vehicles/${vehicleId}/maintenance`).pipe(map(unwrap));
  }
}
