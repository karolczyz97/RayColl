import { ARCHIVE_RETENTION_MS } from '@/constants/archive';
import {
  getArchiveCountdownLabelKey,
  getArchiveDaysRemaining,
} from '@/features/archive/archiveCountdown';

const DAY_MS = 24 * 60 * 60 * 1000;

describe('getArchiveDaysRemaining', () => {
  it('returns the full retention window for a deck archived just now', () => {
    const now = 1_000_000_000_000;
    expect(getArchiveDaysRemaining(now, now)).toBe(ARCHIVE_RETENTION_MS / DAY_MS);
  });

  it('rounds a partial day up', () => {
    const now = 1_000_000_000_000;
    // Archived so that 2 days minus a sliver remain.
    const archivedAt = now - (ARCHIVE_RETENTION_MS - (2 * DAY_MS - 1));
    expect(getArchiveDaysRemaining(archivedAt, now)).toBe(2);
  });

  it('returns 1 on the final day', () => {
    const now = 1_000_000_000_000;
    const archivedAt = now - (ARCHIVE_RETENTION_MS - DAY_MS);
    expect(getArchiveDaysRemaining(archivedAt, now)).toBe(1);
  });

  it('clamps to 0 once the retention window has elapsed', () => {
    const now = 1_000_000_000_000;
    const archivedAt = now - ARCHIVE_RETENTION_MS;
    expect(getArchiveDaysRemaining(archivedAt, now)).toBe(0);
  });

  it('clamps to 0 for a deck already past expiry', () => {
    const now = 1_000_000_000_000;
    const archivedAt = now - ARCHIVE_RETENTION_MS - 5 * DAY_MS;
    expect(getArchiveDaysRemaining(archivedAt, now)).toBe(0);
  });
});

describe('getArchiveCountdownLabelKey', () => {
  it('maps 0 (or expired) to today', () => {
    expect(getArchiveCountdownLabelKey(0)).toBe('archive.auto_delete_today');
    expect(getArchiveCountdownLabelKey(-3)).toBe('archive.auto_delete_today');
  });

  it('maps 1 to tomorrow', () => {
    expect(getArchiveCountdownLabelKey(1)).toBe('archive.auto_delete_tomorrow');
  });

  it('maps multiple days to the {days} variant', () => {
    expect(getArchiveCountdownLabelKey(5)).toBe('archive.auto_delete_in');
  });
});
