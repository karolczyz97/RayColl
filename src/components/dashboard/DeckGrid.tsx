import React, { useMemo } from 'react';
import { Platform, View, StyleSheet } from 'react-native';
import Animated, { LinearTransition } from 'react-native-reanimated';
import { GroupCard } from './GroupCard';
import type { FlashcardGroup, StudyMode } from '@/types/models';
import { TOKENS } from '@/theme/tokens';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { useNavigationShell } from '@/contexts/NavigationShellContext';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import {
  getGridColumns,
  getDeterministicContainerWidth,
  getGridGap,
  getGridItemWidth,
} from '@/utils/gridLayout';

interface DeckGridProps {
  baseOrder?: number;
  groups: FlashcardGroup[];
  /** Passed to the default GroupCard renderer; not needed when renderItem is provided. */
  studyModes?: StudyMode[];
  /**
   * Custom card renderer. Defaults to the dashboard `GroupCard`; the archive
   * screen overrides it to render archived-variant cards with restore/delete.
   */
  renderItem?: (group: FlashcardGroup) => React.ReactNode;
}

export function DeckGrid({
  baseOrder = 0,
  groups,
  studyModes,
  renderItem,
}: DeckGridProps) {
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

    // Column count is driven by the available width (capped at maxCols), not by
    // the number of decks. This keeps each card at a normal column's width so a
    // single deck doesn't stretch to fill the whole row.
    const columns = getGridColumns(currentWidth, maxCols, minCardWidth, maxCols, gap);
    return { cardWidth: getGridItemWidth(currentWidth, columns, gap), columns };
  }, [currentWidth, gap]);

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
        <Animated.View
          key={group.id}
          layout={LinearTransition.duration(TOKENS.motion.duration.medium)}
          style={[styles.gridItem, widthStyle]}
        >
          <AnimatedSection order={baseOrder + Math.floor(index / columns)}>
            {renderItem ? (
              renderItem(group)
            ) : (
              <GroupCard group={group} studyModes={studyModes} />
            )}
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
