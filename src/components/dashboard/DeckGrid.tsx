import React, { useMemo, useState } from 'react';
import { View, StyleSheet, useWindowDimensions } from 'react-native';
import Animated, { Layout } from 'react-native-reanimated';
import { GroupCard } from './GroupCard';
import type { FlashcardGroup } from '../../types/models';
import { TOKENS } from '../../theme/tokens';
import { AnimatedSection } from '../layout/AnimatedSection';
import {
  getGridColumns,
  getGridContainerWidth,
  getGridGap,
  getGridItemWidth,
} from '../../utils/gridLayout';

interface Props {
  baseOrder?: number;
  groups: FlashcardGroup[];
  onModeChange: (groupId: string, modeId: string) => void;
}

export function DeckGrid({ baseOrder = 0, groups, onModeChange }: Props) {
  const { width: windowWidth } = useWindowDimensions();
  const [measuredWidth, setMeasuredWidth] = useState<number | undefined>(undefined);

  const currentWidth = getGridContainerWidth({
    measuredWidth,
    windowWidth,
    clampMeasuredToFallback: true,
  });

  const gap = useMemo(() => getGridGap(currentWidth), [currentWidth]);

  const { cardWidth, columns } = useMemo(() => {
    const minCardWidth = TOKENS.layout.minCardWidth;
    const maxCols = TOKENS.layout.maxCols;

    const columns = getGridColumns(currentWidth, groups.length, minCardWidth, maxCols, gap);
    return { cardWidth: getGridItemWidth(currentWidth, columns, gap), columns };
  }, [currentWidth, gap, groups.length]);

  const widthStyle = useMemo(() => {
    return {
      width: cardWidth,
      maxWidth: cardWidth,
      flexBasis: cardWidth,
    };
  }, [cardWidth]);

  return (
    <View
      style={[styles.grid, { gap }]}
      onLayout={(event) => setMeasuredWidth(event.nativeEvent.layout.width)}
    >
      {groups.map((group, index) => (
        <Animated.View key={group.id} layout={Layout.springify()} style={[styles.gridItem, widthStyle]}>
          <AnimatedSection order={baseOrder + Math.floor(index / columns)}>
            <GroupCard
              group={group}
              onModeChange={(modeId) => onModeChange(group.id, modeId)}
            />
          </AnimatedSection>
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
