import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import type { Flashcard, FlashcardGroup } from '@/types/models';
import { useI18n } from '@/i18n';
import { AppIcon } from '@/components/AppIcon';
import { getVisiblePages } from '@/store/selectors/pages';
import {
  getActiveRevealedPage,
  getAdaptiveCardFontSize,
  getBottomAlignedScrollY,
} from '@/features/study/session/sessionUtils';
import { CardPageSection } from './CardPageSection';
import type { CardAudioIndicator } from './CardPageSection';
import { TOKENS } from '@/theme/tokens';
import { getElevationStyle } from '@/theme/elevation';
import { getDueColor } from '@/theme/semanticColors';

export type CardAudioMode = 'speaking' | 'listening' | null;

interface StudyCardProps {
  currentCard: Flashcard | null;
  activeGroup: FlashcardGroup;
  revealedPages: number[];
  peekedPageIndex: number | null;
  showRatingButtons: boolean;
  waitingForTap: boolean;
  audioPageIndex: number | null;
  audioMode: CardAudioMode;
  onCardPress: () => void;
  onHoldingChange: (holding: boolean) => void;
}

export function StudyCard({
  currentCard,
  activeGroup,
  revealedPages,
  peekedPageIndex,
  showRatingButtons,
  waitingForTap,
  audioPageIndex,
  audioMode,
  onCardPress,
  onHoldingChange,
}: StudyCardProps) {
  const theme = useTheme();
  const { t } = useI18n();
  const scale = useSharedValue(1);
  const tapToRevealLabel = t('study.tap_to_reveal');

  const scrollRef = useRef<ScrollView>(null);
  const pageGeometryRef = useRef<Record<number, { y: number; height: number }>>({});
  const viewportHeightRef = useRef(0);
  const contentHeightRef = useRef(0);
  const prevRevealedRef = useRef<number[]>([]);

  // Pages flex-grow to fill the viewport, so the content height equals the
  // viewport unless the card text genuinely overflows it.
  const [contentOverflows, setContentOverflows] = useState(false);
  const updateContentOverflows = useCallback(() => {
    setContentOverflows(contentHeightRef.current > viewportHeightRef.current + 1);
  }, []);

  const scrollToY = useCallback((y: number) => {
    // Defer one frame so a just-committed layout/state change is in place first.
    requestAnimationFrame(() => scrollRef.current?.scrollTo({ y, animated: true }));
  }, []);

  // Reset scroll state and cached measurements when the card changes.
  useEffect(() => {
    pageGeometryRef.current = {};
    prevRevealedRef.current = [];
    scrollRef.current?.scrollTo({ y: 0, animated: false });
  }, [currentCard?.id]);

  // Auto-scroll as pages are revealed. Hidden pages reserve their full height, so
  // revealing only changes opacity (no reflow) and the page geometry measured at
  // mount is still accurate — dock the active page to the bottom of the viewport.
  useEffect(() => {
    const activePage = getActiveRevealedPage(prevRevealedRef.current, revealedPages);
    prevRevealedRef.current = revealedPages;
    if (activePage === null) return;
    const geometry = pageGeometryRef.current[activePage];
    if (!geometry) return;
    scrollToY(getBottomAlignedScrollY(geometry.y, geometry.height, viewportHeightRef.current));
  }, [revealedPages, scrollToY]);

  // When the answer is shown for rating, scroll back to the top to review it.
  useEffect(() => {
    if (showRatingButtons) scrollToY(0);
  }, [showRatingButtons, scrollToY]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const pressIn = () => {
    scale.value = withSpring(0.97, TOKENS.motion.spring.tap);
    onHoldingChange(true);
  };

  const pressOut = () => {
    scale.value = withSpring(1, TOKENS.motion.spring.tap);
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
        accessibilityRole="button"
        accessibilityLabel={waitingForTap ? tapToRevealLabel : currentCard?.pages[0] || tapToRevealLabel}
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
          <ScrollView
            ref={scrollRef}
            style={styles.scroll}
            contentContainerStyle={[
              styles.scrollContent,
              showRatingButtons && contentOverflows && styles.scrollContentWithRatings,
            ]}
            showsVerticalScrollIndicator={false}
            onLayout={(event) => {
              viewportHeightRef.current = event.nativeEvent.layout.height;
              updateContentOverflows();
            }}
            onContentSizeChange={(_width, height) => {
              contentHeightRef.current = height;
              updateContentOverflows();
            }}
          >
            {(() => {
              if (!currentCard) return null;
              const activePageCount = activeGroup.activePageCount;
              const visiblePages = getVisiblePages(currentCard, activeGroup);

              return activeGroup.pageNames.slice(0, activePageCount).map((pageName, pageIndex) => {
                const content = visiblePages[pageIndex] || '';
                const isRevealed =
                  pageIndex === 0 || revealedPages.includes(pageIndex) || peekedPageIndex === pageIndex || showRatingButtons;
                const audioIndicator: CardAudioIndicator | null =
                  audioMode && audioPageIndex === pageIndex
                    ? {
                        iconName: audioMode === 'speaking' ? 'volume-high' : 'microphone',
                        color: audioMode === 'speaking' ? theme.colors.primary : getDueColor(theme),
                      }
                    : null;

                return (
                  <CardPageSection
                    key={`${currentCard.id}-${pageIndex}`}
                    pageName={pageName}
                    pageContent={content}
                    isRevealed={isRevealed}
                    isPrimary={pageIndex === 0}
                    hasBorderTop={pageIndex > 0}
                    borderColor={theme.colors.outlineVariant}
                    primaryColor={theme.colors.primary}
                    textColor={theme.colors.onSurface}
                    labelColor={theme.colors.onSurfaceVariant}
                    fontSize={getAdaptiveCardFontSize(content)}
                    audioIndicator={audioIndicator}
                    onLayout={(event) => {
                      const { y, height } = event.nativeEvent.layout;
                      pageGeometryRef.current[pageIndex] = { y, height };
                    }}
                  />
                );
              });
            })()}
          </ScrollView>

          {waitingForTap ? (
            <View style={styles.tapIndicator}>
              <AppIcon name="gesture-tap" size={TOKENS.iconSize.sm} color={theme.colors.outline} />
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
    paddingBottom: TOKENS.spacing.xxl,
    maxWidth: TOKENS.layout.studyCardMaxWidth,
    width: '100%',
    alignSelf: 'center',
    overflow: 'hidden',
  },
  scroll: {
    flex: 1,
    width: '100%',
  },
  scrollContent: {
    flexGrow: 1,
    width: '100%',
    justifyContent: 'center',
  },
  // Clear space under the answer so the floating rating buttons don't cover the
  // last lines of a long card. Applied only when the content actually overflows;
  // otherwise it would shrink the evenly-distributed pages and shift them up.
  scrollContentWithRatings: {
    paddingBottom: TOKENS.control.height + TOKENS.spacing.xxl,
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
