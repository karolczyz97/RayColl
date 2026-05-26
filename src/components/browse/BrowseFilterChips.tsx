import React from 'react';
import { StyleSheet } from 'react-native';
import { SegmentedButtons, useTheme } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import {
  BROWSE_FILTER_ORDER,
  BROWSE_FILTER_STATS_KEY,
  getBrowseFilterLabelKey,
  type BrowseFilter,
} from '../../constants/browseFilters';

interface Props {
  browseFilter: BrowseFilter;
  setBrowseFilter: (filter: BrowseFilter) => void;
  stats: { total: number; learning: number; review: number; newCount: number; mastered: number };
  t: TranslationFn;
}

export function BrowseFilterChips({ browseFilter, setBrowseFilter, stats, t }: Props) {
  const theme = useTheme();

  const buttons = BROWSE_FILTER_ORDER.map((filter) => {
    const count = stats[BROWSE_FILTER_STATS_KEY[filter]] as number;
    const isSelected = browseFilter === filter;

    return {
      value: filter,
      label: `${t(getBrowseFilterLabelKey(filter))} (${count})`,
      showSelectedCheck: false,
      accessibilityLabel: `Filter ${filter} cards`,
      checkedColor: theme.colors.onSecondaryContainer,
      uncheckedColor: theme.colors.onSurfaceVariant,
      style: [
        styles.segment,
        isSelected && { backgroundColor: theme.colors.secondaryContainer },
      ],
      labelStyle: styles.segmentLabel,
    };
  });

  return (
    <SegmentedButtons
      value={browseFilter}
      onValueChange={(filter) => setBrowseFilter(filter as BrowseFilter)}
      buttons={buttons}
      style={styles.segmentedButtons}
    />
  );
}

const styles = StyleSheet.create({
  segmentedButtons: {
    width: '100%',
    marginBottom: TOKENS.spacing.lg,
  },
  segment: {
    flex: 1,
    minHeight: TOKENS.touchTarget.min,
    minWidth: 0,
  },
  segmentLabel: {
    fontSize: 12,
    lineHeight: 16,
    textAlign: 'center',
  },
});
