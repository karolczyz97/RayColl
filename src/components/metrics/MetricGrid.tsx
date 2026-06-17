import React from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { LinearTransition } from 'react-native-reanimated';
import type { MetricItem } from './MetricCard';
import { MetricCard } from './MetricCard';
import { TOKENS } from '@/theme/tokens';
import { useNavigationShell } from '@/contexts/NavigationShellContext';

interface MetricGridProps {
  items: MetricItem[];
  screenMaxWidth?: number;
}

export function MetricGrid({
  items,
  screenMaxWidth = TOKENS.layout.maxWidth,
}: MetricGridProps) {
  const { contentWidth } = useNavigationShell();

  const gap = TOKENS.spacing.lg;
  const availableWidth = Math.min(screenMaxWidth, Math.max(0, contentWidth - TOKENS.spacing.lg * 2));
  const columns = availableWidth >= TOKENS.layout.minCardWidth * 2 + gap ? 4 : 2;
  const cellStyle = columns === 4 ? styles.fourColumnCell : styles.twoColumnCell;

  return (
    <View style={[styles.grid, { gap }]}>
      {items.map((item) => (
        <Animated.View
          key={item.label}
          layout={LinearTransition.duration(TOKENS.motion.duration.medium)}
          style={[styles.cell, cellStyle]}
        >
          <MetricCard item={item} />
        </Animated.View>
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
  fourColumnCell: {
    flexBasis: 0,
    flexGrow: 1,
  },
  twoColumnCell: {
    flexBasis: '45%',
    flexGrow: 1,
  },
});
