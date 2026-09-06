import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

/**
 * JWT interceptor — attaches Bearer token to all API requests.
 * Only triggers logout on 401 (session expired), not 403 (insufficient permissions).
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  if (token) {
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Session expired or invalid token — force re-login
        authService.logout();
      }
      // 403 = Forbidden (permission denied) — do NOT logout, the session is still valid
      return throwError(() => error);
    })
  );
};

