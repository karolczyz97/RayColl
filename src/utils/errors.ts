export function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

const OFFLINE_CODES = new Set(['unavailable', 'auth/network-request-failed']);
const OFFLINE_PATTERNS = [
  /offline/i,
  /network/i,
  /unavailable/i,
  /failed to fetch/i,
  /^Cloud load timed out/i,
];

/**
 * Returns true when an error represents a network/offline condition.
 * Accepts raw Error objects, Firebase-like objects with a `code` property,
 * or already-stringified messages stored in store state.
 * A local-persistence timeout ("Local persistence timed out...") is NOT
 * treated as offline — the anchor on "Cloud load" ensures only cloud
 * timeouts qualify.
 */
export function isOfflineError(error: unknown): boolean {
  if (error == null || error === '') return false;

  if (typeof error === 'object') {
    const code = (error as Record<string, unknown>).code;
    if (typeof code === 'string') {
      if (OFFLINE_CODES.has(code)) return true;
      if (code.endsWith('/unavailable')) return true;
    }
  }

  const message = getErrorMessage(error);
  return OFFLINE_PATTERNS.some((re) => re.test(message));
}
