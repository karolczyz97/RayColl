import React, { useEffect } from 'react';
import { Platform, Pressable, StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import type { Flashcard, FlashcardGroup } from '../../../types/models';
import { AppIcon } from '../../../components/AppIcon';
import { getVisiblePages } from '../../../store/selectors/pages';
import { CardPageSection } from './CardPageSection';
import { TOKENS } from '../../../theme/tokens';
import { getElevationStyle } from '../../../theme/elevation';

interface StudyCardProps {
  currentCard: Flashcard | null;
  activeGroup: FlashcardGroup;
  revealedPages: number[];
  peekedPageIndex: number | null;
  showRatingButtons: boolean;
  waitingForTap: boolean;
  onCardPress: () => void;
  onHoldingChange: (holding: boolean) => void;
  tapToRevealLabel: string;
}

export function StudyCard({
  currentCard,
  activeGroup,
  revealedPages,
  peekedPageIndex,
  showRatingButtons,
  waitingForTap,
  onCardPress,
  onHoldingChange,
  tapToRevealLabel,
}: StudyCardProps) {
  const theme = useTheme();
  const scale = useSharedValue(1);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const pressIn = () => {
    scale.value = withSpring(0.96, { damping: 12, stiffness: 150 });
    onHoldingChange(true);
  };

  const pressOut = () => {
    scale.value = withSpring(1, { damping: 12, stiffness: 150 });
    onHoldingChange(false);
  };

  useEffect(() => {
    return () => {
      onHoldingChange(false);
    };
  }, [onHoldingChange]);

  return (
    <View style={styles.cardContainer}>
      <Pressable
        style={styles.cardPressable}
        onPressIn={pressIn}
        onPressOut={pressOut}
        onPress={onCardPress}
        accessibilityRole={waitingForTap ? 'button' : undefined}
        accessibilityLabel={waitingForTap ? tapToRevealLabel : undefined}
      >
        <Animated.View
          style={[
            styles.animatedCard,
            animatedStyle,
            {
              backgroundColor: theme.colors.surface,
              borderColor: theme.colors.outlineVariant,
            },
            getElevationStyle(TOKENS.elevation.level2, theme.colors.shadow, Platform.OS),
          ]}
        >
          {(() => {
            if (!currentCard) return null;
            const activePageCount = activeGroup.activePageCount;
            const visiblePages = getVisiblePages(currentCard, activeGroup);

            return activeGroup.pageNames.slice(0, activePageCount).map((pageName, pageIndex) => {
              const isRevealed =
                pageIndex === 0 || revealedPages.includes(pageIndex) || peekedPageIndex === pageIndex || showRatingButtons;

              return (
                <CardPageSection
                  key={`${currentCard.id}-${pageIndex}`}
                  pageName={pageName}
                  pageContent={visiblePages[pageIndex] || ''}
                  isRevealed={isRevealed}
                  isPrimary={pageIndex === 0}
                  hasBorderTop={pageIndex > 0}
                  borderColor={theme.colors.outlineVariant}
                  primaryColor={theme.colors.primary}
                  textColor={theme.colors.onSurface}
                  labelColor={theme.colors.onSurfaceVariant}
                />
              );
            });
          })()}

          {waitingForTap ? (
            <View style={styles.tapIndicator}>
              <AppIcon name="gesture-tap" size={20} color={theme.colors.outline} />
              <Text variant="labelMedium" style={[styles.tapLabel, { color: theme.colors.outline }]}>
                {tapToRevealLabel}
              </Text>
            </View>
          ) : null}
        </Animated.View>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  cardContainer: {
    flex: 1,
    alignItems: 'stretch',
    justifyContent: 'center',
    marginVertical: TOKENS.spacing.sm,
  },
  cardPressable: {
    flex: 1,
  },
  animatedCard: {
    flex: 1,
    borderRadius: TOKENS.radius.xxl,
    borderWidth: 1,
    padding: TOKENS.spacing.xl,
    maxWidth: TOKENS.layout.studyCardMaxWidth,
    width: '100%',
    alignSelf: 'center',
  },
  tapIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: TOKENS.spacing.sm,
    position: 'absolute',
    bottom: TOKENS.spacing.md,
    left: 0,
    right: 0,
  },
  tapLabel: {
    fontWeight: TOKENS.typography.weight.bold,
  },
});
