import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import {
  ApiResponse, UserResponse, JobTitleResponse, PermissionInfo,
  CreateUserRequest, JobTitleRequest, StaffResponse, PageResponse,
  UserAllocationSummary, UserAllocationDetail, CaptainSimple, UpdateUserAllocationRequest
} from '../../core/models/api.models';

import { AuthService } from '../../core/auth/auth.service';

import { TranslationService, TranslatePipe } from '../../core/i18n';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.scss']
})
export class UserManagementComponent implements OnInit {
  public translationService = inject(TranslationService);
  activeTab: 'users' | 'roles' | 'allocations' = 'users';

  // Users
  users: UserResponse[] = [];
  jobTitles: JobTitleResponse[] = [];
  allPermissions: PermissionInfo[] = [];
  managerStaff: StaffResponse[] = [];
  isLoading = true;

  // Allocations
  allocations: UserAllocationSummary[] = [];
  selectedAllocationUser: UserAllocationDetail | null = null;
  showAllocationModal = false;
  allocationSearchQuery = '';
  isSavingAllocation = false;
  selectedCaptainIdsSet: Set<string> = new Set<string>();

  // Create user modal
  showUserModal = false;
  newUser: CreateUserRequest = { username: '', password: '', displayName: null, staffId: null, jobTitleId: null };

  // Edit user modal
  showEditUserModal = false;
  editingUserId: string | null = null;
  editingUsername = '';
  editUser: { displayName: string | null; jobTitleId: string | null; staffId: string | null; active: boolean; password?: string | null } = {
    displayName: null,
    jobTitleId: null,
    staffId: null,
    active: true,
    password: null
  };
  isSavingUser = false;

  // Create job title modal
  showRoleModal = false;
  newRole: JobTitleRequest = { name: '', labelAr: '', permissions: [] };
  editingRoleId: string | null = null;

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.loadAll();
  }

  isPrimaryAdmin(user: { role?: string } | null | undefined): boolean {
    if (!user || !user.role) return false;
    return user.role === 'ADMIN' || user.role === 'SUPER_ADMIN';
  }

  loadAll(): void {
    this.isLoading = true;
    this.authService.refreshUserProfile().subscribe();
    this.http.get<ApiResponse<PermissionInfo[]>>('/api/admin/permissions').subscribe(res => {
      if (res.success && res.data) this.allPermissions = res.data;
    });
    this.http.get<ApiResponse<UserResponse[]>>('/api/admin/users').subscribe(res => {
      if (res.success && res.data) this.users = res.data;
      this.isLoading = false;
    });
    this.http.get<ApiResponse<JobTitleResponse[]>>('/api/admin/job-titles').subscribe(res => {
      if (res.success && res.data) this.jobTitles = res.data;
    });
    this.http.get<ApiResponse<PageResponse<StaffResponse>>>('/api/staff?type=MANAGER&size=200').subscribe(res => {
      if (res.success && res.data) this.managerStaff = res.data.content;
    });
    this.loadAllocations();
  }

  loadAllocations(): void {
    this.http.get<UserAllocationSummary[]>('/api/admin/allocations').subscribe(res => {
      if (res) this.allocations = res;
    });
  }

  // ---- Users ----

  openUserModal(): void {
    this.newUser = { username: '', password: '', displayName: null, staffId: null, jobTitleId: null };
    this.showUserModal = true;
  }

  createUser(): void {
    if (!this.newUser.username.trim() || !this.newUser.password.trim()) return;
    this.http.post<ApiResponse<UserResponse>>('/api/admin/users', this.newUser).subscribe({
      next: (res) => {
        if (res.success) {
          this.showUserModal = false;
          this.loadAll();
        }
      }
    });
  }

  openEditUserModal(user: UserResponse): void {
    if (this.isPrimaryAdmin(user)) return;
    this.editingUserId = user.id;
    this.editingUsername = user.username;
    this.editUser = {
      displayName: user.displayName || '',
      jobTitleId: user.jobTitleId || null,
      staffId: user.staffId || null,
      active: user.active,
      password: ''
    };
    this.showEditUserModal = true;
  }

  saveUserChanges(): void {
    if (!this.editingUserId) return;
    this.isSavingUser = true;

    const payload = {
      displayName: this.editUser.displayName ? this.editUser.displayName.trim() : null,
      jobTitleId: this.editUser.jobTitleId || null,
      staffId: this.editUser.staffId || null,
      active: this.editUser.active,
      password: this.editUser.password && this.editUser.password.trim() ? this.editUser.password.trim() : null
    };

    this.http.put<ApiResponse<UserResponse>>(`/api/admin/users/${this.editingUserId}`, payload).subscribe({
      next: (res) => {
        this.isSavingUser = false;
        if (res.success) {
          this.showEditUserModal = false;
          this.loadAll();
        }
      },
      error: () => {
        this.isSavingUser = false;
      }
    });
  }

  toggleUserActive(user: UserResponse): void {
    if (this.isPrimaryAdmin(user)) return;
    this.http.put<ApiResponse<UserResponse>>(`/api/admin/users/${user.id}`, {
      displayName: user.displayName,
      jobTitleId: user.jobTitleId,
      staffId: user.staffId,
      active: !user.active,
      password: null
    }).subscribe(() => this.loadAll());
  }

  deleteUser(user: UserResponse): void {
    if (this.isPrimaryAdmin(user)) return;
    if (!confirm(`هل أنت متأكد من حذف حساب ${user.username}؟`)) return;
    this.http.delete<ApiResponse<void>>(`/api/admin/users/${user.id}`)
      .subscribe(() => this.loadAll());
  }


  // ---- Job Titles ----

  openRoleModal(role?: JobTitleResponse): void {
    if (role) {
      this.editingRoleId = role.id;
      this.newRole = { name: role.name, labelAr: role.labelAr, permissions: [...role.permissions] };
    } else {
      this.editingRoleId = null;
      this.newRole = { name: '', labelAr: '', permissions: [] };
    }
    this.showRoleModal = true;
  }

  togglePermission(perm: string): void {
    const idx = this.newRole.permissions.indexOf(perm);
    if (idx >= 0) this.newRole.permissions.splice(idx, 1);
    else this.newRole.permissions.push(perm);
  }

  hasPermission(perm: string): boolean {
    return this.newRole.permissions.includes(perm);
  }

  saveRole(): void {
    if (!this.newRole.labelAr.trim()) return;
    const obs = this.editingRoleId
      ? this.http.put<ApiResponse<JobTitleResponse>>(`/api/admin/job-titles/${this.editingRoleId}`, this.newRole)
      : this.http.post<ApiResponse<JobTitleResponse>>('/api/admin/job-titles', this.newRole);

    obs.subscribe({
      next: (res) => {
        if (res.success) {
          this.showRoleModal = false;
          this.loadAll();
        }
      }
    });
  }

  deleteRole(role: JobTitleResponse): void {
    if (role.systemDefined) return;
    if (!confirm(`هل أنت متأكد من حذف وظيفة ${role.labelAr}؟`)) return;
    this.http.delete<ApiResponse<void>>(`/api/admin/job-titles/${role.id}`)
      .subscribe(() => this.loadAll());
  }

  getJobTitleLabel(id: string | null): string {
    if (!id) return '—';
    return this.jobTitles.find(jt => jt.id === id)?.labelAr || '—';
  }

  getPermLabel(name: string): string {
    return this.allPermissions.find(p => p.name === name)?.labelAr || name;
  }

  // ---- Allocation Methods ----

  openAllocationModal(userId: string): void {
    this.allocationSearchQuery = '';
    this.selectedCaptainIdsSet.clear();
    this.http.get<UserAllocationDetail>(`/api/admin/allocations/users/${userId}`).subscribe(res => {
      if (res) {
        this.selectedAllocationUser = res;
        res.assignedCaptains.forEach(c => this.selectedCaptainIdsSet.add(c.id));
        this.showAllocationModal = true;
      }
    });
  }

  getAllCaptainsForModal(): CaptainSimple[] {
    if (!this.selectedAllocationUser) return [];
    return [...this.selectedAllocationUser.assignedCaptains, ...this.selectedAllocationUser.availableCaptains];
  }

  getFilteredCaptains(): CaptainSimple[] {
    const all = this.getAllCaptainsForModal();
    if (!this.allocationSearchQuery.trim()) return all;
    const q = this.allocationSearchQuery.trim().toLowerCase();
    return all.filter(c => c.fullNameAr?.toLowerCase().includes(q) || c.kamalCode?.toLowerCase().includes(q) || c.phone?.includes(q));
  }

  isCaptainSelected(captainId: string): boolean {
    return this.selectedCaptainIdsSet.has(captainId);
  }

  toggleCaptainSelection(captainId: string): void {
    if (this.selectedCaptainIdsSet.has(captainId)) {
      this.selectedCaptainIdsSet.delete(captainId);
    } else {
      this.selectedCaptainIdsSet.add(captainId);
    }
  }

  selectAllFilteredCaptains(): void {
    this.getFilteredCaptains().forEach(c => this.selectedCaptainIdsSet.add(c.id));
  }

  deselectAllCaptains(): void {
    this.selectedCaptainIdsSet.clear();
  }

  saveAllocation(): void {
    if (!this.selectedAllocationUser) return;
    this.isSavingAllocation = true;

    const payload: UpdateUserAllocationRequest = {
      dataScope: this.selectedAllocationUser.dataScope,
      captainIds: Array.from(this.selectedCaptainIdsSet)
    };

    this.http.put<UserAllocationDetail>(`/api/admin/allocations/users/${this.selectedAllocationUser.userId}`, payload).subscribe({
      next: () => {
        this.isSavingAllocation = false;
        this.showAllocationModal = false;
        this.loadAllocations();
      },
      error: () => {
        this.isSavingAllocation = false;
      }
    });
  }
}
