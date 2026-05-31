import React, { useMemo } from 'react';
import { Platform, StyleSheet, View, useWindowDimensions } from 'react-native';
import type { MetricItem } from './MetricCard';
import { MetricCard } from './MetricCard';
import { TOKENS } from '../../theme/tokens';
import {
  getDeterministicContainerWidth,
  getGridColumns,
  getGridGap,
  getGridItemWidth,
} from '../../utils/gridLayout';

interface MetricGridProps {
  items: MetricItem[];
  screenMaxWidth?: number;
  hasScrollViewPadding?: boolean;
}

export function MetricGrid({
  items,
  screenMaxWidth = TOKENS.layout.maxWidth,
  hasScrollViewPadding = false,
}: MetricGridProps) {
  const { width: windowWidth } = useWindowDimensions();

  const currentWidth = useMemo(() => {
    return getDeterministicContainerWidth(
      windowWidth,
      screenMaxWidth,
      hasScrollViewPadding,
      Platform.OS === 'web',
    );
  }, [windowWidth, screenMaxWidth, hasScrollViewPadding]);

  const gap = useMemo(() => getGridGap(currentWidth), [currentWidth]);
  // Use same threshold as DeckGrid: expand to 4 cols when 2 deck-sized cards fit side by side.
  const deckCols = getGridColumns(currentWidth, 4, TOKENS.layout.minCardWidth, 2, gap);
  const columns = Math.min(items.length, deckCols >= 2 ? 4 : 2);
  const itemWidth = useMemo(
    () => getGridItemWidth(currentWidth, columns, gap),
    [columns, currentWidth, gap],
  );

  return (
    <View style={[styles.grid, { gap }]}>
      {items.map((item) => (
        <View
          key={`${item.label}-${item.value}`}
          style={[styles.cell, { width: itemWidth, maxWidth: itemWidth, flexBasis: itemWidth }]}
        >
          <MetricCard item={item} />
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    width: '100%',
  },
  cell: {
    minWidth: 0,
  },
});
