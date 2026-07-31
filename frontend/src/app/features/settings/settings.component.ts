import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SettingResponse, SettingsByGroup } from '../../core/models/api.models';
import { SettingsService } from '../../core/services/settings.service';
import { errorMessage, isoDate } from '../../core/services/api.util';

/** A setting being edited, held apart so a failed save leaves the row untouched. */
interface EditState {
  key: string;
  value: string;
  effectiveFrom: string;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class SettingsComponent implements OnInit {

  private settingsService = inject(SettingsService);

  /** Section order and titles — anything unmapped falls back to its key. */
  readonly groupLabels: Record<string, string> = {
    SALARY: 'أساس الراتب',
    PENALTY: 'شرائح خصم الساعات',
    SHIFT_INCOME: 'الحافز اليومي للطلبات',
    ALLOWANCE: 'البدلات',
    TARGET: 'الهدف الشهري',
    DOCUMENT_ALERT: 'إنذارات انتهاء الوثائق',
    OTHER: 'إعدادات أخرى',
  };

  readonly groupIcons: Record<string, string> = {
    SALARY: 'payments',
    PENALTY: 'schedule',
    SHIFT_INCOME: 'local_shipping',
    ALLOWANCE: 'local_gas_station',
    TARGET: 'flag',
    DOCUMENT_ALERT: 'notification_important',
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

  ngOnInit(): void {
    this.loadSettings();
  }

  loadSettings(): void {
    this.isLoadingSettings = true;
    this.settingsService.current().subscribe({
      next: (settings) => {
        this.isLoadingSettings = false;
        this.settings = settings;
      },
      error: (err) => {
        this.isLoadingSettings = false;
        this.errorMessage = errorMessage(err, 'فشل في تحميل الإعدادات');
      }
    });
  }

  /** Renders sections in a stable order rather than whatever the map iterated in. */
  get groupKeys(): string[] {
    const known = Object.keys(this.groupLabels).filter(g => this.settings[g]?.length);
    const extra = Object.keys(this.settings).filter(g => !(g in this.groupLabels));
    return [...known, ...extra];
  }

  groupLabel(group: string): string {
    return this.groupLabels[group] ?? group;
  }

  groupIcon(group: string): string {
    return this.groupIcons[group] ?? 'tune';
  }

  startEdit(setting: SettingResponse): void {
    this.clearMessages();
    this.edit = {
      key: setting.settingKey,
      value: setting.value,
      // Defaults to today; an earlier date would rewrite already-priced shifts.
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
        this.successMessage =
          `تم تحديث "${updated.labelAr ?? updated.settingKey}" — يبدأ العمل به من ${effectiveFrom}`;
        this.loadSettings();
        if (this.historyKey === key) this.loadHistory(key);
      },
      error: (err) => {
        this.isSaving = false;
        this.errorMessage = errorMessage(err, 'فشل في حفظ الإعداد');
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
        // The user may have collapsed or switched rows while this was in flight.
        if (this.historyKey === key) this.history = rows;
      },
      error: (err) => {
        this.isLoadingHistory = false;
        this.errorMessage = errorMessage(err, 'فشل في تحميل سجل التغييرات');
      }
    });
  }

  isCurrent(row: SettingResponse): boolean {
    return row.effectiveTo === null;
  }

  /** A value dated in the future is scheduled, not yet in force. */
  isScheduled(row: SettingResponse): boolean {
    return row.effectiveTo === null && row.effectiveFrom > isoDate(new Date());
  }

  unitOf(setting: SettingResponse): string {
    return setting.unitLabel ?? '';
  }

  /** A switch (تفعيل بدل السيارة / البنزين) is picked, not typed. */
  isFlag(setting: SettingResponse): boolean {
    return setting.valueType === 'BOOLEAN';
  }

  flagLabel(value: string): string {
    return value === 'true' ? 'مفعّل' : 'غير مفعّل';
  }

  displayValue(setting: SettingResponse, value: string = setting.value): string {
    return this.isFlag(setting) ? this.flagLabel(value) : value;
  }

  // ---------------------------------------------------------------- shared

  clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }

  formatDate(iso: string | null): string {
    return iso ?? '—';
  }

}
