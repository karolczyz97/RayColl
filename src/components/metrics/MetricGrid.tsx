import React from 'react';
import { StyleSheet, View, useWindowDimensions } from 'react-native';
import type { MetricItem } from './MetricCard';
import { MetricCard } from './MetricCard';
import { TOKENS } from '../../theme/tokens';

interface MetricGridProps {
  items: MetricItem[];
  compactBreakpoint?: number;
}

export function MetricGrid({ items, compactBreakpoint = 600 }: MetricGridProps) {
  const { width } = useWindowDimensions();
  const isCompact = width < compactBreakpoint;

  return (
    <View style={styles.grid}>
      {items.map((item) => (
        <View
          key={`${item.label}-${item.value}`}
          style={[styles.cell, { width: isCompact ? '48%' : '23%' }]}
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
    gap: TOKENS.spacing.sm,
  },
  cell: {
    minWidth: 160,
  },
});
