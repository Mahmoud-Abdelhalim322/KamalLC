import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { FleetService } from '../../core/services/fleet.service';
import { CaptainService } from '../../core/services/captain.service';
import {
  VehicleResponse, VehicleAssignmentResponse, CaptainLookupItem,
  MaintenanceResponse, MaintenanceOpenRequest, MaintenanceCloseRequest
} from '../../core/models/api.models';
import { errorMessage } from '../../core/services/api.util';
import { AuthService } from '../../core/auth/auth.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { SarPipe } from '../../shared/pipes/sar.pipe';
import { TranslationService } from '../../core/i18n/translation.service';

@Component({
  selector: 'app-fleet',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, SarPipe],
  templateUrl: './fleet.component.html',
  styleUrls: ['./fleet.component.scss']
})
export class FleetComponent implements OnInit {
  activeTab: 'vehicles' | 'assignments' | 'maintenance' = 'vehicles';
  public i18n = inject(TranslationService);

  // Vehicles
  vehicles: VehicleResponse[] = [];
  vehiclePage = 0;
  vehicleTotalPages = 0;
  vehicleSearch = '';
  vehicleStatusFilter = '';
  vehicleOwnershipFilter = '';
  vehicleAgentFilter = '';
  rentalAgents: string[] = [];
  isLoading = false;

  // Assignments
  assignments: VehicleAssignmentResponse[] = [];
  assignmentPage = 0;
  assignmentTotalPages = 0;
  assignmentStatusFilter = '';

  // Maintenance
  maintenanceList: MaintenanceResponse[] = [];
  maintenancePage = 0;
  maintenanceTotalPages = 0;
  maintenanceStatusFilter = '';

  // Dialogs
  showVehicleDialog = false;
  showAssignDialog = false;
  showHistoryDialog = false;
  showMaintenanceOpenDialog = false;
  showMaintenanceCloseDialog = false;
  showMaintenanceHistoryDialog = false;
  editingVehicle: VehicleResponse | null = null;
  historyItems: VehicleAssignmentResponse[] = [];
  maintenanceHistoryItems: MaintenanceResponse[] = [];

  // Vehicle form
  vForm = this.emptyVehicleForm();

  // Assign form
  assignVehicleId = '';
  assignVehicleName = '';
  aForm = { captainId: '', expectedReturnAt: '', nextCaptainId: '', notes: '' };
  captainResults: CaptainLookupItem[] = [];
  nextCaptainResults: CaptainLookupItem[] = [];
  maintCaptainResults: CaptainLookupItem[] = [];

  // Maintenance forms
  maintVehicleId = '';
  maintVehicleName = '';
  selectedMaintenanceId: string | null = null;
  selectedMaintenanceDetails: MaintenanceResponse | null = null;
  mOpenForm: MaintenanceOpenRequest = { vehicleId: '', faultReason: '', costBearer: 'COMPANY', parts: [] };
  mCloseForm: MaintenanceCloseRequest = { totalCost: 0, parts: [] };
  closingMaintenanceId = '';
  showMaintenanceDetailsDialog = false;

  error = '';
  successMsg = '';

  constructor(
    private fleetService: FleetService,
    private captainService: CaptainService,
    private http: HttpClient,
    public authService: AuthService
  ) {}

  get canManage(): boolean {
    return this.authService.hasPermission('FLEET_MANAGE');
  }

  ngOnInit(): void {
    this.loadVehicles();
    this.fleetService.getRentalAgents().subscribe(agents => this.rentalAgents = agents);
  }

  // ── Tab Switch ──

  switchTab(tab: 'vehicles' | 'assignments' | 'maintenance'): void {
    this.activeTab = tab;
    if (tab === 'assignments' && this.assignments.length === 0) this.loadAssignments();
    if (tab === 'maintenance' && this.maintenanceList.length === 0) this.loadMaintenance();
  }

  // ── Vehicles ──

  loadVehicles(): void {
    this.isLoading = true;
    this.fleetService.listVehicles({
      search: this.vehicleSearch || undefined,
      status: this.vehicleStatusFilter || undefined,
      ownership: this.vehicleOwnershipFilter || undefined,
      agent: this.vehicleAgentFilter || undefined,
      page: this.vehiclePage, size: 15
    }).subscribe({
      next: p => { this.vehicles = p.content; this.vehicleTotalPages = p.totalPages; this.isLoading = false; },
      error: e => { this.error = errorMessage(e, this.i18n.get('FLEET.LOAD_FAILED')); this.isLoading = false; }
    });
  }

  searchVehicles(): void { this.vehiclePage = 0; this.loadVehicles(); }

  openAddVehicle(): void {
    this.editingVehicle = null;
    this.vForm = this.emptyVehicleForm();
    this.showVehicleDialog = true;
  }

  openEditVehicle(v: VehicleResponse): void {
    this.editingVehicle = v;
    this.vForm = {
      plateNumber: v.plateNumber, ownershipType: v.ownershipType,
      rentalAgentCode: v.rentalAgentCode || '',
      brand: v.brand || '', model: v.model || '', year: v.year, color: v.color || '',
      odometer: v.odometer, inspectionDate: v.inspectionDate || '',
      rentalAgentName: v.rentalAgentName || '', rentalStartDate: v.rentalStartDate || '',
      rentalEndDate: v.rentalEndDate || '',
      registrationNumber: v.registrationNumber || '',
      vin: v.vin || '',
      registrationExpiryDate: v.registrationExpiryDate || '',
      operatingCardNumber: v.operatingCardNumber || '',
      insuranceCompany: v.insuranceCompany || '',
      inspectionExpiryDate: v.inspectionExpiryDate || '',
      notes: v.notes || '', status: v.status
    };
    this.showVehicleDialog = true;
  }

  saveVehicle(): void {
    const req: any = { ...this.vForm };
    if (!req.plateNumber) { this.error = this.i18n.get('FLEET.PLATE_REQUIRED'); return; }

    const obs = this.editingVehicle
      ? this.fleetService.updateVehicle(this.editingVehicle.id, req)
      : this.fleetService.createVehicle(req);

    obs.subscribe({
      next: () => { this.showVehicleDialog = false; this.successMsg = this.i18n.get('FLEET.SAVE_SUCCESS'); this.loadVehicles(); },
      error: e => this.error = errorMessage(e, this.i18n.get('FLEET.SAVE_FAILED'))
    });
  }

  deleteVehicle(v: VehicleResponse): void {
    if (!confirm(this.i18n.get('FLEET.CONFIRM_DELETE_VEHICLE', { plate: v.plateNumber }))) return;
    this.fleetService.deleteVehicle(v.id).subscribe({
      next: () => { this.successMsg = this.i18n.get('FLEET.DELETE_SUCCESS'); this.loadVehicles(); },
      error: e => this.error = errorMessage(e, this.i18n.get('FLEET.DELETE_FAILED'))
    });
  }

  openAssignDialog(v: VehicleResponse): void {
    this.assignVehicleId = v.id;
    this.assignVehicleName = `${v.brand || ''} ${v.model || ''} ${v.plateNumber}`.trim();
    this.aForm = { captainId: '', expectedReturnAt: '', nextCaptainId: '', notes: '' };
    this.captainResults = [];
    this.nextCaptainResults = [];
    this.showAssignDialog = true;
  }

  searchCaptains(query: string, target: 'captain' | 'next' | 'maint'): void {
    this.captainService.lookup(query).subscribe(results => {
      if (target === 'captain') this.captainResults = results;
      else if (target === 'next') this.nextCaptainResults = results;
      else this.maintCaptainResults = results;
    });
  }

  selectCaptain(c: CaptainLookupItem, target: 'captain' | 'next' | 'maint'): void {
    if (target === 'captain') { this.aForm.captainId = c.id; this.captainResults = []; }
    else if (target === 'next') { this.aForm.nextCaptainId = c.id; this.nextCaptainResults = []; }
    else { this.mOpenForm.captainId = c.id; this.maintCaptainResults = []; }
  }

  submitAssign(): void {
    if (!this.aForm.captainId || !this.aForm.expectedReturnAt) {
      this.error = this.i18n.get('FLEET.CAPTAIN_AND_RETURN_REQUIRED'); return;
    }
    this.fleetService.assignVehicle(this.assignVehicleId, {
      captainId: this.aForm.captainId,
      expectedReturnAt: new Date(this.aForm.expectedReturnAt).toISOString(),
      nextCaptainId: this.aForm.nextCaptainId || null,
      notes: this.aForm.notes || null
    }).subscribe({
      next: () => { this.showAssignDialog = false; this.successMsg = this.i18n.get('FLEET.ASSIGN_SUCCESS'); this.loadVehicles(); },
      error: e => this.error = errorMessage(e, this.i18n.get('FLEET.ASSIGN_FAILED'))
    });
  }

  returnAssignment(a: VehicleAssignmentResponse): void {
    if (!confirm(this.i18n.get('FLEET.CONFIRM_RETURN_VEHICLE'))) return;
    this.fleetService.returnVehicle(a.id).subscribe({
      next: () => { this.successMsg = this.i18n.get('FLEET.RETURN_SUCCESS'); this.loadAssignments(); this.loadVehicles(); },
      error: e => this.error = errorMessage(e, this.i18n.get('FLEET.RETURN_FAILED'))
    });
  }

  openHistory(v: VehicleResponse): void {
    this.fleetService.getVehicleHistory(v.id).subscribe({
      next: items => { this.historyItems = items; this.showHistoryDialog = true; },
      error: e => this.error = errorMessage(e, this.i18n.get('FLEET.HISTORY_LOAD_FAILED'))
    });
  }

  // ── Assignments ──

  loadAssignments(): void {
    this.isLoading = true;
    this.fleetService.listAssignments({
      status: this.assignmentStatusFilter || undefined,
      page: this.assignmentPage, size: 15
    }).subscribe({
      next: p => { this.assignments = p.content; this.assignmentTotalPages = p.totalPages; this.isLoading = false; },
      error: e => { this.error = errorMessage(e, this.i18n.get('FLEET.ASSIGNMENTS_LOAD_FAILED')); this.isLoading = false; }
    });
  }

  // ── Maintenance ──

  loadMaintenance(): void {
    this.isLoading = true;
    this.fleetService.listMaintenance({
      status: this.maintenanceStatusFilter || undefined,
      page: this.maintenancePage, size: 15
    }).subscribe({
      next: p => { this.maintenanceList = p.content; this.maintenanceTotalPages = p.totalPages; this.isLoading = false; },
      error: e => { this.error = errorMessage(e, this.i18n.get('FLEET.MAINTENANCE_LOAD_FAILED')); this.isLoading = false; }
    });
  }

  openMaintenanceDialog(v: VehicleResponse): void {
    this.selectedMaintenanceId = null;
    this.maintVehicleId = v.id;
    this.maintVehicleName = `${v.brand || ''} ${v.model || ''} ${v.plateNumber}`.trim();
    this.mOpenForm = { vehicleId: v.id, faultReason: '', costBearer: 'COMPANY', workshopName: '', notes: '', parts: [] };
    this.maintCaptainResults = [];
    this.showMaintenanceOpenDialog = true;
  }

  editMaintenance(m: MaintenanceResponse): void {
    this.selectedMaintenanceId = m.id;
    this.maintVehicleId = m.vehicleId;
    this.maintVehicleName = `${m.vehicleBrand || ''} ${m.vehicleModel || ''} ${m.vehiclePlate}`.trim();
    this.mOpenForm = { 
      vehicleId: m.vehicleId, 
      faultReason: m.faultReason, 
      costBearer: m.costBearer, 
      workshopName: m.workshopName || '', 
      notes: m.notes || '', 
      captainId: m.captainId || undefined,
      parts: m.parts?.map(p => ({ partName: p.partName, partCost: p.partCost })) || [] 
    };
    this.maintCaptainResults = [];
    this.showMaintenanceOpenDialog = true;
  }

  viewMaintenanceDetails(m: MaintenanceResponse): void {
    this.selectedMaintenanceDetails = m;
    this.showMaintenanceDetailsDialog = true;
  }

  addPartToOpen(): void { this.mOpenForm.parts = this.mOpenForm.parts || []; this.mOpenForm.parts.push({ partName: '', partCost: 0 }); }
  removePartFromOpen(index: number): void { this.mOpenForm.parts?.splice(index, 1); }

  submitOpenMaintenance(): void {
    if (!this.mOpenForm.faultReason) { this.error = this.i18n.get('FLEET.FAULT_REASON_REQUIRED'); return; }
    if (this.mOpenForm.costBearer === 'CAPTAIN' && !this.mOpenForm.captainId) { this.error = this.i18n.get('FLEET.CAPTAIN_REQUIRED_FOR_COST'); return; }

    const obs = this.selectedMaintenanceId 
      ? this.fleetService.updateMaintenance(this.selectedMaintenanceId, this.mOpenForm)
      : this.fleetService.openMaintenance(this.mOpenForm);

    obs.subscribe({
      next: () => { 
        this.showMaintenanceOpenDialog = false; 
        this.successMsg = this.selectedMaintenanceId ? this.i18n.get('FLEET.MAINT_UPDATE_SUCCESS') : this.i18n.get('FLEET.MAINT_ENTERED_SUCCESS'); 
        this.loadVehicles(); 
        this.loadMaintenance(); 
      },
      error: e => this.error = errorMessage(e, this.i18n.get('FLEET.MAINT_FAILED'))
    });
  }

  openCloseMaintenanceDialog(m: MaintenanceResponse): void {
    this.closingMaintenanceId = m.id;
    this.mCloseForm = { totalCost: 0, odometerOnClose: undefined, notes: '', parts: m.parts?.map(p => ({ partName: p.partName, partCost: p.partCost })) || [] };
    this.showMaintenanceCloseDialog = true;
  }

  addPartToClose(): void { this.mCloseForm.parts = this.mCloseForm.parts || []; this.mCloseForm.parts.push({ partName: '', partCost: 0 }); }
  removePartFromClose(index: number): void { this.mCloseForm.parts?.splice(index, 1); }

  submitCloseMaintenance(): void {
    this.fleetService.closeMaintenance(this.closingMaintenanceId, this.mCloseForm).subscribe({
      next: () => { this.showMaintenanceCloseDialog = false; this.successMsg = this.i18n.get('FLEET.MAINT_CLOSED_SUCCESS'); this.loadMaintenance(); this.loadVehicles(); },
      error: e => this.error = errorMessage(e, this.i18n.get('FLEET.MAINT_CLOSE_FAILED'))
    });
  }

  cancelMaintenance(m: MaintenanceResponse): void {
    if (!confirm(this.i18n.get('FLEET.CONFIRM_CANCEL_MAINT'))) return;
    this.fleetService.cancelMaintenance(m.id).subscribe({
      next: () => { this.successMsg = this.i18n.get('FLEET.MAINT_CANCELLED_SUCCESS'); this.loadMaintenance(); this.loadVehicles(); },
      error: e => this.error = errorMessage(e, this.i18n.get('FLEET.MAINT_CANCEL_FAILED'))
    });
  }

  openMaintenanceHistory(v: VehicleResponse): void {
    this.fleetService.getMaintenanceHistory(v.id).subscribe({
      next: items => { this.maintenanceHistoryItems = items; this.showMaintenanceHistoryDialog = true; },
      error: e => this.error = errorMessage(e, this.i18n.get('FLEET.HISTORY_LOAD_FAILED'))
    });
  }

  // ── Pagination ──
  prevPage(type: 'vehicle' | 'assignment' | 'maintenance'): void {
    if (type === 'vehicle' && this.vehiclePage > 0) { this.vehiclePage--; this.loadVehicles(); }
    if (type === 'assignment' && this.assignmentPage > 0) { this.assignmentPage--; this.loadAssignments(); }
    if (type === 'maintenance' && this.maintenancePage > 0) { this.maintenancePage--; this.loadMaintenance(); }
  }
  nextPage(type: 'vehicle' | 'assignment' | 'maintenance'): void {
    if (type === 'vehicle' && this.vehiclePage < this.vehicleTotalPages - 1) { this.vehiclePage++; this.loadVehicles(); }
    if (type === 'assignment' && this.assignmentPage < this.assignmentTotalPages - 1) { this.assignmentPage++; this.loadAssignments(); }
    if (type === 'maintenance' && this.maintenancePage < this.maintenanceTotalPages - 1) { this.maintenancePage++; this.loadMaintenance(); }
  }

  statusLabel(s: string): string {
    const map: Record<string, string> = {
      AVAILABLE: 'FLEET.STATUS_AVAILABLE',
      ASSIGNED: 'FLEET.STATUS_ASSIGNED',
      MAINTENANCE: 'FLEET.STATUS_MAINTENANCE',
      DECOMMISSIONED: 'FLEET.STATUS_DECOMMISSIONED',
      ACTIVE: 'FLEET.STATUS_ACTIVE',
      RETURNED: 'FLEET.STATUS_RETURNED',
      LATE: 'FLEET.STATUS_LATE',
      IN_PROGRESS: 'FLEET.STATUS_IN_PROGRESS',
      COMPLETED: 'FLEET.STATUS_COMPLETED',
      CANCELLED: 'FLEET.STATUS_CANCELLED'
    };
    return map[s] ? this.i18n.get(map[s]) : s;
  }

  ownershipLabel(s: string): string {
    if (s === 'RENTED') return this.i18n.get('FLEET.OWNERSHIP_RENTED');
    if (s === 'LEASE_TO_OWN') return this.i18n.get('FLEET.OWNERSHIP_LEASE');
    if (s === 'OTHER') return this.i18n.get('FLEET.OWNERSHIP_OTHER');
    return this.i18n.get('FLEET.OWNERSHIP_OWNED');
  }

  dismissMsg(): void { this.error = ''; this.successMsg = ''; }

  private emptyVehicleForm() {
    return {
      plateNumber: '', ownershipType: 'OWNED', rentalAgentCode: '',
      brand: '', model: '',
      year: null as number | null, color: '', odometer: null as number | null,
      inspectionDate: '', rentalAgentName: '', rentalStartDate: '', rentalEndDate: '',
      registrationNumber: '', vin: '', registrationExpiryDate: '',
      operatingCardNumber: '', insuranceCompany: '', inspectionExpiryDate: '',
      notes: '', status: 'AVAILABLE'
    };
  }

  // ── Export ──

  isExportingExcel = false;

  exportExcel(): void {
    this.isExportingExcel = true;
    const params: Record<string, string> = {};
    if (this.vehicleOwnershipFilter) params['ownership'] = this.vehicleOwnershipFilter;

    this.http.get('/api/fleet/vehicles/export/excel', { params, responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.isExportingExcel = false;
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = this.i18n.get('FLEET.EXPORT_FILENAME');
        a.click();
        URL.revokeObjectURL(a.href);
      },
      error: () => {
        this.isExportingExcel = false;
        this.error = this.i18n.get('FLEET.EXPORT_FAILED');
      }
    });
  }
}
