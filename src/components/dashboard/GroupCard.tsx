import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Card, Text, IconButton, useTheme } from 'react-native-paper';
import { router } from 'expo-router';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useI18n } from '../../i18n';
import { SegmentedProgressBar } from '../SegmentedProgressBar';
import { computeCardStats } from '../../store/selectors/stats';
import { StudyModeMenuButton } from './StudyModeMenuButton';
import type { FlashcardGroup } from '../../types/models';

interface Props {
  group: FlashcardGroup;
  cardWidth: number;
  onModeChange: (modeId: string) => void;
}

export function GroupCard({ group, cardWidth, onModeChange }: Props) {
  const store = useFlashcardStore();
  const { t } = useI18n();
  const theme = useTheme();

  const dueCount = store.getDueCards(group.id).length;
  const cardStats = computeCardStats(group.cards);

  return (
    <Card
      style={[styles.card, { width: cardWidth }]}
      mode="elevated"
      onPress={() => router.push(`/study/${group.id}`)}
      accessibilityLabel={`Deck ${group.name}, contains ${group.cards.length} cards`}
    >
      <Card.Content>
        <Text variant="titleMedium" style={styles.cardTitle}>
          {group.name}
        </Text>
        <Text
          variant="bodyMedium"
          style={{ color: theme.colors.onSurfaceVariant, marginBottom: 12 }}
        >
          {t('dashboard.cards_count', { count: group.cards.length })}
        </Text>
        <SegmentedProgressBar stats={cardStats} />
        {dueCount > 0 && (
          <Text variant="bodyMedium" style={[styles.dueText, { color: theme.colors.error }]}>
            {t('dashboard.due_count', { count: dueCount })}
          </Text>
        )}
      </Card.Content>
      <Card.Actions style={styles.cardActions}>
        <View style={styles.cardActionsRow}>
          <IconButton
            icon="eye-outline"
            size={20}
            iconColor={theme.colors.primary}
            onPress={() => router.push(`/browse/${group.id}`)}
            accessibilityLabel={`Browse cards in deck ${group.name}`}
          />
          <IconButton
            icon="tune"
            size={20}
            iconColor={theme.colors.primary}
            onPress={() => router.push(`/settings/${group.id}`)}
            accessibilityLabel={`Configure settings for deck ${group.name}`}
          />
          <View style={{ flex: 1 }}>
            <StudyModeMenuButton
              group={group}
              onStudy={() => router.push(`/study/${group.id}`)}
              onModeChange={onModeChange}
            />
          </View>
        </View>
      </Card.Actions>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 24,
    marginBottom: 8,
  },
  cardTitle: {
    fontWeight: 'bold',
    marginBottom: 4,
  },
  dueText: {
    fontWeight: '500',
    marginTop: 8,
  },
  cardActions: {
    paddingHorizontal: 8,
    paddingBottom: 8,
  },
  cardActionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
    gap: 4,
  },
});
