import { Routes } from '@angular/router';
import { authGuard, loginGuard, permissionGuard, platformAdminGuard, tenantGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [loginGuard],
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent)
  },
  // ============================================ Platform Admin Routes
  {
    path: 'platform',
    canActivate: [authGuard, platformAdminGuard],
    loadComponent: () => import('./features/platform/platform-layout/platform-layout.component').then(m => m.PlatformLayoutComponent),
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/platform/platform-dashboard/platform-dashboard.component').then(m => m.PlatformDashboardComponent)
      },
      {
        path: 'tenants',
        loadComponent: () => import('./features/platform/tenant-management/tenant-management.component').then(m => m.TenantManagementComponent)
      },
      {
        path: 'plans',
        loadComponent: () => import('./features/platform/plan-management/plan-management.component').then(m => m.PlanManagementComponent)
      },
      {
        path: 'subscriptions',
        loadComponent: () => import('./features/platform/subscription-management/subscription-management.component').then(m => m.SubscriptionManagementComponent)
      },
      {
        path: 'invoices',
        loadComponent: () => import('./features/platform/invoice-management/invoice-management.component').then(m => m.InvoiceManagementComponent)
      },
      {
        path: 'audit-log',
        loadComponent: () => import('./features/platform/audit-log/audit-log.component').then(m => m.AuditLogComponent)
      },
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      }
    ]
  },
  // ============================================ Tenant (Company) Routes
  {
    path: '',
    canActivate: [tenantGuard],

    loadComponent: () => import('./shared/components/layout/layout.component').then(m => m.LayoutComponent),
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'orders',
        loadComponent: () => import('./features/orders/orders.component').then(m => m.OrdersComponent)
      },
      {
        path: 'captains',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_VIEW' },
        loadComponent: () => import('./features/captains/captains.component').then(m => m.CaptainsComponent)
      },
      // Literal segments must precede 'captains/:id' or the detail route swallows them.
      {
        path: 'captains/new',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_MANAGE' },
        loadComponent: () => import('./features/captains/captain-form/captain-form.component').then(m => m.CaptainFormComponent)
      },
      {
        path: 'captains/:id/edit',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_MANAGE' },
        loadComponent: () => import('./features/captains/captain-form/captain-form.component').then(m => m.CaptainFormComponent)
      },
      {
        path: 'captains/:id',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_VIEW' },
        loadComponent: () => import('./features/captains/captain-detail/captain-detail.component').then(m => m.CaptainDetailComponent)
      },
      // ---- Workers ----
      {
        path: 'workers',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_VIEW' },
        loadComponent: () => import('./features/workers/workers.component').then(m => m.WorkersComponent)
      },
      {
        path: 'workers/new',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_MANAGE' },
        loadComponent: () => import('./features/staff-form/staff-form.component').then(m => m.StaffFormComponent)
      },
      {
        path: 'workers/:id/edit',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_MANAGE' },
        loadComponent: () => import('./features/staff-form/staff-form.component').then(m => m.StaffFormComponent)
      },
      {
        path: 'workers/:id',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_VIEW' },
        loadComponent: () => import('./features/staff-form/staff-detail/staff-detail.component').then(m => m.StaffDetailComponent)
      },
      // ---- Managers ----
      {
        path: 'managers',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_VIEW' },
        loadComponent: () => import('./features/managers/managers.component').then(m => m.ManagersComponent)
      },
      {
        path: 'managers/new',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_MANAGE' },
        loadComponent: () => import('./features/staff-form/staff-form.component').then(m => m.StaffFormComponent)
      },
      {
        path: 'managers/:id/edit',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_MANAGE' },
        loadComponent: () => import('./features/staff-form/staff-form.component').then(m => m.StaffFormComponent)
      },
      {
        path: 'managers/:id',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_VIEW' },
        loadComponent: () => import('./features/staff-form/staff-detail/staff-detail.component').then(m => m.StaffDetailComponent)
      },
      // ---- Changelog ----
      {
        path: 'changelog',
        canActivate: [permissionGuard],
        data: { permission: 'CHANGELOG_VIEW' },
        loadComponent: () => import('./features/changelog/changelog.component').then(m => m.ChangeLogComponent)
      },
      // ---- User Management (Company ADMIN only) ----
      {
        path: 'user-management',
        canActivate: [permissionGuard],
        data: { adminOnly: true },
        loadComponent: () => import('./features/user-management/user-management.component').then(m => m.UserManagementComponent)
      },
      // ---- Existing ----
      {
        path: 'ninja-ids',
        canActivate: [permissionGuard],
        data: { permission: 'MEMBERS_VIEW' },
        loadComponent: () => import('./features/ninja-ids/ninja-ids.component').then(m => m.NinjaIdsComponent)
      },
      {
        path: 'imports',
        canActivate: [permissionGuard],
        data: { permission: 'IMPORTS_MANAGE' },
        loadComponent: () => import('./features/imports/imports.component').then(m => m.ImportsComponent)
      },
      {
        path: 'shifts',
        canActivate: [permissionGuard],
        data: { permission: 'SHIFTS_VIEW' },
        loadComponent: () => import('./features/shifts/shifts.component').then(m => m.ShiftsComponent)
      },
      {
        path: 'payroll',
        canActivate: [permissionGuard],
        data: { permission: 'PAYROLL_VIEW' },
        loadComponent: () => import('./features/payroll/payroll.component').then(m => m.PayrollComponent)
      },
      // ---- Loans ----
      {
        path: 'loans',
        canActivate: [permissionGuard],
        data: { permission: 'LOANS_VIEW' },
        loadComponent: () => import('./features/loans/loans.component').then(m => m.LoansComponent)
      },
      {
        path: 'loans/:memberType/:memberId',
        canActivate: [permissionGuard],
        data: { permission: 'LOANS_VIEW' },
        loadComponent: () => import('./features/loans/loan-detail/loan-detail.component').then(m => m.LoanDetailComponent)
      },
      {
        path: 'supplier-revenue',
        canActivate: [permissionGuard],
        data: { permission: 'REPORTS_VIEW' },
        loadComponent: () => import('./features/supplier-revenue/supplier-revenue.component').then(m => m.SupplierRevenueComponent)
      },
      {
        path: 'reports',
        canActivate: [permissionGuard],
        data: { permission: 'REPORTS_VIEW' },
        loadComponent: () => import('./features/reports/reports.component').then(m => m.ReportsComponent)
      },
      {
        path: 'settings',
        canActivate: [permissionGuard],
        data: { permission: 'SETTINGS_MANAGE' },
        loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent)
      },
      {
        path: 'fleet',
        canActivate: [permissionGuard],
        data: { permission: 'FLEET_VIEW' },
        loadComponent: () => import('./features/fleet/fleet.component').then(m => m.FleetComponent)
      },
      {
        path: 'inventory',
        canActivate: [permissionGuard],
        data: { permission: 'INVENTORY_VIEW' },
        loadComponent: () => import('./features/inventory/inventory.component').then(m => m.InventoryComponent)
      },
      {
        path: 'custody',
        canActivate: [permissionGuard],
        data: { permission: 'CUSTODY_VIEW' },
        loadComponent: () => import('./features/custody/custody.component').then(m => m.CustodyComponent)
      },
      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      }
    ]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
