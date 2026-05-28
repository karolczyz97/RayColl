import React from 'react';
import { StyleSheet } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import { AppCard } from '../AppCard';
import { TOKENS } from '../../theme/tokens';

interface SectionCardProps {
  title?: string;
  children: React.ReactNode;
  mode?: 'elevated' | 'outlined' | 'contained';
  danger?: boolean;
  actions?: React.ReactNode;
  titleAlign?: 'left' | 'center';
}

export function SectionCard({
  title,
  children,
  mode = 'elevated',
  danger = false,
  actions,
  titleAlign = 'left',
}: SectionCardProps) {
  const theme = useTheme();

  return (
    <AppCard mode={mode} style={[styles.card, danger && { borderColor: theme.colors.error }]}>
      <AppCard.Content style={styles.content}>
        {title ? (
          <Text
            variant="titleMedium"
            style={[
              styles.title,
              titleAlign === 'center' && styles.centerTitle,
              danger && { color: theme.colors.error },
            ]}
          >
            {title}
          </Text>
        ) : null}
        {children}
      </AppCard.Content>
      {actions ? <AppCard.Actions>{actions}</AppCard.Actions> : null}
    </AppCard>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: TOKENS.radius.xl,
  },
  content: {
    gap: TOKENS.spacing.md,
    paddingBottom: TOKENS.spacing.lg,
  },
  title: {
    fontWeight: '700',
    marginBottom: TOKENS.spacing.xs,
  },
  centerTitle: {
    textAlign: 'center',
  },
});
