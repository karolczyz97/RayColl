/**
 * Narrowing guard for plain object records. Rejects `null` and arrays.
 * Single source of truth shared by persistence schema parsing and backup
 * validation.
 */
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
