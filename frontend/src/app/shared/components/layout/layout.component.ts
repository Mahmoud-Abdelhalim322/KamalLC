import { Component, OnInit, OnDestroy, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/auth/auth.service';
import { ApiResponse, NotificationItem } from '../../../core/models/api.models';

interface NavItem {
  icon: string;
  label: string;
  route?: string;
  children?: NavItem[];
  expanded?: boolean;
  superAdminOnly?: boolean;
}

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './layout.component.html',
  styleUrls: ['./layout.component.scss']
})
export class LayoutComponent implements OnInit, OnDestroy {
  sidebarCollapsed = false;
  showNotifications = false;
  notifications: NotificationItem[] = [];
  notificationCount = 0;
  readNotificationIds: Set<string> = new Set<string>();
  page = 0;
  readonly pageSize = 20;
  hasMoreNotifications = true;
  isLoadingMore = false;
  private pollTimer: any;

  navItems: NavItem[] = [
    { icon: 'dashboard', label: 'لوحة التحكم', route: '/dashboard' },
    {
      icon: 'groups', label: 'الأعضاء', expanded: false,
      children: [
        { icon: 'local_shipping', label: 'الكباتن', route: '/captains' },
        { icon: 'engineering', label: 'العاملين', route: '/workers' },
        { icon: 'admin_panel_settings', label: 'الإداريين', route: '/managers' },
      ]
    },
    { icon: 'badge', label: 'سجل المعرفات', route: '/ninja-ids' },
    { icon: 'upload_file', label: 'استيراد البيانات', route: '/imports' },
    { icon: 'event_note', label: 'الشيفتات', route: '/shifts' },
    { icon: 'payments', label: 'مسير الرواتب', route: '/payroll' },
    { icon: 'assessment', label: 'التقارير', route: '/reports' },
    { icon: 'directions_car', label: 'أسطول السيارات', route: '/fleet' },
    { icon: 'inventory_2', label: 'المخزن', route: '/inventory' },
    { icon: 'account_balance_wallet', label: 'سجل العهد', route: '/custody' },
    { icon: 'manage_accounts', label: 'إدارة المستخدمين', route: '/user-management', superAdminOnly: true },
    { icon: 'settings', label: 'الإعدادات', route: '/settings' },
  ];

  constructor(
    public authService: AuthService,
    private http: HttpClient,
    private router: Router,
    private elementRef: ElementRef
  ) {}

  ngOnInit(): void {
    this.loadReadIds();
    this.loadNotifications(true);
    // Refresh notifications count periodically
    this.pollTimer = setInterval(() => this.loadNotifications(true), 30000);
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
    if (user.role === 'SUPER_ADMIN') return 'مدير النظام';
    return '';
  }

  toggleSidebar(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  toggleGroup(item: NavItem): void {
    item.expanded = !item.expanded;
  }

  isVisible(item: NavItem): boolean {
    if (item.superAdminOnly) return this.authService.isSuperAdmin();
    return true;
  }

  logout(): void {
    this.authService.logout();
  }
}
