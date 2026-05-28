import type { FlashcardGroup } from '../../../types/models';

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function uniquePageIndexes(pageIndexes: number[]): number[] {
  return [...new Set(pageIndexes)].sort((a, b) => a - b);
}

export function getActivePageIndexes(group: FlashcardGroup): number[] {
  return group.pageNames.slice(0, group.activePageCount).map((_, index) => index);
}

export function getNextHiddenPageIndex(
  group: FlashcardGroup,
  revealedPages: number[],
): number | null {
  const revealed = new Set([0, ...revealedPages]);
  return getActivePageIndexes(group).find((pageIndex) => !revealed.has(pageIndex)) ?? null;
}

export function areAllActivePagesRevealed(
  group: FlashcardGroup,
  revealedPages: number[],
): boolean {
  const revealed = new Set([0, ...revealedPages]);
  return getActivePageIndexes(group).every((pageIndex) => revealed.has(pageIndex));
}
