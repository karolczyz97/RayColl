import React from 'react';
import { View, StyleSheet, useWindowDimensions } from 'react-native';
import { Card, Text, useTheme } from 'react-native-paper';
import { AppIcon } from '../AppIcon';
import { useI18n } from '../../i18n';
import { getWarningColor } from '../../theme/semanticColors';

interface Props {
  decksCount: number;
  cardsCount: number;
  dueCount: number;
  streak: number;
}

export function DashboardStats({ decksCount, cardsCount, dueCount, streak }: Props) {
  const { t } = useI18n();
  const theme = useTheme();
  const { width } = useWindowDimensions();
  const isNarrow = width < 480;

  const stats = [
    {
      icon: 'book-multiple',
      label: t('stats.decks_title') || 'Talie',
      value: String(decksCount),
      color: theme.colors.primary,
    },
    {
      icon: 'card-multiple-outline',
      label: t('stats.cards_title') || 'Fiszki',
      value: String(cardsCount),
      color: theme.colors.secondary,
    },
    {
      icon: 'sync',
      label: t('stats.due_cards'),
      value: String(dueCount),
      color: dueCount > 0 ? theme.colors.error : theme.colors.outline,
    },
    {
      icon: 'fire',
      label: t('stats.streak'),
      value: `${streak} \u{1F525}`,
      color: getWarningColor(theme),
    },
  ];

  return (
    <View style={styles.statsContainer}>
      {stats.map((s, idx) => (
        <Card
          key={idx}
          style={[
            styles.statCard,
            {
              width: isNarrow ? '47%' : '23%',
              backgroundColor: theme.colors.surfaceVariant + '33', // 20% opacity
            },
          ]}
          mode="outlined"
        >
          <Card.Content style={styles.statContent}>
            <AppIcon name={s.icon} size={20} color={s.color} style={styles.icon} />
            <Text
              variant="labelSmall"
              style={[styles.label, { color: theme.colors.onSurfaceVariant }]}
            >
              {s.label}
            </Text>
            <Text variant="titleMedium" style={[styles.value, { color: s.color }]}>
              {s.value}
            </Text>
          </Card.Content>
        </Card>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  statsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingBottom: 16,
    gap: 8,
  },
  statCard: {
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'transparent',
  },
  statContent: {
    alignItems: 'center',
    paddingVertical: 8,
    paddingHorizontal: 4,
  },
  icon: {
    marginBottom: 2,
  },
  label: {
    fontSize: 10,
    textAlign: 'center',
  },
  value: {
    fontWeight: 'bold',
    fontSize: 14,
  },
});
