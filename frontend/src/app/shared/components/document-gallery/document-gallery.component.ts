import { Component, EventEmitter, HostListener, Input, OnDestroy, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ApiResponse, DocumentInfo } from '../../../core/models/api.models';

/** Arabic labels for the document_type values defined in V3__create_captain_tables.sql. */
const DOCUMENT_TYPE_LABELS: Record<string, string> = {
  PERSONAL_PHOTO: 'صورة شخصية',
  ID_CARD: 'بطاقة هوية',
  IQAMA: 'إقامة',
  PASSPORT: 'جواز سفر',
  DRIVING_LICENCE: 'رخصة قيادة',
  VEHICLE_REGISTRATION: 'استمارة مركبة',
  CONTRACT: 'عقد عمل',
  OTHER: 'أخرى'
};

/**
 * Shows a captain's uploaded documents with image thumbnails and an in-page viewer
 * for images and PDFs.
 *
 * The JWT is attached by the HttpClient interceptor only, so a plain <img src> or
 * <iframe src> pointing at the API would load unauthenticated. Every file is fetched
 * as a blob instead and rendered from an object URL, which is cached per document
 * and released on destroy.
 */
@Component({
  selector: 'app-document-gallery',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './document-gallery.component.html',
  styleUrls: ['./document-gallery.component.scss']
})
export class DocumentGalleryComponent implements OnDestroy {
  @Input() set documents(value: DocumentInfo[]) {
    this.docs = value ?? [];
    this.loadThumbnails();
  }
  /** Delete buttons appear only where the page can actually remove files (the edit form). */
  @Input() allowDelete = false;
  /** API prefix for endpoints e.g. 'captains' or 'staff' (defaults to 'captains') */
  @Input() apiPrefix = 'captains';

  @Output() deleted = new EventEmitter<DocumentInfo>();

  docs: DocumentInfo[] = [];
  errorMessage = '';

  /** Object URLs by document id, for both thumbnails and the viewer. */
  private objectUrls = new Map<string, string>();
  private loadingIds = new Set<string>();

  // Viewer state
  activeDoc: DocumentInfo | null = null;
  activeUrl: SafeResourceUrl | null = null;
  activeRawUrl = '';
  isViewerLoading = false;
  viewerError = '';

  constructor(private http: HttpClient, private sanitizer: DomSanitizer) {}

  ngOnDestroy(): void {
    this.objectUrls.forEach(url => URL.revokeObjectURL(url));
    this.objectUrls.clear();
  }

  // ---------- Thumbnails ----------

  private loadThumbnails(): void {
    this.docs
      .filter(doc => this.isImage(doc) && !this.objectUrls.has(doc.id) && !this.loadingIds.has(doc.id))
      .forEach(doc => this.fetchBlob(doc).then(() => {}, () => {}));
  }

  thumbnailUrl(doc: DocumentInfo): string | null {
    return this.objectUrls.get(doc.id) ?? null;
  }

  // ---------- Viewer ----------

  openViewer(doc: DocumentInfo): void {
    if (!this.canPreview(doc)) {
      // Nothing renders inline for other types — hand the file over instead.
      this.download(doc);
      return;
    }

    this.activeDoc = doc;
    this.activeUrl = null;
    this.activeRawUrl = '';
    this.viewerError = '';
    this.isViewerLoading = true;

    this.fetchBlob(doc).then(
      (url) => {
        // A second file may have been opened while this one was loading.
        if (this.activeDoc?.id !== doc.id) { return; }
        this.isViewerLoading = false;
        this.activeRawUrl = url;
        this.activeUrl = this.sanitizer.bypassSecurityTrustResourceUrl(url);
      },
      () => {
        if (this.activeDoc?.id !== doc.id) { return; }
        this.isViewerLoading = false;
        this.viewerError = 'فشل عرض المستند';
      }
    );
  }

  closeViewer(): void {
    this.activeDoc = null;
    this.activeUrl = null;
    this.activeRawUrl = '';
    this.viewerError = '';
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.activeDoc) {
      this.closeViewer();
    }
  }

  /** Steps to the next/previous previewable document without leaving the viewer. */
  step(offset: number): void {
    if (!this.activeDoc) { return; }
    const previewable = this.docs.filter(d => this.canPreview(d));
    if (previewable.length < 2) { return; }

    const current = previewable.findIndex(d => d.id === this.activeDoc!.id);
    const next = (current + offset + previewable.length) % previewable.length;
    this.openViewer(previewable[next]);
  }

  get hasMultiplePreviewable(): boolean {
    return this.docs.filter(d => this.canPreview(d)).length > 1;
  }

  // ---------- Actions ----------

  download(doc: DocumentInfo): void {
    this.errorMessage = '';
    this.fetchBlob(doc).then(
      (url) => {
        const link = document.createElement('a');
        link.href = url;
        link.download = doc.fileName;
        link.click();
      },
      () => { this.errorMessage = 'فشل تحميل المستند'; }
    );
  }

  remove(doc: DocumentInfo): void {
    if (!confirm(`هل تريد حذف المستند "${doc.fileName}"؟`)) {
      return;
    }

    this.errorMessage = '';
    this.http.delete<ApiResponse<void>>(`/api/${this.apiPrefix}/documents/${doc.id}`)
      .subscribe({
        next: () => {
          this.releaseUrl(doc.id);
          if (this.activeDoc?.id === doc.id) { this.closeViewer(); }
          this.docs = this.docs.filter(d => d.id !== doc.id);
          this.deleted.emit(doc);
        },
        error: (err) => {
          this.errorMessage = err?.error?.message || 'فشل حذف المستند';
        }
      });
  }

  // ---------- Helpers ----------

  /** Fetches (once) and caches the document as an object URL. */
  private fetchBlob(doc: DocumentInfo): Promise<string> {
    const cached = this.objectUrls.get(doc.id);
    if (cached) {
      return Promise.resolve(cached);
    }

    this.loadingIds.add(doc.id);
    return new Promise<string>((resolve, reject) => {
      this.http.get(`/api/${this.apiPrefix}/documents/${doc.id}/download`, { responseType: 'blob' })
        .subscribe({
          next: (blob) => {
            this.loadingIds.delete(doc.id);
            const url = URL.createObjectURL(blob);
            this.objectUrls.set(doc.id, url);
            resolve(url);
          },
          error: (err) => {
            this.loadingIds.delete(doc.id);
            reject(err);
          }
        });
    });
  }

  private releaseUrl(docId: string): void {
    const url = this.objectUrls.get(docId);
    if (url) {
      URL.revokeObjectURL(url);
      this.objectUrls.delete(docId);
    }
  }

  isImage(doc: DocumentInfo): boolean {
    return !!doc.contentType?.startsWith('image/');
  }

  isPdf(doc: DocumentInfo): boolean {
    return doc.contentType === 'application/pdf';
  }

  canPreview(doc: DocumentInfo): boolean {
    return this.isImage(doc) || this.isPdf(doc);
  }

  getDocTypeLabel(type: string): string {
    return DOCUMENT_TYPE_LABELS[type] || type;
  }

  getDocIcon(doc: DocumentInfo): string {
    if (this.isImage(doc)) { return 'image'; }
    if (this.isPdf(doc)) { return 'picture_as_pdf'; }
    return 'description';
  }

  formatSize(bytes: number | null): string {
    if (!bytes) { return '—'; }
    if (bytes < 1024) { return `${bytes} بايت`; }
    if (bytes < 1024 * 1024) { return `${(bytes / 1024).toFixed(1)} كيلوبايت`; }
    return `${(bytes / (1024 * 1024)).toFixed(1)} ميجابايت`;
  }
}
