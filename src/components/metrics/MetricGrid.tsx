import React, { useMemo } from 'react';
import { Platform, StyleSheet, View, useWindowDimensions } from 'react-native';
import type { MetricItem } from './MetricCard';
import { MetricCard } from './MetricCard';
import { TOKENS } from '../../theme/tokens';
import {
  getDeterministicContainerWidth,
  getGridGap,
  getGridItemWidth,
} from '../../utils/gridLayout';

interface MetricGridProps {
  items: MetricItem[];
  compactBreakpoint?: number;
  screenMaxWidth?: number;
  hasScrollViewPadding?: boolean;
}

export const METRIC_GRID_COMPACT_BREAKPOINT = 900;

export function MetricGrid({
  items,
  compactBreakpoint = METRIC_GRID_COMPACT_BREAKPOINT,
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
  const isCompact = currentWidth < compactBreakpoint;
  const columns = Math.min(items.length, isCompact ? 2 : 4);
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
