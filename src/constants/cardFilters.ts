export type CardFilter = 'all' | 'new' | 'review' | 'new+review';

export const CARD_FILTERS = {
  ALL: 'all' as const,
  NEW: 'new' as const,
  REVIEW: 'review' as const,
  NEW_REVIEW: 'new+review' as const,
};

export const CARD_FILTER_OPTIONS: { value: CardFilter; labelKey: string }[] = [
  { value: CARD_FILTERS.NEW_REVIEW, labelKey: 'filter.new_review' },
  { value: CARD_FILTERS.NEW, labelKey: 'filter.new' },
  { value: CARD_FILTERS.REVIEW, labelKey: 'filter.review' },
  { value: CARD_FILTERS.ALL, labelKey: 'filter.all' },
];
