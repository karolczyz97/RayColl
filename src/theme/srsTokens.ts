import { SrsState } from '../types/models';
import { SrsCardCategory } from '../srs/srsEngine';

export interface SrsCategoryToken {
  labelKey: string;
  color: string; // primary color
  bg: string;    // light background
  badgeTextKey: string;
}

export const SRS_CATEGORIES_TOKENS: Record<SrsCardCategory, SrsCategoryToken> = {
  new: {
    labelKey: 'filter.new',
    color: '#1565c0',
    bg: '#d1e4ff',
    badgeTextKey: 'srs.badge.new',
  },
  learning: {
    labelKey: 'filter.learning',
    color: '#b86800',
    bg: '#ffddb3',
    badgeTextKey: 'srs.badge.learning',
  },
  review: {
    labelKey: 'filter.review',
    color: '#6750a4',
    bg: '#e8def8',
    badgeTextKey: 'srs.badge.review',
  },
  mastered: {
    labelKey: 'filter.mastered',
    color: '#006c4c',
    bg: '#c8ffc0',
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
