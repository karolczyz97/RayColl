import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, Text, useTheme } from 'react-native-paper';
import Animated, { FadeInUp, ZoomIn } from 'react-native-reanimated';
import type { FlashcardGroup } from '@/types/models';
import { useI18n } from '@/i18n';
import { AppIcon } from '@/components/AppIcon';
import { SegmentedProgressBar } from '@/components/SegmentedProgressBar';
import { computeCardStats } from '@/store/selectors/stats';
import { getSuccessColor } from '@/theme/semanticColors';
import { TOKENS } from '@/theme/tokens';

interface StudyFinishedStateProps {
  activeGroup: FlashcardGroup;
  /** True when the user left mid-session (vs reviewing every due card). */
  endedEarly?: boolean;
  failedCount: number;
  dueCardsCount: number;
  onRestartFailed: () => void;
  onRestartSession: () => void;
  onBack: () => void;
}

export function StudyFinishedState({
  activeGroup,
  endedEarly = false,
  failedCount,
  dueCardsCount,
  onRestartFailed,
  onRestartSession,
  onBack,
}: StudyFinishedStateProps) {
  const theme = useTheme();
  const { t } = useI18n();

  return (
    <View style={styles.finishedContainer}>
      <Animated.View entering={ZoomIn.springify().damping(12)}>
        <AppIcon name="check-circle" size={TOKENS.iconSize.display} color={getSuccessColor(theme)} />
      </Animated.View>
      <Animated.View entering={FadeInUp.springify().delay(150)}>
        <Text variant="headlineLarge" style={styles.bravoTitle}>
          {endedEarly ? t('study.session_ended_title') : t('study.bravo')}
        </Text>
      </Animated.View>
      <Animated.View entering={FadeInUp.springify().delay(250)}>
        <Text variant="bodyLarge" style={[styles.finishedDescription, { color: theme.colors.onSurfaceVariant }]}>
          {dueCardsCount === 0
            ? t('study.no_due')
            : endedEarly
              ? t('study.session_ended_desc')
              : t('study.finished_desc')}
        </Text>
      </Animated.View>

      <Animated.View entering={FadeInUp.springify().delay(350)} style={styles.statsSummary}>
        <Text variant="titleMedium" style={styles.statsTitle}>
          {t('stats.deck_progress')}
        </Text>
        <SegmentedProgressBar stats={computeCardStats(activeGroup.cards)} showLegend />
      </Animated.View>

      <Animated.View entering={FadeInUp.springify().delay(450)} style={styles.actionButtons}>
        {failedCount > 0 ? (
          <Button mode="contained" buttonColor={theme.colors.error} onPress={onRestartFailed} style={styles.actionButton}>
            {t('study.restart_failed')} ({failedCount})
          </Button>
        ) : null}
        <Button mode="contained" onPress={onRestartSession} style={styles.actionButton}>
          {t('study.restart_session')}
        </Button>
        <Button mode="outlined" onPress={onBack} style={styles.actionButton}>
          {t('study.back_to_panel')}
        </Button>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  finishedContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: TOKENS.spacing.xl,
  },
  bravoTitle: {
    fontWeight: TOKENS.typography.weight.bold,
    marginTop: TOKENS.spacing.lg,
    marginBottom: TOKENS.spacing.sm,
  },
  finishedDescription: {
    textAlign: 'center',
    marginBottom: TOKENS.spacing.xxl,
  },
  statsSummary: {
    width: '100%',
    maxWidth: TOKENS.layout.studyCardMaxWidth,
    marginBottom: TOKENS.spacing.xxl,
  },
  statsTitle: {
    fontWeight: TOKENS.typography.weight.bold,
    textAlign: 'center',
    marginBottom: TOKENS.spacing.lg,
  },
  actionButtons: {
    width: '100%',
    maxWidth: TOKENS.layout.actionMaxWidth,
    gap: TOKENS.spacing.md,
  },
  actionButton: {
    borderRadius: TOKENS.radius.pill,
  },
});
