import { SrsState } from '../types/models';
import { SrsCardCategory } from '../srs/srsEngine';

export interface SrsCategoryToken {
  labelKey: string;
  badgeTextKey: string;
}

export const SRS_CATEGORIES_TOKENS: Record<SrsCardCategory, SrsCategoryToken> = {
  new: {
    labelKey: 'filter.new',
    badgeTextKey: 'srs.badge.new',
  },
  learning: {
    labelKey: 'filter.learning',
    badgeTextKey: 'srs.badge.learning',
  },
  review: {
    labelKey: 'filter.review',
    badgeTextKey: 'srs.badge.review',
  },
  mastered: {
    labelKey: 'filter.mastered',
    badgeTextKey: 'srs.badge.mastered',
  },
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
