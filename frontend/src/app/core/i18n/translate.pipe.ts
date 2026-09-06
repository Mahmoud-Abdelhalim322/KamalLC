import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslationService } from './translation.service';

@Pipe({
  name: 'translate',
  pure: false,
  standalone: true
})
export class TranslatePipe implements PipeTransform {
  private readonly translationService = inject(TranslationService);

  transform(key: string | null | undefined, params?: Record<string, any> | any): string {
    if (!key) return '';
    return this.translationService.translate(key, params);
  }
}
