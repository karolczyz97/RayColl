import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import { AppTopBar } from '../../../components/layout/AppTopBar';
import { SegmentedProgressBar } from '../../../components/SegmentedProgressBar';
import type { SessionProgressItem } from '../session/sessionProgress';
import { TOKENS } from '../../../theme/tokens';

interface StudyStatusBannerProps {
  title: string;
  sessionItems: SessionProgressItem[];
  currentIndex: number;
  progressLabel: string;
  onBack: () => void;
}

export function StudyStatusBanner({
  title,
  sessionItems,
  currentIndex,
  progressLabel,
  onBack,
}: StudyStatusBannerProps) {
  const theme = useTheme();

  return (
    <View style={styles.header}>
      <AppTopBar title={title} onBack={onBack} />
      <View style={styles.progressBarWrapper}>
        <SegmentedProgressBar
          mode="session"
          items={sessionItems}
          currentIndex={currentIndex}
          height={TOKENS.control.progressHeight}
        />
      </View>
      <Text style={[styles.progressCounter, { color: theme.colors.onSurfaceVariant }]}>
        {progressLabel}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    marginBottom: TOKENS.spacing.lg,
  },
  progressBarWrapper: {
    marginVertical: TOKENS.spacing.md,
    paddingHorizontal: TOKENS.spacing.lg,
  },
  progressCounter: {
    fontSize: TOKENS.typography.size.xs,
    textAlign: 'right',
    fontWeight: TOKENS.typography.weight.semibold,
    letterSpacing: 0,
    paddingHorizontal: TOKENS.spacing.lg,
  },
});
