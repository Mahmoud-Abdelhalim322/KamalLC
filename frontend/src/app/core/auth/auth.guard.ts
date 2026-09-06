import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Route guard — redirects to login if not authenticated.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  router.navigate(['/login']);
  return false;
};

/**
 * Tenant guard — only non-platform users (tenant users) can access tenant routes.
 * Platform admins are redirected to platform dashboard.
 */
export const tenantGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    router.navigate(['/login']);
    return false;
  }

  if (authService.isPlatformAdmin()) {
    router.navigate(['/platform/dashboard']);
    return false;
  }

  return true;
};

/**
 * Login guard — redirects to appropriate dashboard if already authenticated.
 */
export const loginGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    // Platform admin goes to platform dashboard
    if (authService.isPlatformAdmin()) {
      router.navigate(['/platform/dashboard']);
    } else {
      router.navigate(['/dashboard']);
    }
    return false;
  }

  return true;
};

/**
 * Permission guard — checks if user has permission or is ADMIN (company admin).
 */
export const permissionGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    router.navigate(['/login']);
    return false;
  }

  // Platform admin shouldn't access tenant routes
  if (authService.isPlatformAdmin()) {
    router.navigate(['/platform/dashboard']);
    return false;
  }

  const adminOnly = route.data?.['adminOnly'] === true || route.data?.['superAdminOnly'] === true;
  if (adminOnly) {
    if (authService.isAdmin()) {
      return true;
    }
    router.navigate(['/dashboard']);
    return false;
  }

  const requiredPermission = route.data?.['permission'] as string | undefined;
  if (!requiredPermission || authService.hasPermission(requiredPermission)) {
    return true;
  }

  router.navigate(['/dashboard']);
  return false;
};

/**
 * Platform admin guard — only PLATFORM_ADMIN can access platform routes.
 */
export const platformAdminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    router.navigate(['/login']);
    return false;
  }

  if (authService.isPlatformAdmin()) {
    return true;
  }

  router.navigate(['/dashboard']);
  return false;
};
