export type CardFilter = 'all' | 'new' | 'review' | 'new+review';

export const CARD_FILTERS = {
  ALL: 'all' as const,
  NEW: 'new' as const,
  REVIEW: 'review' as const,
  NEW_REVIEW: 'new+review' as const,
};
