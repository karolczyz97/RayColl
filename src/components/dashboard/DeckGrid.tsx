import React, { useMemo } from 'react';
import { Platform, View, StyleSheet } from 'react-native';
import { GroupCard } from './GroupCard';
import type { FlashcardGroup } from '../../types/models';
import { TOKENS } from '../../theme/tokens';
import { useResponsiveLayout } from '../../hooks/useResponsiveLayout';
import { useNavigationShell } from '../../contexts/NavigationShellContext';
import { AnimatedSection } from '../layout/AnimatedSection';
import {
  getGridColumns,
  getDeterministicContainerWidth,
  getGridGap,
  getGridItemWidth,
} from '../../utils/gridLayout';

interface Props {
  baseOrder?: number;
  groups: FlashcardGroup[];
  onModeChange: (groupId: string, modeId: string) => void;
}

export function DeckGrid({ baseOrder = 0, groups, onModeChange }: Props) {
  const { width: windowWidth } = useResponsiveLayout();
  const { navWidth } = useNavigationShell();

  const currentWidth = useMemo(() => {
    return getDeterministicContainerWidth(
      windowWidth,
      TOKENS.layout.maxWidth,
      Platform.OS === 'web',
      navWidth,
    );
  }, [windowWidth, navWidth]);

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
    <View style={[styles.grid, { gap }]}>
      {groups.map((group, index) => (
        <View key={group.id} style={[styles.gridItem, widthStyle]}>
          <AnimatedSection order={baseOrder + Math.floor(index / columns)}>
            <GroupCard
              group={group}
              onModeChange={(modeId) => onModeChange(group.id, modeId)}
            />
          </AnimatedSection>
        </View>
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
