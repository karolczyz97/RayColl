import React, { useEffect, useCallback, useMemo, useRef } from 'react';
import { View, StyleSheet, ScrollView, Pressable } from 'react-native';
import { Text, Card, Button, ProgressBar, useTheme, ActivityIndicator } from 'react-native-paper';
import { useLocalSearchParams, router } from 'expo-router';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withSpring,
  interpolate,
  withRepeat,
  withSequence,
} from 'react-native-reanimated';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useStudySession } from '../../hooks/useStudySession';
import type { Flashcard } from '../../types/models';
import { PageHeader } from '../../components/PageHeader';
import { GroupNotFound } from '../../components/GroupNotFound';
import { useI18n } from '../../i18n';
import { SegmentedProgressBar, computeCardStats } from '../../components/SegmentedProgressBar';

export default function StudyPage() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();

  const group = store.groups.find((g) => g.id === groupId) || null;

  // Cache last valid group during exit
  const lastValidGroupRef = useRef<typeof group>(null);
  if (group) {
    lastValidGroupRef.current = group;
  }
  const activeGroup = group || lastValidGroupRef.current;

  const mode = store.studyModes.find((m) => m.id === activeGroup?.activeModeId) || store.studyModes[0];
  const steps = mode?.steps || [];

  const onCardReviewed = useCallback(
    (gId: string, card: Flashcard) => {
      store.updateFlashcard(gId, card);
      store.recordActivity();
    },
    [store]
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
  useEffect(() => {
    if (activeGroup) {
      const due = store.getDueCards(activeGroup.id);
      if (due.length > 0) {
        startSession(due);
      }
    }
  }, [groupId, store.isLoading]);

  const handleBack = () => {
    stopSession();
    router.back();
  };

  const progressPct = dueCards.length > 0 ? s.currentCardIndex / dueCards.length : 0;
  const matchColor =
    s.sttMatchPercent >= 85
      ? '#4caf50'
      : s.sttMatchPercent >= 60
      ? '#2196f3'
      : s.sttMatchPercent >= 40
      ? '#ff9800'
      : '#f44336';

  const hasTts = useMemo(() => steps.some((st) => st.type === 'speak_page'), [steps]);
  const hasStt = useMemo(() => steps.some((st) => st.type === 'listen_and_branch'), [steps]);

  // Card Animation Values
  const rotateY = useSharedValue(0);
  const scale = useSharedValue(1);

  // Sync rotation with revealed status / rating buttons
  useEffect(() => {
    rotateY.value = withSpring(s.showRatingButtons ? 180 : 0, {
      damping: 15,
      stiffness: 90,
    });
  }, [s.showRatingButtons]);

  // Reset rotation on card change
  useEffect(() => {
    rotateY.value = 0;
  }, [s.currentCardIndex]);

  // Animated Styles for Card Container
  const cardAnimatedStyle = useAnimatedStyle(() => {
    return {
      transform: [
        { scale: scale.value },
        { perspective: 1000 },
        { rotateY: `${rotateY.value}deg` },
      ],
    };
  });

  const frontStyle = useAnimatedStyle(() => {
    const opacity = interpolate(rotateY.value, [0, 90, 180], [1, 0, 0]);
    return {
      opacity,
      display: rotateY.value > 90 ? 'none' : 'flex',
    };
  });

  const backStyle = useAnimatedStyle(() => {
    const opacity = interpolate(rotateY.value, [0, 90, 180], [0, 0, 1]);
    return {
      opacity,
      transform: [{ rotateY: '180deg' }],
      display: rotateY.value < 90 ? 'none' : 'flex',
    };
  });

  // Micro-animations for TTS and STT Icons
  const ttsScale = useSharedValue(1);
  useEffect(() => {
    if (s.isTtsPlaying) {
      ttsScale.value = withRepeat(
        withSequence(withSpring(1.25, { damping: 3 }), withSpring(1, { damping: 3 })),
        -1,
        true
      );
    } else {
      ttsScale.value = withSpring(1);
    }
  }, [s.isTtsPlaying]);

  const ttsIconStyle = useAnimatedStyle(() => {
    return {
      transform: [{ scale: ttsScale.value }],
    };
  });

  const sttScale = useSharedValue(1);
  useEffect(() => {
    if (s.isSttListening) {
      sttScale.value = withRepeat(
        withSequence(withSpring(1.25, { damping: 3 }), withSpring(1, { damping: 3 })),
        -1,
        true
      );
    } else {
      sttScale.value = withSpring(1);
    }
  }, [s.isSttListening]);

  const sttIconStyle = useAnimatedStyle(() => {
    return {
      transform: [{ scale: sttScale.value }],
    };
  });

  const pressIn = () => {
    scale.value = withSpring(0.95);
    setHolding(true);
  };

  const pressOut = () => {
    scale.value = withSpring(1);
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
        <MaterialCommunityIcons name="check-circle" size={96} color="#4caf50" style={{ marginBottom: 16 }} />
        <Text variant="headlineLarge" style={styles.bravoTitle}>
          {t('study.bravo')}
        </Text>
        <Text variant="bodyLarge" style={[styles.finishedDesc, { color: theme.colors.onSurfaceVariant }]}>
          {dueCards.length === 0 ? t('study.no_due') : t('study.finished_desc')}
        </Text>

        <View style={styles.statsSummary}>
          <Text variant="titleMedium" style={styles.statsTitle}>
            {t('stats.deck_progress')}
          </Text>
          <SegmentedProgressBar stats={computeCardStats(activeGroup.cards)} showLegend />
        </View>

        <View style={styles.actionButtons}>
          {failedCount > 0 && (
            <Button mode="contained" buttonColor={theme.colors.error} onPress={restartFailed} style={styles.actionBtn}>
              {t('study.restart_failed')} ({failedCount})
            </Button>
          )}
          <Button mode="contained" onPress={restartSession} style={styles.actionBtn}>
            {t('study.restart_session')}
          </Button>
          <Button mode="outlined" onPress={handleBack} style={styles.actionBtn}>
            {t('study.back_to_panel')}
          </Button>
        </View>
      </View>
    );
  }

  return (
    <View style={[styles.root, { backgroundColor: theme.colors.background }]}>
      {/* Top Header */}
      <View style={styles.header}>
        <PageHeader title={activeGroup.name} onBack={handleBack} />
        <View style={styles.progressBarWrapper}>
          <ProgressBar progress={progressPct} color={theme.colors.primary} style={styles.progressBar} />
        </View>
        <Text style={[styles.progressCounter, { color: theme.colors.onSurfaceVariant }]}>
          {s.currentCardIndex + 1}/{dueCards.length}
        </Text>
      </View>

      {/* Main card viewport */}
      <View style={styles.cardContainer}>
        <Pressable
          style={styles.cardPressable}
          onPressIn={pressIn}
          onPressOut={pressOut}
          onPress={s.waitingForTap ? handleCardTap : undefined}
        >
          <Animated.View style={[styles.animatedCard, cardAnimatedStyle, { backgroundColor: theme.colors.surface, borderColor: theme.colors.outlineVariant }]}>
            {/* Card Front Side */}
            <Animated.View style={[styles.cardSide, frontStyle]}>
              {currentCard &&
                activeGroup.pageNames.map((pageName, pi) => {
                  const isRevealed = s.revealedPages.includes(pi);
                  if (pi > 0 && !isRevealed) return null; // Show only revealed on front

                  return (
                    <View key={pi} style={[styles.pageRow, pi > 0 && styles.pageRowBorder, { borderColor: theme.colors.outlineVariant }]}>
                      <Text style={[styles.pageLabel, { color: theme.colors.onSurfaceVariant }]}>
                        {pageName}
                      </Text>
                      <Text variant="headlineMedium" style={[styles.cardText, { color: pi === 0 ? theme.colors.primary : theme.colors.onSurface }]}>
                        {currentCard.pages[pi] || '—'}
                      </Text>
                    </View>
                  );
                })}
              {s.waitingForTap && (
                <View style={styles.tapIndicator}>
                  <MaterialCommunityIcons name="gesture-tap" size={20} color={theme.colors.outline} />
                  <Text variant="labelMedium" style={{ color: theme.colors.outline, fontWeight: 'bold' }}>
                    {t('study.tap_to_reveal')}
                  </Text>
                </View>
              )}
            </Animated.View>

            {/* Card Back Side */}
            <Animated.View style={[styles.cardSide, backStyle]}>
              {currentCard &&
                activeGroup.pageNames.map((pageName, pi) => {
                  return (
                    <View key={pi} style={[styles.pageRow, pi > 0 && styles.pageRowBorder, { borderColor: theme.colors.outlineVariant }]}>
                      <Text style={[styles.pageLabel, { color: theme.colors.onSurfaceVariant }]}>
                        {pageName}
                      </Text>
                      <Text variant="headlineMedium" style={[styles.cardText, { color: pi === 0 ? theme.colors.primary : theme.colors.onSurface }]}>
                        {currentCard.pages[pi] || '—'}
                      </Text>
                    </View>
                  );
                })}
            </Animated.View>
          </Animated.View>
        </Pressable>
      </View>

      {/* Bottom control zone */}
      <View style={[styles.bottomControlZone, { height: hasTts || hasStt ? 180 : 80 }]}>
        {s.showRatingButtons ? (
          <View style={styles.ratingButtonsRow}>
            <Button
              mode="outlined"
              textColor="#f44336"
              style={[styles.rateBtn, { borderColor: '#f44336' }]}
              onPress={() => handleRating(1)}
            >
              {t('study.rating.1')}
            </Button>
            <Button
              mode="outlined"
              textColor="#ff9800"
              style={[styles.rateBtn, { borderColor: '#ff9800' }]}
              onPress={() => handleRating(2)}
            >
              {t('study.rating.2')}
            </Button>
            <Button mode="contained" style={styles.rateBtn} onPress={() => handleRating(3)}>
              {t('study.rating.3')}
            </Button>
            <Button
              mode="contained"
              buttonColor="#4caf50"
              textColor="#ffffff"
              style={styles.rateBtn}
              onPress={() => handleRating(4)}
            >
              {t('study.rating.4')}
            </Button>
          </View>
        ) : hasTts || hasStt ? (
          <View style={styles.feedbackContainer}>
            {/* Recognized voice display */}
            <View style={styles.sttTextWrapper}>
              {s.sttResultText ? (
                <View style={[styles.sttResultBox, { backgroundColor: theme.colors.surfaceVariant }]}>
                  <Text variant="labelSmall" style={{ color: theme.colors.onSurfaceVariant }}>
                    {t('study.recognized')}
                  </Text>
                  <Text variant="bodyLarge" style={{ fontWeight: 'bold', color: theme.colors.onSurface }}>
                    {s.sttResultText}
                  </Text>
                  {s.sttMatchPercent > 0 && (
                    <Text variant="labelSmall" style={{ color: matchColor, fontWeight: 'bold', marginTop: 4 }}>
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
                  <MaterialCommunityIcons
                    name="volume-high"
                    size={36}
                    color={s.isTtsPlaying ? theme.colors.primary : theme.colors.outline}
                  />
                </Animated.View>
              )}
              {hasStt && (
                <Animated.View style={[styles.iconWrapper, sttIconStyle]}>
                  <MaterialCommunityIcons
                    name="microphone"
                    size={36}
                    color={s.isSttListening ? '#f44336' : theme.colors.outline}
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
    height: 6,
    borderRadius: 3,
  },
  progressCounter: {
    fontSize: 12,
    textAlign: 'right',
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
    borderRadius: 24,
    borderWidth: 1,
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 12,
    padding: 24,
    maxWidth: 600,
    width: '100%',
    alignSelf: 'center',
  },
  cardSide: {
    flex: 1,
    justifyContent: 'center',
    width: '100%',
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
    fontSize: 12,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    marginBottom: 8,
    fontWeight: '600',
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
    bottom: 0,
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
    borderRadius: 12,
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
