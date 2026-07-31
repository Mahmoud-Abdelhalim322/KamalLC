import { Routes } from '@angular/router';
import { authGuard, loginGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [loginGuard],
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shared/components/layout/layout.component').then(m => m.LayoutComponent),
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'captains',
        loadComponent: () => import('./features/captains/captains.component').then(m => m.CaptainsComponent)
      },
      // Literal segments must precede 'captains/:id' or the detail route swallows them.
      {
        path: 'captains/new',
        loadComponent: () => import('./features/captains/captain-form/captain-form.component').then(m => m.CaptainFormComponent)
      },
      {
        path: 'captains/:id/edit',
        loadComponent: () => import('./features/captains/captain-form/captain-form.component').then(m => m.CaptainFormComponent)
      },
      {
        path: 'captains/:id',
        loadComponent: () => import('./features/captains/captain-detail/captain-detail.component').then(m => m.CaptainDetailComponent)
      },
      // ---- Workers ----
      {
        path: 'workers',
        loadComponent: () => import('./features/workers/workers.component').then(m => m.WorkersComponent)
      },
      {
        path: 'workers/new',
        loadComponent: () => import('./features/staff-form/staff-form.component').then(m => m.StaffFormComponent)
      },
      {
        path: 'workers/:id/edit',
        loadComponent: () => import('./features/staff-form/staff-form.component').then(m => m.StaffFormComponent)
      },
      {
        path: 'workers/:id',
        loadComponent: () => import('./features/staff-form/staff-detail/staff-detail.component').then(m => m.StaffDetailComponent)
      },
      // ---- Managers ----
      {
        path: 'managers',
        loadComponent: () => import('./features/managers/managers.component').then(m => m.ManagersComponent)
      },
      {
        path: 'managers/new',
        loadComponent: () => import('./features/staff-form/staff-form.component').then(m => m.StaffFormComponent)
      },
      {
        path: 'managers/:id/edit',
        loadComponent: () => import('./features/staff-form/staff-form.component').then(m => m.StaffFormComponent)
      },
      {
        path: 'managers/:id',
        loadComponent: () => import('./features/staff-form/staff-detail/staff-detail.component').then(m => m.StaffDetailComponent)
      },
      // ---- User Management (SUPER_ADMIN only) ----
      {
        path: 'user-management',
        loadComponent: () => import('./features/user-management/user-management.component').then(m => m.UserManagementComponent)
      },
      // ---- Existing ----
      {
        path: 'ninja-ids',
        loadComponent: () => import('./features/ninja-ids/ninja-ids.component').then(m => m.NinjaIdsComponent)
      },
      {
        path: 'imports',
        loadComponent: () => import('./features/imports/imports.component').then(m => m.ImportsComponent)
      },
      {
        path: 'shifts',
        loadComponent: () => import('./features/shifts/shifts.component').then(m => m.ShiftsComponent)
      },
      {
        path: 'payroll',
        loadComponent: () => import('./features/payroll/payroll.component').then(m => m.PayrollComponent)
      },
      {
        path: 'reports',
        loadComponent: () => import('./features/reports/reports.component').then(m => m.ReportsComponent)
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent)
      },
      {
        path: 'fleet',
        loadComponent: () => import('./features/fleet/fleet.component').then(m => m.FleetComponent)
      },
      {
        path: 'inventory',
        loadComponent: () => import('./features/inventory/inventory.component').then(m => m.InventoryComponent)
      },
      {
        path: 'custody',
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
