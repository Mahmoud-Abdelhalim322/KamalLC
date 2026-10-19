import { Component, Input, ElementRef, HostListener, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslationService, Language, LanguageOption } from '../../../core/i18n';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

@Component({
  selector: 'app-language-switcher',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './language-switcher.component.html',
  styleUrls: ['./language-switcher.component.scss']
})
export class LanguageSwitcherComponent {
  readonly translationService = inject(TranslationService);
  private readonly elementRef = inject(ElementRef);

  /** Display mode: 'dropdown' for rich menu, 'compact' for quick pill toggle */
  @Input() mode: 'dropdown' | 'compact' | 'toggle' = 'dropdown';

  /** Dark or Light theme variant */
  @Input() variant: 'default' | 'glass' | 'header' = 'header';

  isOpen = false;

  get currentOption(): LanguageOption {
    return this.translationService.currentLanguageOption();
  }

  get languages(): LanguageOption[] {
    return this.translationService.availableLanguages;
  }

  toggleDropdown(event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.isOpen = !this.isOpen;
  }

  selectLanguage(lang: Language, event?: Event): void {
    if (event) {
      event.stopPropagation();
    }
    this.translationService.setLanguage(lang);
    this.isOpen = false;
  }

  toggleQuick(): void {
    this.translationService.toggleLanguage();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.isOpen = false;
    }
  }
}
