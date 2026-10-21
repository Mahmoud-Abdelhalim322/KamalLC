import { Pipe, PipeTransform } from '@angular/core';

/** Two-letter monogram for avatars: first letters of the first two words of a name. */
@Pipe({
  name: 'initials',
  standalone: true
})
export class InitialsPipe implements PipeTransform {
  transform(name: string | null | undefined): string {
    return (name || '').trim().split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0]).join('');
  }
}
