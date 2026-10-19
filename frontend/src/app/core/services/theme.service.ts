import { Injectable, signal, inject } from '@angular/core';
import { SettingsService } from './settings.service';
import { SettingsByGroup } from '../models/api.models';

export interface ThemeSettings {
  logoUrl: string;
  sidebarBg: string;
  topbarBg: string;
  sidebarActiveBg: string;
  sidebarTextColor: string;
  brandText: string;
}

export const DEFAULT_THEME: ThemeSettings = {
  logoUrl: 'logo-ksclc.png',
  sidebarBg: '#F5B000',
  topbarBg: '#F5B000',
  sidebarActiveBg: '#D98E04',
  sidebarTextColor: '#000000',
  brandText: 'Kamal Bin Khalaf Al-Sulami Logistics Co.',
};

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private settingsService = inject(SettingsService);

  logoUrl = signal<string>(DEFAULT_THEME.logoUrl);
  sidebarBg = signal<string>(DEFAULT_THEME.sidebarBg);
  topbarBg = signal<string>(DEFAULT_THEME.topbarBg);
  sidebarActiveBg = signal<string>(DEFAULT_THEME.sidebarActiveBg);
  sidebarTextColor = signal<string>(DEFAULT_THEME.sidebarTextColor);
  brandText = signal<string>(DEFAULT_THEME.brandText);

  constructor() {
    this.applyCssVariables(this.sidebarBg(), this.topbarBg(), this.sidebarActiveBg(), this.sidebarTextColor());
  }

  /** Fetch active appearance settings and apply to root CSS and signals */
  loadTheme(): void {
    this.settingsService.current().subscribe({
      next: (grouped: SettingsByGroup) => {
        this.extractAndApply(grouped);
      },
      error: (err) => {
        console.warn('Could not load theme settings, keeping defaults', err);
      }
    });
  }

  /** Apply grouped settings directly (e.g. after settings refresh) */
  extractAndApply(grouped: SettingsByGroup): void {
    const appearance = grouped['APPEARANCE'] || [];
    let logo = DEFAULT_THEME.logoUrl;
    let sBg = DEFAULT_THEME.sidebarBg;
    let tBg = DEFAULT_THEME.topbarBg;
    let saBg = DEFAULT_THEME.sidebarActiveBg;
    let sTextColor = DEFAULT_THEME.sidebarTextColor;
    let brand = DEFAULT_THEME.brandText;

    for (const setting of appearance) {
      if (setting.settingKey === 'THEME_LOGO' && setting.value) {
        logo = setting.value;
      } else if (setting.settingKey === 'THEME_SIDEBAR_BG' && setting.value) {
        sBg = setting.value;
      } else if (setting.settingKey === 'THEME_TOPBAR_BG' && setting.value) {
        tBg = setting.value;
      } else if (setting.settingKey === 'THEME_SIDEBAR_ACTIVE_BG' && setting.value) {
        saBg = setting.value;
      } else if (setting.settingKey === 'THEME_SIDEBAR_TEXT_COLOR' && setting.value) {
        sTextColor = setting.value;
      } else if (setting.settingKey === 'THEME_BRAND_TEXT' && setting.value) {
        brand = setting.value;
      }
    }

    this.updateTheme({ logoUrl: logo, sidebarBg: sBg, topbarBg: tBg, sidebarActiveBg: saBg, sidebarTextColor: sTextColor, brandText: brand });
  }

  /** Live update signals and root CSS properties */
  updateTheme(theme: Partial<ThemeSettings>): void {
    if (theme.logoUrl !== undefined) this.logoUrl.set(theme.logoUrl);
    if (theme.sidebarBg !== undefined) this.sidebarBg.set(theme.sidebarBg);
    if (theme.topbarBg !== undefined) this.topbarBg.set(theme.topbarBg);
    if (theme.sidebarActiveBg !== undefined) this.sidebarActiveBg.set(theme.sidebarActiveBg);
    if (theme.sidebarTextColor !== undefined) this.sidebarTextColor.set(theme.sidebarTextColor);
    if (theme.brandText !== undefined) this.brandText.set(theme.brandText);

    this.applyCssVariables(this.sidebarBg(), this.topbarBg(), this.sidebarActiveBg(), this.sidebarTextColor());
  }

  private applyCssVariables(sidebarColor: string, topbarColor: string, activeColor: string, textColor: string): void {
    if (typeof document !== 'undefined' && document.documentElement) {
      document.documentElement.style.setProperty('--bg-sidebar', sidebarColor);
      document.documentElement.style.setProperty('--bg-header', topbarColor);
      document.documentElement.style.setProperty('--bg-sidebar-active', activeColor);
      document.documentElement.style.setProperty('--text-sidebar', textColor);
    }
  }
}
