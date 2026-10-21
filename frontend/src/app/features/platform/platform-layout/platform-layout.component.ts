import { Component, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../core/auth/auth.service';
import { ThemeService } from '../../../core/services/theme.service';
import { TranslationService, TranslatePipe } from '../../../core/i18n';
import { LanguageSwitcherComponent } from '../../../shared/components/language-switcher/language-switcher.component';

import { HeroBandDirective } from '../../../shared/components/layout/hero-band.directive';

@Component({
  selector: 'app-platform-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    TranslatePipe,
    LanguageSwitcherComponent,
    HeroBandDirective
  ],
  templateUrl: './platform-layout.component.html',
  styleUrl: './platform-layout.component.scss'
})
export class PlatformLayoutComponent {
  readonly themeService = inject(ThemeService);
  readonly authService = inject(AuthService);
  readonly translationService = inject(TranslationService);
  private readonly router = inject(Router);

  sidebarCollapsed = false;

  readonly navItems = [
    { route: '/platform/dashboard', icon: 'space_dashboard', key: 'NAV.PLATFORM_DASHBOARD' },
    { route: '/platform/tenants', icon: 'domain', key: 'NAV.PLATFORM_TENANTS' },
    { route: '/platform/plans', icon: 'workspace_premium', key: 'NAV.PLATFORM_PLANS' },
    { route: '/platform/subscriptions', icon: 'autorenew', key: 'NAV.PLATFORM_SUBSCRIPTIONS' },
    { route: '/platform/invoices', icon: 'receipt_long', key: 'NAV.PLATFORM_INVOICES' },
    { route: '/platform/audit-log', icon: 'policy', key: 'NAV.PLATFORM_AUDIT_LOG' },
  ];

  /** Two-letter monogram for the avatar in the sidebar user card. */
  get initials(): string {
    const parts = this.authService.getDisplayName().trim().split(/\s+/).filter(Boolean);
    return parts.slice(0, 2).map(p => p[0]).join('').toUpperCase() || '؟';
  }

  logout(): void {
    this.authService.logout();
  }
}
