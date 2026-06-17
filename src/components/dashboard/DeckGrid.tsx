import React, { useMemo } from 'react';
import { View, StyleSheet } from 'react-native';
import Animated, { LinearTransition } from 'react-native-reanimated';
import { GroupCard } from './GroupCard';
import type { FlashcardGroup, StudyMode } from '@/types/models';
import { TOKENS } from '@/theme/tokens';
import { useNavigationShell } from '@/contexts/NavigationShellContext';
import { AnimatedSection } from '@/components/layout/AnimatedSection';

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
  const { contentWidth } = useNavigationShell();

  const gap = TOKENS.spacing.lg;
  const currentWidth = Math.min(
    TOKENS.layout.maxWidth,
    Math.max(0, contentWidth - TOKENS.spacing.lg * 2),
  );

  const columns = useMemo(() => {
    const min = TOKENS.layout.minCardWidth;

    if (currentWidth >= min * 4 + gap * 3) return 4;
    if (currentWidth >= min * 3 + gap * 2) return 3;
    if (currentWidth >= min * 2 + gap) return 2;
    return 1;
  }, [currentWidth, gap]);

  const widthStyle =
    columns === 4
      ? styles.fourColumnItem
      : columns === 3
        ? styles.threeColumnItem
        : columns === 2
          ? styles.twoColumnItem
          : styles.oneColumnItem;
  const fillerCount = groups.length > 0 ? (columns - (groups.length % columns)) % columns : 0;

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
      {Array.from({ length: fillerCount }).map((_, index) => (
        <View
          key={`filler-${index}`}
          pointerEvents="none"
          style={[styles.gridItem, widthStyle, styles.fillerItem]}
        />
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
  fillerItem: {
    opacity: 0,
  },
  fourColumnItem: {
    flexBasis: '23%',
    flexGrow: 1,
  },
  threeColumnItem: {
    flexBasis: '31%',
    flexGrow: 1,
  },
  twoColumnItem: {
    flexBasis: '47%',
    flexGrow: 1,
  },
  oneColumnItem: {
    flexBasis: '100%',
    flexGrow: 1,
  },
});
