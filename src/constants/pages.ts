export const MIN_PAGE_COUNT = 2;
export const MAX_VISIBLE_PAGE_COUNT = 5;
export const MAX_STORED_PAGE_COUNT = 20;

export function clampActivePageCount(candidate: number, upperBound: number): number {
  return Math.max(MIN_PAGE_COUNT, Math.min(upperBound, Math.floor(candidate)));
}
