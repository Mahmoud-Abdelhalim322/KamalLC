import { Pipe, PipeTransform } from '@angular/core';
import { formatNumber } from '@angular/common';

/** U+20C1 SAUDI RIYAL SIGN — drawn by the bundled 'Saudi Riyal' font (see styles.scss). */
export const RIYAL_SIGN = '⃁';

/**
 * Formats an amount with the Saudi riyal sign the way SAMA's usage rules ask: the sign to the left
 * of the number with a space between. The result is wrapped in a left-to-right isolate so the sign
 * stays on the left inside Arabic text too, and the space is non-breaking so the two never wrap apart.
 *
 *   {{ 417 | sar }}            → ⃁ 417.00
 *   {{ 417 | sar:'1.0-0' }}    → ⃁ 417
 *   {{ 50 | sar:'1.2-2':'−' }} → ⃁ −50.00
 */
@Pipe({
  name: 'sar',
  standalone: true
})
export class SarPipe implements PipeTransform {
  transform(value: number | null | undefined, digitsInfo = '1.2-2', sign = ''): string {
    if (value == null) return '—';
    return `⁦${RIYAL_SIGN} ${sign}${formatNumber(value, 'en-US', digitsInfo)}⁩`;
  }
}
