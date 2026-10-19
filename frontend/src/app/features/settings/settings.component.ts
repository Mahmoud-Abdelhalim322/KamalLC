import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SettingResponse, SettingsByGroup } from '../../core/models/api.models';
import { SettingsService } from '../../core/services/settings.service';
import { ThemeService, DEFAULT_THEME, ThemeSettings } from '../../core/services/theme.service';
import { errorMessage, isoDate } from '../../core/services/api.util';
import { TranslationService, TranslatePipe } from '../../core/i18n';

/** A setting being edited, held apart so a failed save leaves the row untouched. */
interface EditState {
  key: string;
  value: string;
  effectiveFrom: string;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class SettingsComponent implements OnInit {

  private settingsService = inject(SettingsService);
  public themeService = inject(ThemeService);
  public translationService = inject(TranslationService);

  activeTab: 'OPERATIONAL' | 'APPEARANCE' = 'OPERATIONAL';

  getGroupLabel(groupKey: string): string {
    return this.translationService.t('SETTINGS.GROUP_' + groupKey) || groupKey;
  }

  readonly groupIcons: Record<string, string> = {
    SALARY: 'payments',
    PENALTY: 'schedule',
    SHIFT_INCOME: 'local_shipping',
    ALLOWANCE: 'local_gas_station',
    TARGET: 'flag',
    DOCUMENT_ALERT: 'notification_important',
    FLEET: 'directions_car',
    OTHER: 'tune',
  };

  settings: SettingsByGroup = {};
  isLoadingSettings = true;

  edit: EditState | null = null;
  isSaving = false;

  historyKey: string | null = null;
  history: SettingResponse[] = [];
  isLoadingHistory = false;

  errorMessage = '';
  successMessage = '';

  // ---------------------------------------------------------------- Appearance State
  appearanceForm: ThemeSettings = { ...DEFAULT_THEME };
  selectedLogoFile: File | null = null;
  logoPreviewUrl: string | null = null;
  isUploadingLogo = false;
  isSavingAppearance = false;

  ngOnInit(): void {
    this.loadSettings();
    this.initAppearanceForm();
  }

  initAppearanceForm(): void {
    this.appearanceForm = {
      logoUrl: this.themeService.logoUrl(),
      sidebarBg: this.themeService.sidebarBg(),
      topbarBg: this.themeService.topbarBg(),
      sidebarActiveBg: this.themeService.sidebarActiveBg(),
      sidebarTextColor: this.themeService.sidebarTextColor(),
      brandText: this.themeService.brandText(),
    };
  }

  setTab(tab: 'OPERATIONAL' | 'APPEARANCE'): void {
    this.activeTab = tab;
    this.clearMessages();
    if (tab === 'APPEARANCE') {
      this.initAppearanceForm();
    }
  }

  loadSettings(): void {
    this.isLoadingSettings = true;
    this.settingsService.current().subscribe({
      next: (settings) => {
        this.isLoadingSettings = false;
        this.settings = settings;
        this.themeService.extractAndApply(settings);
        this.initAppearanceForm();
      },
      error: (err) => {
        this.isLoadingSettings = false;
        this.errorMessage = errorMessage(err, this.translationService.t('SETTINGS.ERR_LOAD_SETTINGS'));
      }
    });
  }

  /** Renders operational sections in a stable order excluding APPEARANCE. */
  get groupKeys(): string[] {
    const knownOrder = ['SALARY', 'PENALTY', 'SHIFT_INCOME', 'ALLOWANCE', 'TARGET', 'DOCUMENT_ALERT', 'FLEET', 'OTHER'];
    const known = knownOrder.filter(g => this.settings[g]?.length);
    const extra = Object.keys(this.settings).filter(g => !knownOrder.includes(g) && g !== 'APPEARANCE');
    return [...known, ...extra];
  }

  groupLabel(group: string): string {
    return this.getGroupLabel(group);
  }

  groupIcon(group: string): string {
    return this.groupIcons[group] ?? 'tune';
  }

  getSettingLabel(setting: SettingResponse): string {
    const key = 'SETTINGS.KEY_' + setting.settingKey;
    const translated = this.translationService.t(key);
    if (translated && translated !== key) {
      return translated;
    }
    return setting.labelAr || setting.settingKey;
  }

  startEdit(setting: SettingResponse): void {
    this.clearMessages();
    this.edit = {
      key: setting.settingKey,
      value: setting.value,
      effectiveFrom: isoDate(new Date()),
    };
  }

  cancelEdit(): void {
    this.edit = null;
  }

  isEditing(setting: SettingResponse): boolean {
    return this.edit?.key === setting.settingKey;
  }

  save(): void {
    if (!this.edit || !this.edit.value.trim()) return;
    const { key, value, effectiveFrom } = this.edit;

    this.isSaving = true;
    this.clearMessages();

    this.settingsService.update(key, value.trim(), effectiveFrom).subscribe({
      next: (updated) => {
        this.isSaving = false;
        this.edit = null;
        this.successMessage = this.translationService.t('SETTINGS.SUCCESS_UPDATE_SETTING', {
          label: this.getSettingLabel(updated),
          date: effectiveFrom
        });
        this.loadSettings();
        if (this.historyKey === key) this.loadHistory(key);
      },
      error: (err) => {
        this.isSaving = false;
        this.errorMessage = errorMessage(err, this.translationService.t('SETTINGS.ERR_SAVE_SETTING'));
      }
    });
  }

  toggleHistory(setting: SettingResponse): void {
    if (this.historyKey === setting.settingKey) {
      this.historyKey = null;
      this.history = [];
      return;
    }
    this.historyKey = setting.settingKey;
    this.loadHistory(setting.settingKey);
  }

  private loadHistory(key: string): void {
    this.isLoadingHistory = true;
    this.settingsService.history(key).subscribe({
      next: (rows) => {
        this.isLoadingHistory = false;
        if (this.historyKey === key) this.history = rows;
      },
      error: (err) => {
        this.isLoadingHistory = false;
        this.errorMessage = errorMessage(err, this.translationService.t('SETTINGS.ERR_LOAD_HISTORY'));
      }
    });
  }

  // ---------------------------------------------------------------- Appearance Handlers

  onLogoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      this.selectedLogoFile = file;
      const reader = new FileReader();
      reader.onload = (e) => {
        this.logoPreviewUrl = e.target?.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  uploadLogo(): void {
    if (!this.selectedLogoFile) return;
    this.isUploadingLogo = true;
    this.clearMessages();

    this.settingsService.uploadLogo(this.selectedLogoFile).subscribe({
      next: (res) => {
        this.isUploadingLogo = false;
        this.selectedLogoFile = null;
        this.logoPreviewUrl = null;
        this.appearanceForm.logoUrl = res.value;
        this.themeService.updateTheme({ logoUrl: res.value });
        this.successMessage = this.translationService.t('SETTINGS.SUCCESS_UPLOAD_LOGO');
        this.loadSettings();
      },
      error: (err) => {
        this.isUploadingLogo = false;
        this.errorMessage = errorMessage(err, this.translationService.t('SETTINGS.ERR_UPLOAD_LOGO'));
      }
    });
  }

  saveAppearance(): void {
    this.isSavingAppearance = true;
    this.clearMessages();
    const today = isoDate(new Date());

    // Update colors and brand text
    const updates = [
      this.settingsService.update('THEME_SIDEBAR_BG', this.appearanceForm.sidebarBg, today),
      this.settingsService.update('THEME_TOPBAR_BG', this.appearanceForm.topbarBg, today),
      this.settingsService.update('THEME_SIDEBAR_ACTIVE_BG', this.appearanceForm.sidebarActiveBg, today),
      this.settingsService.update('THEME_SIDEBAR_TEXT_COLOR', this.appearanceForm.sidebarTextColor, today),
      this.settingsService.update('THEME_BRAND_TEXT', this.appearanceForm.brandText, today),
    ];

    if (this.appearanceForm.logoUrl && this.appearanceForm.logoUrl !== this.themeService.logoUrl()) {
      updates.push(this.settingsService.update('THEME_LOGO', this.appearanceForm.logoUrl, today));
    }

    let completed = 0;
    let hasError = false;

    updates.forEach(obs => {
      obs.subscribe({
        next: () => {
          completed++;
          if (completed === updates.length && !hasError) {
            this.isSavingAppearance = false;
            this.themeService.updateTheme(this.appearanceForm);
            this.successMessage = this.translationService.t('SETTINGS.SUCCESS_SAVE_APPEARANCE');
            this.loadSettings();
          }
        },
        error: (err) => {
          if (!hasError) {
            hasError = true;
            this.isSavingAppearance = false;
            this.errorMessage = errorMessage(err, this.translationService.t('SETTINGS.ERR_SAVE_APPEARANCE'));
          }
        }
      });
    });
  }

  resetAppearanceToDefault(): void {
    this.clearMessages();
    this.appearanceForm = { ...DEFAULT_THEME };
    this.selectedLogoFile = null;
    this.logoPreviewUrl = null;
    this.saveAppearance();
  }

  onLiveColorChange(): void {
    this.themeService.updateTheme({
      sidebarBg: this.appearanceForm.sidebarBg,
      topbarBg: this.appearanceForm.topbarBg,
      sidebarActiveBg: this.appearanceForm.sidebarActiveBg,
      sidebarTextColor: this.appearanceForm.sidebarTextColor,
      brandText: this.appearanceForm.brandText
    });
  }

  // ---------------------------------------------------------------- Helpers

  isCurrent(row: SettingResponse): boolean {
    return row.effectiveTo === null;
  }

  isScheduled(row: SettingResponse): boolean {
    return row.effectiveTo === null && row.effectiveFrom > isoDate(new Date());
  }

  unitOf(setting: SettingResponse): string {
    const raw = setting.unitLabel?.trim();
    if (!raw) return '';
    if (raw === 'ريال' || raw === 'ر.س') {
      return this.translationService.t('SETTINGS.UNIT_SAR');
    }
    if (raw === 'ساعة') {
      return this.translationService.t('SETTINGS.UNIT_HOURS');
    }
    if (raw === 'يوم') {
      return this.translationService.t('SETTINGS.UNIT_DAYS');
    }
    if (raw === 'طلب' || raw === 'أوردر') {
      return this.translationService.t('SETTINGS.UNIT_ORDERS');
    }
    return raw;
  }

  isFlag(setting: SettingResponse): boolean {
    return setting.valueType === 'BOOLEAN';
  }

  flagLabel(value: string): string {
    return value === 'true' ? this.translationService.t('SETTINGS.ENABLED') : this.translationService.t('SETTINGS.DISABLED');
  }

  displayValue(setting: SettingResponse, value: string = setting.value): string {
    return this.isFlag(setting) ? this.flagLabel(value) : value;
  }

  clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }

  formatDate(iso: string | null): string {
    return iso ?? '—';
  }
}
