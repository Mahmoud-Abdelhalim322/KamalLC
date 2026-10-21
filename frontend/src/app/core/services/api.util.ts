import { HttpParams } from '@angular/common/http';
import { ApiResponse } from '../models/api.models';

/**
 * Every endpoint wraps its payload in ApiResponse. A `success: false` body still
 * arrives as HTTP 200, so it has to be turned into an error here — otherwise a
 * failed call would flow into the success branch with `data` undefined.
 */
/** Rows per page on every server-paged list. */
export const PAGE_SIZE = 10;

export function unwrap<T>(response: ApiResponse<T>): T {
  if (!response.success || response.data == null) {
    throw new ApiError(response.message ?? 'Operation failed', response.errorCode);
  }
  return response.data;
}

/** Carries the server's Arabic message so components can display it as-is. */
export class ApiError extends Error {
  constructor(message: string, readonly errorCode?: string) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Reads the displayable message off whatever the HTTP layer threw: an ApiError
 * from `unwrap`, or an HttpErrorResponse carrying the server's error body.
 */
export function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message;
  const body = (error as { error?: { message?: string } })?.error;
  return body?.message || fallback;
}

/**
 * Reads the server's machine-readable error code, so callers can react to a specific
 * failure (and not just show its message). Undefined when the error carries none.
 */
export function errorCode(error: unknown): string | undefined {
  if (error instanceof ApiError) return error.errorCode;
  return (error as { error?: { errorCode?: string } })?.error?.errorCode;
}

/**
 * Drops null/undefined/'' so blank filter fields never reach the query string.
 * Takes `object` rather than a Record so plain interfaces — which have no implicit
 * index signature — can be passed straight in.
 */
export function toHttpParams(source: object): HttpParams {
  return Object.entries(source).reduce((params, [key, value]) => {
    if (value === null || value === undefined || value === '') return params;
    return params.set(key, String(value));
  }, new HttpParams());
}

/**
 * Formats a date as `yyyy-MM-dd` in the browser's own timezone.
 *
 * <p>`toISOString()` converts to UTC first, which east of Greenwich pulls a local
 * midnight back into the previous day — in Riyadh (UTC+3) the 1st of the month came
 * out as the last day of the month before, so a period picked as "this month"
 * silently shifted a day and dropped its final day.
 */
export function isoDate(date: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Triggers a browser download for a blob the API returned. */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}
