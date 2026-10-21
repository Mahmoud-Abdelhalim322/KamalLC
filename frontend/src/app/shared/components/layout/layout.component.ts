import { Component, OnInit, OnDestroy, ElementRef, HostListener, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/auth/auth.service';
import { ThemeService } from '../../../core/services/theme.service';
import { TranslationService, TranslatePipe } from '../../../core/i18n';
import { LanguageSwitcherComponent } from '../language-switcher/language-switcher.component';
import { ApiResponse, NotificationItem } from '../../../core/models/api.models';

interface NavItem {
  icon: string;
  label: string;
  translationKey?: string;
  route: string;
  superAdminOnly?: boolean;
  permission?: string;
}

interface NavSection {
  labelKey?: string;
  items: NavItem[];
}

import { HeroBandDirective } from './hero-band.directive';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    TranslatePipe,
    LanguageSwitcherComponent,
    HeroBandDirective
  ],
  templateUrl: './layout.component.html',
  styleUrls: ['./layout.component.scss']
})
export class LayoutComponent implements OnInit, OnDestroy {
  sidebarCollapsed = false;
  globalSearch = '';
  showNotifications = false;
  notifications: NotificationItem[] = [];
  notificationCount = 0;
  readNotificationIds: Set<string> = new Set<string>();
  page = 0;
  readonly pageSize = 20;
  hasMoreNotifications = true;
  isLoadingMore = false;
  dataScopeInfo: { dataScope: string; restricted: boolean; assignedCaptainCount: number | null } | null = null;
  private pollTimer: any;

  readonly authService = inject(AuthService);
  readonly themeService = inject(ThemeService);
  readonly translationService = inject(TranslationService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly elementRef = inject(ElementRef);

  /** Sidebar sections; a section's heading is hidden when none of its items are visible. */
  navSections: NavSection[] = [
    {
      items: [
        { icon: 'dashboard', label: 'Dashboard', translationKey: 'NAV.DASHBOARD', route: '/dashboard' },
        { icon: 'shopping_bag', label: 'Orders', translationKey: 'NAV.ORDERS', route: '/orders' },
      ]
    },
    {
      labelKey: 'NAV.SECTION_MEMBERS',
      items: [
        { icon: 'local_shipping', label: 'Captains', translationKey: 'NAV.CAPTAINS', route: '/captains', permission: 'MEMBERS_VIEW' },
        { icon: 'engineering', label: 'Workers', translationKey: 'NAV.WORKERS', route: '/workers', permission: 'MEMBERS_VIEW' },
        { icon: 'admin_panel_settings', label: 'Managers', translationKey: 'NAV.MANAGERS', route: '/managers', permission: 'MEMBERS_VIEW' },
        { icon: 'badge', label: 'Ninja IDs', translationKey: 'NAV.NINJA_IDS', route: '/ninja-ids', permission: 'MEMBERS_VIEW' },
      ]
    },
    {
      labelKey: 'NAV.SECTION_OPERATIONS',
      items: [
        { icon: 'upload_file', label: 'Data Import', translationKey: 'NAV.IMPORTS', route: '/imports', permission: 'IMPORTS_MANAGE' },
        { icon: 'event_note', label: 'Shifts', translationKey: 'NAV.SHIFTS', route: '/shifts', permission: 'SHIFTS_VIEW' },
        { icon: 'payments', label: 'Payroll', translationKey: 'NAV.PAYROLL', route: '/payroll', permission: 'PAYROLL_VIEW' },
        { icon: 'account_balance_wallet', label: 'Loans', translationKey: 'NAV.LOANS', route: '/loans', permission: 'LOANS_VIEW' },
      ]
    },
    {
      labelKey: 'NAV.SECTION_FINANCE',
      items: [
        { icon: 'account_balance', label: 'Supplier Revenue', translationKey: 'NAV.SUPPLIER_REVENUE', route: '/supplier-revenue', permission: 'REPORTS_VIEW' },
        { icon: 'assessment', label: 'Reports', translationKey: 'NAV.REPORTS', route: '/reports', permission: 'REPORTS_VIEW' },
      ]
    },
    {
      labelKey: 'NAV.SECTION_ASSETS',
      items: [
        { icon: 'directions_car', label: 'Car Fleet', translationKey: 'NAV.FLEET', route: '/fleet', permission: 'FLEET_VIEW' },
        { icon: 'inventory_2', label: 'Inventory', translationKey: 'NAV.INVENTORY', route: '/inventory', permission: 'INVENTORY_VIEW' },
        { icon: 'handshake', label: 'Custody Log', translationKey: 'NAV.CUSTODY', route: '/custody', permission: 'CUSTODY_VIEW' },
      ]
    },
    {
      labelKey: 'NAV.SECTION_SYSTEM',
      items: [
        { icon: 'history', label: 'Changelog', translationKey: 'NAV.CHANGELOG', route: '/changelog', permission: 'CHANGELOG_VIEW' },
        { icon: 'manage_accounts', label: 'User Management', translationKey: 'NAV.USER_MANAGEMENT', route: '/user-management', superAdminOnly: true },
        { icon: 'settings', label: 'Settings', translationKey: 'NAV.SETTINGS', route: '/settings', permission: 'SETTINGS_MANAGE' },
      ]
    },
  ];

  ngOnInit(): void {
    if (this.authService.isPlatformAdmin()) {
      this.router.navigate(['/platform/dashboard']);
      return;
    }
    this.themeService.loadTheme();
    this.loadReadIds();
    this.loadNotifications(true);
    this.loadDataScopeInfo();
    this.authService.refreshUserProfile().subscribe();
    // Refresh notifications count & permissions periodically
    this.pollTimer = setInterval(() => {
      this.loadNotifications(true);
      this.loadDataScopeInfo();
      this.authService.refreshUserProfile().subscribe();
    }, 30000);
  }

  loadDataScopeInfo(): void {
    this.http.get<{ dataScope: string; restricted: boolean; assignedCaptainCount: number | null }>('/api/auth/me/data-scope').subscribe(res => {
      if (res) this.dataScopeInfo = res;
    });
  }

  @HostListener('window:focus')
  onWindowFocus(): void {
    if (this.authService.isLoggedIn()) {
      this.authService.refreshUserProfile().subscribe();
    }
  }

  ngOnDestroy(): void {
    if (this.pollTimer) {
      clearInterval(this.pollTimer);
    }
  }

  loadReadIds(): void {
    try {
      const saved = localStorage.getItem('kamalres_read_notifications');
      if (saved) {
        this.readNotificationIds = new Set(JSON.parse(saved));
      }
    } catch (e) {
      this.readNotificationIds = new Set();
    }
  }

  saveReadIds(): void {
    try {
      localStorage.setItem('kamalres_read_notifications', JSON.stringify(Array.from(this.readNotificationIds)));
    } catch (e) {}
  }

  updateNotificationCount(): void {
    this.notificationCount = this.notifications.filter(n => !n.isRead).length;
  }

  loadNotifications(reset: boolean = true): void {
    if (reset) {
      this.page = 0;
      this.hasMoreNotifications = true;
    }

    this.http.get<ApiResponse<NotificationItem[]>>(`/api/notifications?page=${this.page}&limit=${this.pageSize}`).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          const fetched = res.data.map(item => ({
            ...item,
            isRead: this.readNotificationIds.has(item.id)
          }));

          if (reset) {
            this.notifications = fetched;
          } else {
            const existingIds = new Set(this.notifications.map(n => n.id));
            const newOnly = fetched.filter(n => !existingIds.has(n.id));
            this.notifications = [...this.notifications, ...newOnly];
          }

          if (fetched.length < this.pageSize) {
            this.hasMoreNotifications = false;
          }
          this.updateNotificationCount();
        }
        this.isLoadingMore = false;
      },
      error: () => {
        this.isLoadingMore = false;
      }
    });
  }

  onNotifScroll(event: Event): void {
    const el = event.target as HTMLElement;
    if (!el) return;
    const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    if (distanceToBottom < 40 && this.hasMoreNotifications && !this.isLoadingMore) {
      this.isLoadingMore = true;
      this.page++;
      this.loadNotifications(false);
    }
  }

  toggleNotifications(): void {
    this.showNotifications = !this.showNotifications;
    if (this.showNotifications) {
      this.loadNotifications(true);
    }
  }

  markAllAsRead(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.notifications.forEach(item => {
      item.isRead = true;
      this.readNotificationIds.add(item.id);
    });
    this.saveReadIds();
    this.updateNotificationCount();
  }

  openNotification(item: NotificationItem): void {
    if (!item.isRead) {
      item.isRead = true;
      this.readNotificationIds.add(item.id);
      this.saveReadIds();
      this.updateNotificationCount();
    }
    this.showNotifications = false;
    this.router.navigateByUrl(item.targetUrl);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.showNotifications = false;
    }
  }

  get username(): string {
    return this.authService.getDisplayName();
  }

  get userRole(): string {
    const user = this.authService.getUser();
    if (!user) return '';
    if (user.role === 'PLATFORM_ADMIN') return this.translationService.translate('HEADER.ROLE_PLATFORM_ADMIN');
    if (user.role === 'ADMIN' || user.role === 'SUPER_ADMIN') return this.translationService.translate('HEADER.ROLE_ADMIN');
    if (user.role === 'MANAGER') return this.translationService.translate('HEADER.ROLE_MANAGER');
    return this.translationService.translate('HEADER.ROLE_STAFF');
  }

  /** Top-bar search: looks captains up by name, code, ID or phone on the captains page. */
  submitSearch(): void {
    const term = this.globalSearch.trim();
    this.router.navigate(['/captains'], { queryParams: term ? { search: term } : {} });
  }

  /** Two-letter monogram for the avatar in the sidebar user card. */
  get initials(): string {
    const parts = this.username.trim().split(/\s+/).filter(Boolean);
    return parts.slice(0, 2).map(p => p[0]).join('').toUpperCase() || '؟';
  }

  isVisible(item: NavItem): boolean {
    if (item.superAdminOnly) {
      return this.authService.isAdmin() || this.authService.isPlatformAdmin();
    }
    if (item.permission) {
      return this.authService.hasPermission(item.permission);
    }
    return true;
  }

  visibleItems(section: NavSection): NavItem[] {
    return section.items.filter(item => this.isVisible(item));
  }

  logout(): void {
    this.authService.logout();
  }
}
