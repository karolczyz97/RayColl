import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import { AppIcon } from '../AppIcon';
import { SectionCard } from '../layout/SectionCard';
import { TOKENS } from '../../theme/tokens';

export interface MetricItem {
  icon: string;
  label: string;
  value: string;
  color?: string;
}

interface MetricCardProps {
  item: MetricItem;
}

export function MetricCard({ item }: MetricCardProps) {
  const theme = useTheme();
  const color = item.color ?? theme.colors.primary;

  return (
    <SectionCard>
      <View style={styles.content}>
        <AppIcon name={item.icon} size={20} color={color} style={styles.icon} />
        <Text variant="labelSmall" style={[styles.label, { color: theme.colors.onSurfaceVariant }]}>
          {item.label}
        </Text>
        <Text variant="titleMedium" style={[styles.value, { color }]}>
          {item.value}
        </Text>
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  content: {
    alignItems: 'center',
    gap: TOKENS.spacing.xs,
    paddingVertical: TOKENS.spacing.sm,
  },
  icon: {
    marginBottom: TOKENS.spacing.xxs,
  },
  label: {
    textAlign: 'center',
  },
  value: {
    fontWeight: TOKENS.typography.weight.bold,
    fontVariant: ['tabular-nums'],
  },
});
