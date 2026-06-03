import { SrsState } from '../types/models';
import { SrsCardCategory } from '../srs/srsEngine';
import type { CardStats } from '../store/selectors/stats';

export interface SrsCategoryToken {
  labelKey: string;
}

export const SRS_CATEGORIES_TOKENS: Record<SrsCardCategory, SrsCategoryToken> = {
  new: {
    labelKey: 'srs.badge.new',
  },
  learning: {
    labelKey: 'srs.badge.learning',
  },
  review: {
    labelKey: 'srs.badge.review',
  },
  mastered: {
    labelKey: 'srs.badge.mastered',
  },
};

/**
 * Canonical display order for SRS card categories.
 * Change this ONE array to reorder:
 *   - SegmentedProgressBar segments and legend
 *   - Browse filters
 *   - Any other UI that iterates over categories
 */
export const SRS_CATEGORY_ORDER: SrsCardCategory[] = [
  'new',
  'learning',
  'review',
  'mastered',
];

/**
 * Canonical mapping from SRS card category to its key in `CardStats`.
 * Single source of truth for SegmentedProgressBar, Browse filters, etc.
 */
export const CATEGORY_TO_STATS_KEY: Record<SrsCardCategory, keyof CardStats> = {
  new: 'newCount',
  learning: 'learning',
  review: 'review',
  mastered: 'mastered',
};

export function getMasteryPercent(state: SrsState): number {
  if (state.state === 0) return 0;
  return Math.min(100, Math.round(Math.min(state.stability / 30, 1) * 100));
}
export function getDaysUntilReview(state: SrsState, now = Date.now()): number {
  if (state.state === 0) return 0;
  const diff = state.nextReviewTimestamp - now;
  if (diff <= 0) return 0;
  return Math.ceil(diff / (1000 * 60 * 60 * 24));
}
