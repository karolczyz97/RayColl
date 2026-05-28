import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, IconButton, useTheme } from 'react-native-paper';
import { router } from 'expo-router';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useI18n } from '../../i18n';
import { AppCard } from '../AppCard';
import { SegmentedProgressBar } from '../SegmentedProgressBar';
import { computeCardStats } from '../../store/selectors/stats';
import { ROUTES } from '../../constants/routes';
import { StudyModeMenuButton } from './StudyModeMenuButton';
import type { FlashcardGroup } from '../../types/models';
import { TOKENS } from '../../theme/tokens';

interface Props {
  group: FlashcardGroup;
  onModeChange: (modeId: string) => void;
}

export function GroupCard({ group, onModeChange }: Props) {
  const store = useFlashcardStore();
  const { t } = useI18n();
  const theme = useTheme();

  const dueCount = store.getDueCards(group.id).length;
  const cardStats = computeCardStats(group.cards);

  return (
    <AppCard
      style={styles.card}
      mode="elevated"
      accessibilityLabel={`Deck ${group.name}, contains ${group.cards.length} cards`}
    >
      <AppCard.Content>
        <Text variant="titleMedium" style={styles.cardTitle}>
          {group.name}
        </Text>
        <Text
          variant="bodyMedium"
          style={[styles.cardSubtitle, { color: theme.colors.onSurfaceVariant }]}
        >
          {t('dashboard.cards_count', { count: group.cards.length })}
        </Text>
        <SegmentedProgressBar stats={cardStats} />
        {dueCount > 0 && (
          <Text variant="bodyMedium" style={[styles.dueText, { color: theme.colors.error }]}>
            {t('dashboard.due_count', { count: dueCount })}
          </Text>
        )}
      </AppCard.Content>
      <AppCard.Actions style={styles.cardActions}>
        <View style={styles.cardActionsRow}>
          <IconButton
            icon="eye-outline"
            size={20}
            iconColor={theme.colors.primary}
            onPress={() => router.push(ROUTES.browseDeck(group.id))}
            accessibilityLabel={`Browse cards in deck ${group.name}`}
          />
          <IconButton
            icon="tune"
            size={20}
            iconColor={theme.colors.primary}
            onPress={() => router.push(ROUTES.deckSettings(group.id))}
            accessibilityLabel={`Configure settings for deck ${group.name}`}
          />
          <View style={styles.studyModeAction}>
            <StudyModeMenuButton
              group={group}
              onStudy={() => router.push(ROUTES.studyDeck(group.id))}
              onModeChange={onModeChange}
            />
          </View>
        </View>
      </AppCard.Actions>
    </AppCard>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: TOKENS.radius.xl,
    marginBottom: TOKENS.spacing.sm,
  },
  cardTitle: {
    fontWeight: 'bold',
    marginBottom: TOKENS.spacing.xs,
  },
  cardSubtitle: {
    marginBottom: TOKENS.spacing.md,
  },
  dueText: {
    fontWeight: '500',
    marginTop: TOKENS.spacing.sm,
  },
  cardActions: {
    paddingHorizontal: TOKENS.spacing.sm,
    paddingBottom: TOKENS.spacing.sm,
  },
  cardActionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
    gap: TOKENS.spacing.xs,
  },
  studyModeAction: {
    flex: 1,
  },
});
