import React from 'react';
import { StyleSheet, View } from 'react-native';
import { ProgressBar, Text, useTheme } from 'react-native-paper';
import { PageHeader } from '../../../components/PageHeader';

interface StudyStatusBannerProps {
  title: string;
  progress: number;
  progressLabel: string;
  onBack: () => void;
}

export function StudyStatusBanner({
  title,
  progress,
  progressLabel,
  onBack,
}: StudyStatusBannerProps) {
  const theme = useTheme();

  return (
    <View style={styles.header}>
      <PageHeader title={title} onBack={onBack} />
      <View style={styles.progressBarWrapper}>
        <ProgressBar progress={progress} color={theme.colors.primary} style={styles.progressBar} />
      </View>
      <Text style={[styles.progressCounter, { color: theme.colors.onSurfaceVariant }]}>
        {progressLabel}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
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
});
