import type { FlashcardGroup } from '@/types/models';
import { TOKENS } from '@/theme/tokens';
import { clamp } from '@/utils/math';

export const PEEK_HOLD_THRESHOLD_MS = 500;

// Character-count tiers at which the study card headline steps down a size.
// Heuristic (not a visual token): keeps short answers large and bold while
// long ones shrink slightly before the card falls back to scrolling.
const CARD_FONT_TIER_FULL_MAX = 70;
const CARD_FONT_TIER_LONG_MAX = 140;
const CARD_FONT_TIER_LONGER_MAX = 260;

/**
 * Pick an adaptive headline size for a card page based on its text length.
 * Shrinks gently through token tiers; the card scrolls once it hits the floor.
 */
export function getAdaptiveCardFontSize(text: string): number {
  const length = text?.length ?? 0;
  const sizes = TOKENS.typography.studyCardSize;
  if (length <= CARD_FONT_TIER_FULL_MAX) return sizes.full;
  if (length <= CARD_FONT_TIER_LONG_MAX) return sizes.long;
  if (length <= CARD_FONT_TIER_LONGER_MAX) return sizes.longer;
  return sizes.min;
}

/**
 * The page that just became active (the highest newly revealed index), or null
 * when nothing new was revealed. Used to drive the card's auto-scroll.
 */
export function getActiveRevealedPage(
  prevRevealed: number[],
  nextRevealed: number[],
): number | null {
  const prevSet = new Set(prevRevealed);
  const added = nextRevealed.filter((pageIndex) => !prevSet.has(pageIndex));
  if (added.length === 0) return null;
  return Math.max(...added);
}

/**
 * Scroll offset that docks the active page to the bottom of the viewport (newest
 * content sticks to the bottom, older pages scroll up). Short pages bottom-align;
 * a page taller than the viewport top-aligns instead so it reads from the start.
 */
export function getBottomAlignedScrollY(
  pageTop: number,
  pageHeight: number,
  viewportHeight: number,
): number {
  const bottomAligned = pageTop + pageHeight - viewportHeight;
  return clamp(bottomAligned, 0, pageTop);
}

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
