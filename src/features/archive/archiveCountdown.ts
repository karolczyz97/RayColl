import { ARCHIVE_RETENTION_MS } from '@/constants/archive';

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Whole days left before an archived deck is auto-purged. Clamped to >= 0; a deck
 * already past its retention window (pending the next purge tick) reports 0.
 */
export function getArchiveDaysRemaining(archivedAt: number, now: number): number {
  const deadline = archivedAt + ARCHIVE_RETENTION_MS;
  const remainingMs = deadline - now;
  if (remainingMs <= 0) {
    return 0;
  }
  return Math.ceil(remainingMs / DAY_MS);
}

/**
 * i18n key for the auto-delete countdown. The `_in` variant expects a `{days}`
 * replacement; today/tomorrow variants are self-contained.
 */
export function getArchiveCountdownLabelKey(daysRemaining: number): string {
  if (daysRemaining <= 0) {
    return 'archive.auto_delete_today';
  }
  if (daysRemaining === 1) {
    return 'archive.auto_delete_tomorrow';
  }
  return 'archive.auto_delete_in';
}
