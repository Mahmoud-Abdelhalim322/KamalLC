import { Directive, ElementRef, NgZone, OnDestroy, OnInit, inject } from '@angular/core';

/** The page title the band is anchored to. */
const TITLE_SELECTOR = '.page-title, h1';

/** A wrapper taller than this is page content, not the header around the title. */
const MAX_HEADER_PX = 240;

/** Search boxes and tables belong to the page body, so the band never wraps a block holding one. */
const BODY_SELECTOR = 'table, input[type="text"], input[type="search"], input:not([type])';

/** How far the yellow band runs past the bottom of the page header. */
const OVERLAP_PX = 34;

/** An ink KPI strip right under the header belongs inside the band, as in the design. */
const STRIP_SELECTOR = '.k-kpi-strip, .kpi-row, .summary-cards';
const STRIP_MAX_DISTANCE_PX = 60;
const STRIP_OVERLAP_PX = 22;

/**
 * Sizes the shell's yellow hero band so it ends just below the current page's header,
 * whatever height that header has. Placed on `.shell-main`; writes `--hero-height` on it.
 * Re-measures when the routed content changes (pages render their header after data loads)
 * and on window resize.
 */
@Directive({
  selector: '[appHeroBand]',
  standalone: true
})
export class HeroBandDirective implements OnInit, OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly zone = inject(NgZone);
  private observer?: MutationObserver;
  private frame = 0;
  private readonly onResize = () => this.schedule();

  ngOnInit(): void {
    this.zone.runOutsideAngular(() => {
      const content = this.host.nativeElement.querySelector('.shell-content');
      if (!content) return;
      this.observer = new MutationObserver(() => this.schedule());
      this.observer.observe(content, { childList: true, subtree: true });
      window.addEventListener('resize', this.onResize);
      this.schedule();
    });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
    window.removeEventListener('resize', this.onResize);
    cancelAnimationFrame(this.frame);
  }

  private schedule(): void {
    cancelAnimationFrame(this.frame);
    this.frame = requestAnimationFrame(() => this.measure());
  }

  private measure(): void {
    const main = this.host.nativeElement;
    const content = main.querySelector('.shell-content');
    if (!content) return;

    const title = content.querySelector(TITLE_SELECTOR);
    if (!title) {
      main.style.removeProperty('--hero-height');
      return;
    }
    // Grow from the title to the block around it (subtitle, actions), stopping before
    // a wrapper that also holds the page body.
    let header: Element = title;
    while (header.parentElement && header.parentElement !== content
        && header.parentElement.getBoundingClientRect().height <= MAX_HEADER_PX
        && !header.parentElement.querySelector(BODY_SELECTOR)) {
      header = header.parentElement;
    }

    const top = main.getBoundingClientRect().top;
    let bottom = header.getBoundingClientRect().bottom + OVERLAP_PX;
    const strip = content.querySelector(STRIP_SELECTOR)?.getBoundingClientRect();
    if (strip && strip.top - header.getBoundingClientRect().bottom <= STRIP_MAX_DISTANCE_PX) {
      bottom = strip.bottom + STRIP_OVERLAP_PX;
    }
    const height = Math.round(bottom - top);
    main.style.setProperty('--hero-height', `${Math.max(height, 180)}px`);
  }
}
