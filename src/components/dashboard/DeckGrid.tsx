import React, { useMemo, useState } from 'react';
import { View, StyleSheet, useWindowDimensions, Platform } from 'react-native';
import Animated, { FadeInDown, Layout } from 'react-native-reanimated';
import { GroupCard } from './GroupCard';
import type { FlashcardGroup } from '../../types/models';
import { TOKENS } from '../../theme/tokens';

interface Props {
  groups: FlashcardGroup[];
  onModeChange: (groupId: string, modeId: string) => void;
}

export function DeckGrid({ groups, onModeChange }: Props) {
  const { width: windowWidth } = useWindowDimensions();
  const [measuredWidth, setMeasuredWidth] = useState<number | undefined>(undefined);

  // Fallback that updates instantly during window resize
  const fallbackWidth = Math.min(1164, windowWidth - 36);
  // Use the smaller of measured layout width or instant window boundary to shrink cards immediately on resize
  const currentWidth = measuredWidth !== undefined ? Math.min(measuredWidth, fallbackWidth) : fallbackWidth;

  // Calculate gap dynamically to match the stats cards space-between gap (2.66% of container width)
  const gap = useMemo(() => {
    return Math.max(
      TOKENS.layout.minGap,
      Math.min(TOKENS.layout.maxGap, Math.floor(currentWidth * TOKENS.layout.gapRatio))
    );
  }, [currentWidth]);

  const { numCols, cardWidth } = useMemo(() => {
    const minCardWidth = TOKENS.layout.minCardWidth;
    const maxCols = TOKENS.layout.maxCols;

    const availableWidth = currentWidth;
    const calculatedCols = Math.floor((availableWidth + gap) / (minCardWidth + gap));
    const normalCols = Math.max(1, Math.min(maxCols, calculatedCols));
    const numColsCalculated = Math.min(normalCols, groups.length || 1);

    // Subtract 1px to absorb floating-point rounding errors and prevent layout wrapping glitches
    const widthCalculated = ((availableWidth - gap * (numColsCalculated - 1)) / numColsCalculated) - 1;
    return { numCols: numColsCalculated, cardWidth: widthCalculated };
  }, [currentWidth, gap, groups.length]);

  const widthStyle = useMemo(() => {
    if (Platform.OS === 'web') {
      return {
        width: `calc((100% - ${(numCols - 1) * gap}px) / ${numCols})`,
      };
    }
    return { width: cardWidth };
  }, [numCols, gap, cardWidth]);

  return (
    <View
      style={[styles.grid, { gap }]}
      onLayout={(event) => setMeasuredWidth(event.nativeEvent.layout.width)}
    >
      {groups.map((group, index) => (
        <Animated.View
          key={group.id}
          entering={FadeInDown.springify().delay(Math.min(index * 80, 600))}
          layout={Layout.springify()}
          style={[styles.gridItem, widthStyle as any]}
        >
          <GroupCard
            group={group}
            onModeChange={(modeId) => onModeChange(group.id, modeId)}
          />
        </Animated.View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    overflow: 'visible',
    width: '100%',
  },
  gridItem: {
    overflow: 'visible',
  },
});
