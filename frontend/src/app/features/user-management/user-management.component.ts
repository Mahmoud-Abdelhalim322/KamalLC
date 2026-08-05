import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import {
  ApiResponse, UserResponse, JobTitleResponse, PermissionInfo,
  CreateUserRequest, JobTitleRequest, StaffResponse, PageResponse
} from '../../core/models/api.models';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.scss']
})
export class UserManagementComponent implements OnInit {
  activeTab: 'users' | 'roles' = 'users';

  // Users
  users: UserResponse[] = [];
  jobTitles: JobTitleResponse[] = [];
  allPermissions: PermissionInfo[] = [];
  managerStaff: StaffResponse[] = [];
  isLoading = true;

  // Create user modal
  showUserModal = false;
  newUser: CreateUserRequest = { username: '', password: '', displayName: null, staffId: null, jobTitleId: null };

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

  toggleUserActive(user: UserResponse): void {
    this.http.put<ApiResponse<UserResponse>>(`/api/admin/users/${user.id}`, {
      displayName: user.displayName, jobTitleId: user.jobTitleId,
      active: !user.active, password: null
    }).subscribe(() => this.loadAll());
  }

  deleteUser(user: UserResponse): void {
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
}
