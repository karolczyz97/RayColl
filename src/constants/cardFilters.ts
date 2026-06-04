export type CardFilter = 'all' | 'new' | 'review' | 'new+review';

export const CARD_FILTERS = {
  ALL: 'all' as const,
  NEW: 'new' as const,
  REVIEW: 'review' as const,
  NEW_REVIEW: 'new+review' as const,
};

/** Canonical default study filter for new/normalized decks. */
export const DEFAULT_STUDY_FILTER: CardFilter = CARD_FILTERS.NEW_REVIEW;

export const CARD_FILTER_OPTIONS: { value: CardFilter; labelKey: string }[] = [
  { value: CARD_FILTERS.NEW_REVIEW, labelKey: 'filter.new_review' },
  { value: CARD_FILTERS.NEW, labelKey: 'filter.new' },
  { value: CARD_FILTERS.REVIEW, labelKey: 'filter.review' },
  { value: CARD_FILTERS.ALL, labelKey: 'filter.all' },
];
