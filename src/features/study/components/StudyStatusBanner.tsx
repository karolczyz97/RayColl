import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import { PageHeader } from '../../../components/PageHeader';
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
      <PageHeader title={title} onBack={onBack} />
      <View style={styles.progressBarWrapper}>
        <SegmentedProgressBar
          mode="session"
          items={sessionItems}
          currentIndex={currentIndex}
          height={8}
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
    width: '100%',
  },
  progressCounter: {
    fontSize: TOKENS.typography.size.xs,
    textAlign: 'right',
    fontWeight: '600',
    letterSpacing: 0.5,
  },
});
