import React, { useMemo } from 'react';
import { View, StyleSheet, useWindowDimensions } from 'react-native';
import Animated, { FadeInDown, Layout } from 'react-native-reanimated';
import { GroupCard } from './GroupCard';
import type { FlashcardGroup } from '../../types/models';

interface Props {
  groups: FlashcardGroup[];
  onModeChange: (groupId: string, modeId: string) => void;
}

export function DeckGrid({ groups, onModeChange }: Props) {
  const { width } = useWindowDimensions();

  const { cardWidth } = useMemo(() => {
    const maxW = 1200;
    const currentWidth = width > maxW ? maxW : width;
    const numCols = currentWidth < 600 ? 1 : currentWidth < 900 ? 2 : currentWidth < 1200 ? 3 : 4;
    const padding = 16;
    const gap = 16;
    const widthCalculated = (currentWidth - padding * 2 - gap * (numCols - 1)) / numCols;
    return { cardWidth: widthCalculated };
  }, [width]);

  return (
    <View style={styles.grid}>
      {groups.map((group, index) => (
        <Animated.View
          key={group.id}
          entering={FadeInDown.springify().delay(Math.min(index * 80, 600))}
          layout={Layout.springify()}
          style={{ width: cardWidth }}
        >
          <GroupCard
            group={group}
            cardWidth={cardWidth}
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
    gap: 16,
    width: '100%',
  },
});
