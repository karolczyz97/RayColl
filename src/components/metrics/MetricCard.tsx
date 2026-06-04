import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import { AppIcon } from '@/components/AppIcon';
import { SectionCard } from '@/components/layout/SectionCard';
import { TOKENS } from '@/theme/tokens';

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
        <Text variant="labelSmall" style={[styles.label, { color: theme.colors.onSurfaceVariant }]}>
          {item.label}
        </Text>
        <View style={styles.bottomRow}>
          <Text variant="titleMedium" style={styles.value}>
            {item.value}
          </Text>
          <AppIcon name={item.icon} size={TOKENS.iconSize.sm} color={color} />
        </View>
      </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  content: {
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: TOKENS.spacing.xs,
    paddingVertical: TOKENS.spacing.sm,
  },
  bottomRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.xs,
  },
  label: {
    width: '100%',
    textAlign: 'center',
  },
  value: {
    fontWeight: TOKENS.typography.weight.bold,
    fontVariant: ['tabular-nums'],
  },
});
