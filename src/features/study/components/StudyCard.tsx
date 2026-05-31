import React, { useEffect } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import type { Flashcard, FlashcardGroup } from '../../../types/models';
import { AppIcon } from '../../../components/AppIcon';
import { getVisiblePages } from '../../../store/selectors/pages';
import { CardPageSection } from './CardPageSection';
import { TOKENS } from '../../../theme/tokens';

interface StudyCardProps {
  currentCard: Flashcard | null;
  activeGroup: FlashcardGroup;
  currentCardIndex: number;
  revealedPages: number[];
  peekedPages: number[];
  showRatingButtons: boolean;
  waitingForTap: boolean;
  onCardPress: () => void;
  onHoldingChange: (holding: boolean) => void;
  tapToRevealLabel: string;
}

export function StudyCard({
  currentCard,
  activeGroup,
  currentCardIndex,
  revealedPages,
  peekedPages,
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
              shadowColor: theme.colors.shadow,
            },
          ]}
        >
          {(() => {
            if (!currentCard) return null;
            const activePageCount = activeGroup.activePageCount;
            const visiblePages = getVisiblePages(currentCard, activeGroup);

            return activeGroup.pageNames.slice(0, activePageCount).map((pageName, pageIndex) => {
              const isRevealed =
                pageIndex === 0 || revealedPages.includes(pageIndex) || peekedPages.includes(pageIndex) || showRatingButtons;

              return (
                <CardPageSection
                  key={`${currentCardIndex}-${pageIndex}`}
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
    elevation: 2,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 10,
    padding: TOKENS.spacing.xl,
    maxWidth: 600,
    width: '100%',
    alignSelf: 'center',
  },
  tapIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    position: 'absolute',
    bottom: TOKENS.spacing.md,
    left: 0,
    right: 0,
  },
  tapLabel: {
    fontWeight: 'bold',
  },
});
