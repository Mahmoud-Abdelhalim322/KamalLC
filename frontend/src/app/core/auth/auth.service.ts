import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import { ApiResponse, LoginRequest, LoginResponse } from '../models/api.models';

const TOKEN_KEY = 'kamal_token';
const USER_KEY = 'kamal_user';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasToken());
  isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  private userSubject = new BehaviorSubject<LoginResponse | null>(this.getUser());
  user$ = this.userSubject.asObservable();

  constructor(
    private http: HttpClient,
    private router: Router
  ) {}

  refreshUserProfile(): Observable<ApiResponse<LoginResponse>> {
    return this.http.get<ApiResponse<LoginResponse>>('/api/auth/me').pipe(
      tap(response => {
        if (response.success && response.data) {
          this.setSession(response.data);
        }
      })
    );
  }

  login(request: LoginRequest): Observable<ApiResponse<LoginResponse>> {
    return this.http.post<ApiResponse<LoginResponse>>('/api/auth/login', request).pipe(
      tap(response => {
        if (response.success && response.data) {
          this.setSession(response.data);
        }
      })
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.isAuthenticatedSubject.next(false);
    this.userSubject.next(null);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getUser(): LoginResponse | null {
    const user = localStorage.getItem(USER_KEY);
    return user ? JSON.parse(user) : null;
  }

  isLoggedIn(): boolean {
    return this.hasToken();
  }

  isSuperAdmin(): boolean {
    return this.getUser()?.role === 'SUPER_ADMIN';
  }

  hasPermission(permission: string): boolean {
    const user = this.getUser();
    if (!user) return false;
    if (user.role === 'SUPER_ADMIN') return true;
    if (!user.permissions) return false;

    if (user.permissions.includes(permission)) return true;

    // MANAGE permissions imply VIEW permissions
    if (permission.endsWith('_VIEW')) {
      const managePermission = permission.replace('_VIEW', '_MANAGE');
      if (user.permissions.includes(managePermission)) return true;
    }

    return false;
  }

  hasAnyPermission(...permissions: string[]): boolean {
    return permissions.some(p => this.hasPermission(p));
  }

  getDisplayName(): string {
    const user = this.getUser();
    return user?.displayName || user?.username || 'مدير';
  }

  private setSession(loginResponse: LoginResponse): void {
    localStorage.setItem(TOKEN_KEY, loginResponse.token);
    localStorage.setItem(USER_KEY, JSON.stringify(loginResponse));
    this.isAuthenticatedSubject.next(true);
    this.userSubject.next(loginResponse);
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(TOKEN_KEY);
  }
}

