import type { CardStats } from '../store/selectors/stats';
import type { SrsCardCategory } from '../srs/srsEngine';
import { SRS_CATEGORIES_TOKENS, SRS_CATEGORY_ORDER } from '../theme/srsTokens';

export type BrowseFilter = 'all' | SrsCardCategory;

export const BROWSE_FILTER_ORDER: BrowseFilter[] = ['all', ...SRS_CATEGORY_ORDER];

export const BROWSE_FILTER_STATS_KEY: Record<BrowseFilter, keyof CardStats> = {
  all: 'total',
  new: 'newCount',
  learning: 'learning',
  review: 'review',
  mastered: 'mastered',
};

export function getBrowseFilterLabelKey(filter: BrowseFilter): string {
  return filter === 'all' ? 'filter.all' : SRS_CATEGORIES_TOKENS[filter].labelKey;
}
