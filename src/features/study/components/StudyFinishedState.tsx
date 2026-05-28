import React from 'react';
import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Button, Text, useTheme } from 'react-native-paper';
import Animated, { FadeInUp, ZoomIn } from 'react-native-reanimated';
import type { FlashcardGroup } from '../../../types/models';
import { AppIcon } from '../../../components/AppIcon';
import { SegmentedProgressBar } from '../../../components/SegmentedProgressBar';
import { computeCardStats } from '../../../store/selectors/stats';
import { getSuccessColor } from '../../../theme/semanticColors';
import { TOKENS } from '../../../theme/tokens';

interface StudyFinishedStateProps {
  activeGroup: FlashcardGroup;
  failedCount: number;
  dueCardsCount: number;
  bravoLabel: string;
  noDueLabel: string;
  finishedDescription: string;
  deckProgressLabel: string;
  restartFailedLabel: string;
  restartSessionLabel: string;
  backToPanelLabel: string;
  onRestartFailed: () => void;
  onRestartSession: () => void;
  onBack: () => void;
}

export function StudyFinishedState({
  activeGroup,
  failedCount,
  dueCardsCount,
  bravoLabel,
  noDueLabel,
  finishedDescription,
  deckProgressLabel,
  restartFailedLabel,
  restartSessionLabel,
  backToPanelLabel,
  onRestartFailed,
  onRestartSession,
  onBack,
}: StudyFinishedStateProps) {
  const theme = useTheme();

  return (
    <SafeAreaView
      edges={['top', 'left', 'right', 'bottom']}
      style={[styles.finishedContainer, { backgroundColor: theme.colors.background }]}
    >
      <Animated.View entering={ZoomIn.springify().damping(12)}>
        <AppIcon name="check-circle" size={96} color={getSuccessColor(theme)} />
      </Animated.View>
      <Animated.View entering={FadeInUp.springify().delay(150)}>
        <Text variant="headlineLarge" style={styles.bravoTitle}>
          {bravoLabel}
        </Text>
      </Animated.View>
      <Animated.View entering={FadeInUp.springify().delay(250)}>
        <Text variant="bodyLarge" style={[styles.finishedDescription, { color: theme.colors.onSurfaceVariant }]}>
          {dueCardsCount === 0 ? noDueLabel : finishedDescription}
        </Text>
      </Animated.View>

      <Animated.View entering={FadeInUp.springify().delay(350)} style={styles.statsSummary}>
        <Text variant="titleMedium" style={styles.statsTitle}>
          {deckProgressLabel}
        </Text>
        <SegmentedProgressBar stats={computeCardStats(activeGroup.cards)} showLegend />
      </Animated.View>

      <Animated.View entering={FadeInUp.springify().delay(450)} style={styles.actionButtons}>
        {failedCount > 0 ? (
          <Button mode="contained" buttonColor={theme.colors.error} onPress={onRestartFailed} style={styles.actionButton}>
            {restartFailedLabel} ({failedCount})
          </Button>
        ) : null}
        <Button mode="contained" onPress={onRestartSession} style={styles.actionButton}>
          {restartSessionLabel}
        </Button>
        <Button mode="outlined" onPress={onBack} style={styles.actionButton}>
          {backToPanelLabel}
        </Button>
      </Animated.View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  finishedContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: TOKENS.spacing.xl,
  },
  bravoTitle: {
    fontWeight: 'bold',
    marginTop: TOKENS.spacing.lg,
    marginBottom: TOKENS.spacing.sm,
  },
  finishedDescription: {
    textAlign: 'center',
    marginBottom: TOKENS.spacing.xxl,
  },
  statsSummary: {
    width: '100%',
    maxWidth: 400,
    marginBottom: TOKENS.spacing.xxl,
  },
  statsTitle: {
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: TOKENS.spacing.lg,
  },
  actionButtons: {
    width: '100%',
    maxWidth: 320,
    gap: TOKENS.spacing.md,
  },
  actionButton: {
    borderRadius: 100,
  },
});
