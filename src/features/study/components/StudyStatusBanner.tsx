import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import type { SessionProgressItem } from '../../../components/SegmentedProgressBar';
import { SegmentedProgressBar } from '../../../components/SegmentedProgressBar';
import { PageHeader } from '../../../components/PageHeader';
import { TOKENS } from '../../../theme/tokens';

interface StudyStatusBannerProps {
  title: string;
  progressLabel: string;
  onBack: () => void;
  sessionItems: SessionProgressItem[];
  currentIndex: number;
}

export function StudyStatusBanner({
  title,
  progressLabel,
  onBack,
  sessionItems,
  currentIndex,
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
  progressBar: {
    height: 8,
    borderRadius: TOKENS.radius.xs,
  },
  progressCounter: {
    fontSize: TOKENS.typography.size.xs,
    textAlign: 'right',
    fontWeight: '600',
    letterSpacing: 0.5,
  },
});
