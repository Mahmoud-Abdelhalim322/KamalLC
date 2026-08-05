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
 * Login guard — redirects to dashboard if already authenticated.
 */
export const loginGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    router.navigate(['/dashboard']);
    return false;
  }

  return true;
};

/**
 * Permission guard — checks if user has permission or is SUPER_ADMIN.
 */
export const permissionGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    router.navigate(['/login']);
    return false;
  }

  const superAdminOnly = route.data?.['superAdminOnly'] === true;
  if (superAdminOnly) {
    if (authService.isSuperAdmin()) {
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
