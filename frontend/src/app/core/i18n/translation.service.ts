import { Injectable, signal, computed, effect } from '@angular/core';
import { AR_TRANSLATIONS } from './translations/ar';
import { EN_TRANSLATIONS } from './translations/en';

export type Language = 'ar' | 'en';
export type Direction = 'rtl' | 'ltr';

export interface LanguageOption {
  code: Language;
  label: string;
  nativeName: string;
  flag: string;
  dir: Direction;
}

export const SUPPORTED_LANGUAGES: LanguageOption[] = [
  {
    code: 'ar',
    label: 'Arabic',
    nativeName: 'العربية',
    flag: '🇸🇦',
    dir: 'rtl'
  },
  {
    code: 'en',
    label: 'English',
    nativeName: 'English',
    flag: '🇬🇧',
    dir: 'ltr'
  }
];

@Injectable({
  providedIn: 'root'
})
export class TranslationService {
  private readonly STORAGE_KEY = 'kamalres_lang';
  private readonly DEFAULT_LANG: Language = 'ar';

  private readonly translationsMap: Record<Language, any> = {
    ar: AR_TRANSLATIONS,
    en: EN_TRANSLATIONS
  };

  /** Reactive signal holding current selected language */
  readonly currentLang = signal<Language>(this.getInitialLanguage());

  /** Reactive computed direction ('rtl' | 'ltr') */
  readonly dir = computed<Direction>(() => this.currentLang() === 'ar' ? 'rtl' : 'ltr');

  /** Reactive boolean check for RTL */
  readonly isRtl = computed<boolean>(() => this.currentLang() === 'ar');

  /** Available language options */
  readonly availableLanguages = SUPPORTED_LANGUAGES;

  /** Current active language metadata */
  readonly currentLanguageOption = computed<LanguageOption>(() => {
    return this.availableLanguages.find(l => l.code === this.currentLang()) || this.availableLanguages[0];
  });

  constructor() {
    // Reactively sync HTML attributes and body classes whenever language changes
    effect(() => {
      const lang = this.currentLang();
      const direction = this.dir();
      this.applyDocumentAttributes(lang, direction);
    });
  }

  private getInitialLanguage(): Language {
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        const saved = localStorage.getItem(this.STORAGE_KEY) as Language;
        if (saved === 'ar' || saved === 'en') {
          return saved;
        }
      }
    } catch (e) {
      console.warn('Could not read language from localStorage', e);
    }
    return this.DEFAULT_LANG;
  }

  private applyDocumentAttributes(lang: Language, dir: Direction): void {
    if (typeof document === 'undefined') return;

    const html = document.documentElement;
    html.setAttribute('lang', lang);
    html.setAttribute('dir', dir);

    if (dir === 'ltr') {
      document.body.classList.add('ltr-layout');
      document.body.classList.remove('rtl-layout');
    } else {
      document.body.classList.add('rtl-layout');
      document.body.classList.remove('ltr-layout');
    }
  }

  /** Change language and persist in localStorage */
  setLanguage(lang: Language): void {
    if (this.currentLang() === lang) return;

    this.currentLang.set(lang);
    try {
      if (typeof window !== 'undefined' && window.localStorage) {
        localStorage.setItem(this.STORAGE_KEY, lang);
      }
    } catch (e) {
      console.warn('Could not persist language to localStorage', e);
    }
  }

  /** Toggle between Arabic and English */
  toggleLanguage(): void {
    const next = this.currentLang() === 'ar' ? 'en' : 'ar';
    this.setLanguage(next);
  }

  /**
   * Translate a nested key string, e.g. 'NAV.DASHBOARD' or 'COMMON.SAVE'
   * Supports parameter interpolation: 'Hello {{name}}' -> { name: 'Ali' }
   */
  translate(key: string, params?: Record<string, any>): string {
    if (!key) return '';

    const lang = this.currentLang();
    let text: string | undefined = this.resolveKey(this.translationsMap[lang], key);

    // Fallback to Arabic if missing in English, or English if missing in Arabic
    if (text === undefined) {
      const fallbackLang: Language = lang === 'ar' ? 'en' : 'ar';
      text = this.resolveKey(this.translationsMap[fallbackLang], key);
    }

    // If still not found, return key
    if (typeof text !== 'string') {
      return key;
    }

    // Param interpolation
    if (params) {
      let result = text;
      Object.keys(params).forEach(paramKey => {
        const val = params[paramKey] ?? '';
        result = result.replace(new RegExp(`{{\\s*${paramKey}\\s*}}`, 'g'), String(val));
        result = result.replace(new RegExp(`{\\s*${paramKey}\\s*}`, 'g'), String(val));
      });
      return result;
    }

    return text;
  }

  /** Instant alias for translate */
  instant(key: string, params?: Record<string, any>): string {
    return this.translate(key, params);
  }

  private resolveKey(obj: any, key: string): string | undefined {
    if (!obj || typeof obj !== 'object') return undefined;

    const parts = key.split('.');
    let current = obj;

    for (const part of parts) {
      if (current === undefined || current === null) return undefined;
      current = current[part];
    }

    return typeof current === 'string' ? current : undefined;
  }
}
