import React, { useEffect, useCallback, useMemo, useRef } from 'react';
import { View, StyleSheet, Pressable, useWindowDimensions } from 'react-native';
import { Text, Button, ProgressBar, useTheme, ActivityIndicator } from 'react-native-paper';
import { useLocalSearchParams, router } from 'expo-router';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  withRepeat,
  withSequence,
  FadeIn,
  FadeInUp,
  ZoomIn,
} from 'react-native-reanimated';
import { AppIcon } from '../../components/AppIcon';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useStudySession } from '../../hooks/useStudySession';
import type { Flashcard } from '../../types/models';
import { PageHeader } from '../../components/PageHeader';
import { GroupNotFound } from '../../components/GroupNotFound';
import { useI18n } from '../../i18n';
import { SegmentedProgressBar } from '../../components/SegmentedProgressBar';
import { computeCardStats } from '../../store/selectors/stats';
import {
  getDangerColor,
  getDangerBgColor,
  getInfoColor,
  getSuccessColor,
  getWarningColor,
  getWarningBgColor,
} from '../../theme/semanticColors';
import { getVisiblePages } from '../../store/selectors/pages';

// Individual page section that fades in when revealed
function CardPageSection({
  pageName,
  pageContent,
  isRevealed,
  isPrimary,
  hasBorderTop,
  borderColor,
  primaryColor,
  textColor,
  labelColor,
}: {
  pageName: string;
  pageContent: string;
  isRevealed: boolean;
  isPrimary: boolean;
  hasBorderTop: boolean;
  borderColor: string;
  primaryColor: string;
  textColor: string;
  labelColor: string;
}) {
  const opacity = useSharedValue(isRevealed ? 1 : 0);

  useEffect(() => {
    opacity.value = withSpring(isRevealed ? 1 : 0, {
      damping: 20,
      stiffness: 120,
      mass: 0.8,
    });
  }, [isRevealed, opacity]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
  }));

  return (
    <View style={[styles.pageRow, hasBorderTop && styles.pageRowBorder, { borderColor }]}>
      <Text style={[styles.pageLabel, { color: labelColor }]}>{pageName}</Text>
      <Animated.View style={animatedStyle}>
        <Text
          variant="headlineMedium"
          style={[styles.cardText, { color: isPrimary ? primaryColor : textColor }]}
        >
          {isRevealed ? pageContent || '—' : ' '}
        </Text>
      </Animated.View>
    </View>
  );
}

export default function StudyPage() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const { width } = useWindowDimensions();
  const isNarrow = width < 480;

  const getButtonText = useCallback(
    (key: string) => {
      if (isNarrow) return '';
      const text = t(key);
      return text.split(' (')[0];
    },
    [isNarrow, t],
  );

  const group = store.groups.find((g) => g.id === groupId) || null;

  const activeGroup = group;

  const mode =
    store.studyModes.find((m) => m.id === activeGroup?.activeModeId) || store.studyModes[0];
  const steps = useMemo(() => mode?.steps || [], [mode]);

  const onCardReviewed = useCallback(
    (gId: string, card: Flashcard) => {
      store.updateFlashcard(gId, card);
      store.recordActivity();
    },
    [store],
  );

  const {
    dueCards,
    sessionState,
    handleRating,
    handleCardTap,
    startSession,
    stopSession,
    setHolding,
    restartSession,
    restartFailed,
    failedCount,
  } = useStudySession(activeGroup, steps, onCardReviewed);

  const s = sessionState;
  const currentCard = dueCards[s.currentCardIndex] || null;

  // Start session on mount/group load
  const startedRef = useRef<string | null>(null);

  useEffect(() => {
    if (store.isLoading) return;
    if (startedRef.current === groupId) return;

    const currentGroup = store.groups.find((g) => g.id === groupId);
    if (currentGroup) {
      const due = store.getDueCards(currentGroup.id);
      if (due.length > 0) {
        startSession(due);
        startedRef.current = groupId;
      }
    }
  }, [groupId, store.isLoading, startSession, store]);

  const handleBack = () => {
    stopSession();
    router.back();
  };

  const progressPct = dueCards.length > 0 ? s.currentCardIndex / dueCards.length : 0;
  const matchColor =
    s.sttMatchPercent >= 85
      ? getSuccessColor(theme)
      : s.sttMatchPercent >= 60
        ? getInfoColor(theme)
        : s.sttMatchPercent >= 40
          ? getWarningColor(theme)
          : getDangerColor(theme);

  const hasTts = useMemo(() => steps.some((st) => st.type === 'speak_page'), [steps]);
  const hasStt = useMemo(() => steps.some((st) => st.type === 'listen_and_branch'), [steps]);

  // Card scale animation (press in/out)
  const scale = useSharedValue(1);

  const cardAnimatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  // Micro-animations for TTS and STT Icons
  const ttsScale = useSharedValue(1);
  useEffect(() => {
    if (s.isTtsPlaying) {
      ttsScale.value = withRepeat(
        withSequence(withSpring(1.25, { damping: 3 }), withSpring(1, { damping: 3 })),
        -1,
        true,
      );
    } else {
      ttsScale.value = withSpring(1);
    }
  }, [s.isTtsPlaying, ttsScale]);

  const ttsIconStyle = useAnimatedStyle(() => ({
    transform: [{ scale: ttsScale.value }],
  }));

  const sttScale = useSharedValue(1);
  useEffect(() => {
    if (s.isSttListening) {
      sttScale.value = withRepeat(
        withSequence(withSpring(1.25, { damping: 3 }), withSpring(1, { damping: 3 })),
        -1,
        true,
      );
    } else {
      sttScale.value = withSpring(1);
    }
  }, [s.isSttListening, sttScale]);

  const sttIconStyle = useAnimatedStyle(() => ({
    transform: [{ scale: sttScale.value }],
  }));

  const pressIn = () => {
    scale.value = withSpring(0.96, { damping: 12, stiffness: 150 });
    setHolding(true);
  };

  const pressOut = () => {
    scale.value = withSpring(1, { damping: 12, stiffness: 150 });
    setHolding(false);
  };

  if (store.isLoading) {
    return (
      <View style={[styles.centerContainer, { backgroundColor: theme.colors.background }]}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
      </View>
    );
  }

  if (!activeGroup) {
    return <GroupNotFound onBack={handleBack} />;
  }

  // Completion screen
  if (s.isSessionFinished || dueCards.length === 0) {
    return (
      <View style={[styles.finishedContainer, { backgroundColor: theme.colors.background }]}>
        <Animated.View entering={ZoomIn.springify().damping(12)}>
          <AppIcon name="check-circle" size={96} color={getSuccessColor(theme)} />
        </Animated.View>
        <Animated.View entering={FadeInUp.springify().delay(150)}>
          <Text variant="headlineLarge" style={styles.bravoTitle}>
            {t('study.bravo')}
          </Text>
        </Animated.View>
        <Animated.View entering={FadeInUp.springify().delay(250)}>
          <Text
            variant="bodyLarge"
            style={[styles.finishedDesc, { color: theme.colors.onSurfaceVariant }]}
          >
            {dueCards.length === 0 ? t('study.no_due') : t('study.finished_desc')}
          </Text>
        </Animated.View>

        <Animated.View entering={FadeInUp.springify().delay(350)} style={styles.statsSummary}>
          <Text variant="titleMedium" style={styles.statsTitle}>
            {t('stats.deck_progress')}
          </Text>
          <SegmentedProgressBar stats={computeCardStats(activeGroup.cards)} showLegend />
        </Animated.View>

        <Animated.View entering={FadeInUp.springify().delay(450)} style={styles.actionButtons}>
          {failedCount > 0 && (
            <Button
              mode="contained"
              buttonColor={theme.colors.error}
              onPress={restartFailed}
              style={styles.actionBtn}
            >
              {t('study.restart_failed')} ({failedCount})
            </Button>
          )}
          <Button mode="contained" onPress={restartSession} style={styles.actionBtn}>
            {t('study.restart_session')}
          </Button>
          <Button mode="outlined" onPress={handleBack} style={styles.actionBtn}>
            {t('study.back_to_panel')}
          </Button>
        </Animated.View>
      </View>
    );
  }

  return (
    <View style={[styles.root, { backgroundColor: theme.colors.background }]}>
      {/* Top Header */}
      <View style={styles.header}>
        <PageHeader title={activeGroup.name} onBack={handleBack} />
        <View style={styles.progressBarWrapper}>
          <ProgressBar
            progress={progressPct}
            color={theme.colors.primary}
            style={styles.progressBar}
          />
        </View>
        <Text style={[styles.progressCounter, { color: theme.colors.onSurfaceVariant }]}>
          {s.currentCardIndex + 1}/{dueCards.length}
        </Text>
      </View>

      {/* Main card viewport — N sections, fade-in-place for each page */}
      <View style={styles.cardContainer}>
        <Pressable
          style={styles.cardPressable}
          onPressIn={pressIn}
          onPressOut={pressOut}
          onPress={s.waitingForTap ? handleCardTap : undefined}
        >
          <Animated.View
            style={[
              styles.animatedCard,
              cardAnimatedStyle,
              {
                backgroundColor: theme.colors.surface,
                borderColor: theme.colors.outlineVariant,
                shadowColor: theme.colors.shadow,
              },
            ]}
          >
            {(() => {
              if (!currentCard) return null;
              const activePageCount = activeGroup.activePageCount ?? activeGroup.pageNames.length;
              const visiblePages = getVisiblePages(currentCard, activeGroup);

              return activeGroup.pageNames.slice(0, activePageCount).map((pageName, pi) => {
                const isRevealed = pi === 0 || s.revealedPages.includes(pi) || s.showRatingButtons;
                return (
                  <CardPageSection
                    key={`${s.currentCardIndex}-${pi}`}
                    pageName={pageName}
                    pageContent={visiblePages[pi] || ''}
                    isRevealed={isRevealed}
                    isPrimary={pi === 0}
                    hasBorderTop={pi > 0}
                    borderColor={theme.colors.outlineVariant}
                    primaryColor={theme.colors.primary}
                    textColor={theme.colors.onSurface}
                    labelColor={theme.colors.onSurfaceVariant}
                  />
                );
              });
            })()}

            {s.waitingForTap && (
              <View style={styles.tapIndicator}>
                <AppIcon name="gesture-tap" size={20} color={theme.colors.outline} />
                <Text
                  variant="labelMedium"
                  style={{ color: theme.colors.outline, fontWeight: 'bold' }}
                >
                  {t('study.tap_to_reveal')}
                </Text>
              </View>
            )}
          </Animated.View>
        </Pressable>
      </View>

      {/* Bottom control zone */}
      <View style={[styles.bottomControlZone, { height: hasTts || hasStt ? 180 : 80 }]}>
        {s.showRatingButtons ? (
          <Animated.View entering={FadeIn.springify()} style={styles.ratingButtonsRow}>
            <Button
              mode="contained-tonal"
              textColor={getDangerColor(theme)}
              buttonColor={getDangerBgColor(theme)}
              style={styles.rateBtn}
              compact={isNarrow}
              contentStyle={
                isNarrow ? { justifyContent: 'center', paddingLeft: 0, paddingRight: 0 } : {}
              }
              icon={({ size, color }) => <AppIcon name="replay" size={size} color={color} />}
              onPress={() => handleRating(1)}
            >
              {getButtonText('study.rating.1')}
            </Button>
            <Button
              mode="contained-tonal"
              textColor={getWarningColor(theme)}
              buttonColor={getWarningBgColor(theme)}
              style={styles.rateBtn}
              compact={isNarrow}
              contentStyle={
                isNarrow ? { justifyContent: 'center', paddingLeft: 0, paddingRight: 0 } : {}
              }
              icon={({ size, color }) => (
                <AppIcon name="emoticon-sad-outline" size={size} color={color} />
              )}
              onPress={() => handleRating(2)}
            >
              {getButtonText('study.rating.2')}
            </Button>
            <Button
              mode="contained"
              style={styles.rateBtn}
              compact={isNarrow}
              contentStyle={
                isNarrow ? { justifyContent: 'center', paddingLeft: 0, paddingRight: 0 } : {}
              }
              icon={({ size, color }) => (
                <AppIcon name="emoticon-happy-outline" size={size} color={color} />
              )}
              onPress={() => handleRating(3)}
            >
              {getButtonText('study.rating.3')}
            </Button>
            <Button
              mode="contained"
              buttonColor={getSuccessColor(theme)}
              textColor={theme.colors.onTertiary}
              style={styles.rateBtn}
              compact={isNarrow}
              contentStyle={
                isNarrow ? { justifyContent: 'center', paddingLeft: 0, paddingRight: 0 } : {}
              }
              icon={({ size, color }) => (
                <AppIcon name="emoticon-excited-outline" size={size} color={color} />
              )}
              onPress={() => handleRating(4)}
            >
              {getButtonText('study.rating.4')}
            </Button>
          </Animated.View>
        ) : hasTts || hasStt ? (
          <View style={styles.feedbackContainer}>
            {/* Recognized voice display */}
            <View style={styles.sttTextWrapper}>
              {s.sttResultText ? (
                <View
                  style={[styles.sttResultBox, { backgroundColor: theme.colors.surfaceVariant }]}
                >
                  <Text variant="labelSmall" style={{ color: theme.colors.onSurfaceVariant }}>
                    {t('study.recognized')}
                  </Text>
                  <Text
                    variant="bodyLarge"
                    style={{ fontWeight: 'bold', color: theme.colors.onSurface }}
                  >
                    {s.sttResultText}
                  </Text>
                  {s.sttMatchPercent > 0 && (
                    <Text
                      variant="labelSmall"
                      style={{ color: matchColor, fontWeight: 'bold', marginTop: 4 }}
                    >
                      {t('study.match_percent', { percent: s.sttMatchPercent })}
                    </Text>
                  )}
                </View>
              ) : null}
            </View>

            {/* Speaking/listening icons */}
            <View style={styles.audioIconsRow}>
              {hasTts && (
                <Animated.View style={[styles.iconWrapper, ttsIconStyle]}>
                  <AppIcon
                    name="volume-high"
                    size={36}
                    color={s.isTtsPlaying ? theme.colors.primary : theme.colors.outline}
                  />
                </Animated.View>
              )}
              {hasStt && (
                <Animated.View style={[styles.iconWrapper, sttIconStyle]}>
                  <AppIcon
                    name="microphone"
                    size={36}
                    color={s.isSttListening ? getDangerColor(theme) : theme.colors.outline}
                  />
                </Animated.View>
              )}
            </View>
          </View>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 48,
    paddingBottom: 24,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  header: {
    marginBottom: 16,
  },
  progressBarWrapper: {
    marginVertical: 12,
    width: '100%',
  },
  progressBar: {
    height: 8,
    borderRadius: 4,
  },
  progressCounter: {
    fontSize: 12,
    textAlign: 'right',
    fontWeight: '600',
    letterSpacing: 0.5,
  },
  cardContainer: {
    flex: 1,
    alignItems: 'stretch',
    justifyContent: 'center',
    marginVertical: 8,
  },
  cardPressable: {
    flex: 1,
  },
  animatedCard: {
    flex: 1,
    borderRadius: 28,
    borderWidth: 1,
    elevation: 2,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 10,
    padding: 24,
    maxWidth: 600,
    width: '100%',
    alignSelf: 'center',
  },
  pageRow: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    width: '100%',
    paddingVertical: 12,
  },
  pageRowBorder: {
    borderTopWidth: 1,
  },
  pageLabel: {
    fontSize: 11,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    marginBottom: 8,
    fontWeight: '700',
  },
  cardText: {
    textAlign: 'center',
    fontWeight: 'bold',
  },
  tapIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    position: 'absolute',
    bottom: 12,
    left: 0,
    right: 0,
  },
  bottomControlZone: {
    justifyContent: 'center',
    marginTop: 16,
  },
  ratingButtonsRow: {
    flexDirection: 'row',
    gap: 8,
    width: '100%',
  },
  rateBtn: {
    flex: 1,
    borderRadius: 100,
  },
  feedbackContainer: {
    flex: 1,
    justifyContent: 'space-between',
  },
  sttTextWrapper: {
    height: 80,
    justifyContent: 'center',
    alignItems: 'center',
  },
  sttResultBox: {
    padding: 12,
    borderRadius: 16,
    width: '100%',
    alignItems: 'center',
  },
  audioIconsRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: 32,
    height: 60,
  },
  iconWrapper: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  finishedContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  bravoTitle: {
    fontWeight: 'bold',
    marginTop: 16,
    marginBottom: 8,
  },
  finishedDesc: {
    textAlign: 'center',
    marginBottom: 32,
  },
  statsSummary: {
    width: '100%',
    maxWidth: 400,
    marginBottom: 32,
  },
  statsTitle: {
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 16,
  },
  actionButtons: {
    width: '100%',
    maxWidth: 320,
    gap: 12,
  },
  actionBtn: {
    borderRadius: 100,
  },
});
