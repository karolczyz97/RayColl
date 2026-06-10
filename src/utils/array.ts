/** Keeps only string entries of an unknown value; non-arrays become `[]`. */
export function coerceStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

export function padArray<T>(arr: T[], targetLength: number, fillValue: T): T[] {
  if (arr.length >= targetLength) return arr;
  const padded = arr.slice();
  while (padded.length < targetLength) padded.push(fillValue);
  return padded;
}

export function filterLive<T extends { deletedAt?: number | null }>(items: T[]): T[] {
  // Reference-stable: when nothing is tombstoned, return the input array as-is.
  // Selectors rely on this so unchanged data keeps its identity (React.memo).
  return items.some((item) => item.deletedAt != null)
    ? items.filter((item) => item.deletedAt == null)
    : items;
}

export function swapElements<T>(arr: T[], i: number, j: number): T[] {
  if (i < 0 || i >= arr.length || j < 0 || j >= arr.length) return arr;
  const next = [...arr];
  [next[i], next[j]] = [next[j], next[i]];
  return next;
}
