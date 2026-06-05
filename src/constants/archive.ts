export const ARCHIVE_RETENTION_DAYS = 14;
export const ARCHIVE_RETENTION_MS = ARCHIVE_RETENTION_DAYS * 24 * 60 * 60 * 1000;

/** How often (in ms) the provider polls for expired archives. */
export const ARCHIVE_PURGE_INTERVAL_MS = 60 * 60 * 1000;
