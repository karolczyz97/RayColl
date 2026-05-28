import React from 'react';
import { StyleSheet } from 'react-native';
import { SegmentedButtons, useTheme } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';
import type { CardStats } from '../../store/selectors/stats';
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
  stats: CardStats;
  t: TranslationFn;
}

function isBrowseFilter(value: string): value is BrowseFilter {
  return BROWSE_FILTER_ORDER.some((filter) => filter === value);
}

export function BrowseFilterChips({ browseFilter, setBrowseFilter, stats, t }: Props) {
  const theme = useTheme();

  const buttons = BROWSE_FILTER_ORDER.map((filter) => {
    const count = stats[BROWSE_FILTER_STATS_KEY[filter]];
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
        {
          backgroundColor: isSelected
            ? theme.colors.secondaryContainer
            : theme.colors.surfaceVariant,
          borderColor: 'transparent',
          borderWidth: 0,
        },
      ],
      labelStyle: styles.segmentLabel,
    };
  });

  return (
    <SegmentedButtons
      value={browseFilter}
      onValueChange={(filter) => {
        if (isBrowseFilter(filter)) {
          setBrowseFilter(filter);
        }
      }}
      buttons={buttons}
      style={[
        styles.segmentedButtons,
        {
          backgroundColor: theme.colors.surfaceVariant,
          borderRadius: TOKENS.radius.pill,
          overflow: 'hidden',
        },
      ]}
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
    fontSize: TOKENS.typography.size.xs,
    lineHeight: 16,
    textAlign: 'center',
  },
});
