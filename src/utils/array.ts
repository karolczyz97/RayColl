export function padArray<T>(arr: T[], targetLength: number, fillValue: T): T[] {
  if (arr.length >= targetLength) return arr;
  const padded = arr.slice();
  while (padded.length < targetLength) padded.push(fillValue);
  return padded;
}

export function filterLive<T extends { deletedAt?: number | null }>(items: T[]): T[] {
  return items.filter((item) => item.deletedAt == null);
}

export function swapElements<T>(arr: T[], i: number, j: number): T[] {
  if (i < 0 || i >= arr.length || j < 0 || j >= arr.length) return arr;
  const next = [...arr];
  [next[i], next[j]] = [next[j], next[i]];
  return next;
}
