import React, { Fragment } from 'react';
import { View, StyleSheet, Platform } from 'react-native';
import { Text, IconButton, Button, useTheme } from 'react-native-paper';
import { router } from 'expo-router';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';
import { AppCard } from '@/components/AppCard';
import { SegmentedProgressBar } from '@/components/SegmentedProgressBar';
import { computeCardStats } from '@/store/selectors/stats';
import { ROUTES } from '@/constants/routes';
import { StudyModeMenuButton } from './StudyModeMenuButton';
import type { FlashcardGroup } from '@/types/models';
import { TOKENS } from '@/theme/tokens';
import { ensureWebMicrophonePermission } from '@/services/sttExpo';

interface GroupCardProps {
  group: FlashcardGroup;
  onModeChange?: (modeId: string) => void;
  /** 'default' renders the dashboard deck card; 'archived' renders the archive variant. */
  variant?: 'default' | 'archived';
  /** Archive only: pre-formatted auto-delete countdown line. */
  expiryLabel?: string;
  /** Archive only: restore the deck to the main list. */
  onRestore?: () => void;
  /** Archive only: permanently delete the deck (tombstone). */
  onDeletePermanently?: () => void;
}

export function GroupCard({
  group,
  onModeChange,
  variant = 'default',
  expiryLabel,
  onRestore,
  onDeletePermanently,
}: GroupCardProps) {
  const store = useFlashcardStore();
  const { t } = useI18n();
  const theme = useTheme();

  const isArchived = variant === 'archived';
  const cardStats = computeCardStats(group.cards);
  const dueCount = isArchived ? 0 : store.getDueCards(group.id).length;

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
        <View style={styles.subtitleRow}>
          <Text
            variant="bodyMedium"
            style={[styles.cardSubtitle, { color: theme.colors.onSurfaceVariant }]}
          >
            {isArchived
              ? t('archive.cards_count', { count: group.cards.length })
              : t('dashboard.cards_count', { count: group.cards.length })}
          </Text>
          {!isArchived && dueCount > 0 && (
            <Fragment>
              <Text variant="bodyMedium" style={[styles.cardSubtitle, { color: theme.colors.onSurfaceVariant }]}>
                {' • '}
              </Text>
              <Text variant="bodyMedium" style={[styles.cardSubtitle, { color: theme.colors.error }]}>
                {t('dashboard.due_count', { count: dueCount })}
              </Text>
            </Fragment>
          )}
        </View>
        <SegmentedProgressBar stats={cardStats} />
        {isArchived && expiryLabel ? (
          <Text
            variant="bodySmall"
            style={[styles.expiryLabel, { color: theme.colors.onSurfaceVariant }]}
          >
            {expiryLabel}
          </Text>
        ) : null}
      </AppCard.Content>
      <AppCard.Actions style={styles.cardActions}>
        {isArchived ? (
          <View style={styles.archivedActionsRow}>
            <Button
              icon="restore"
              onPress={onRestore}
              accessibilityLabel={`${t('btn.restore')} ${group.name}`}
            >
              {t('btn.restore')}
            </Button>
            <Button
              icon="delete-outline"
              textColor={theme.colors.error}
              onPress={onDeletePermanently}
              accessibilityLabel={`${t('archive.delete_permanently')} ${group.name}`}
            >
              {t('archive.delete_permanently')}
            </Button>
          </View>
        ) : (
          <View style={styles.cardActionsRow}>
            <IconButton
              icon="eye-outline"
              size={TOKENS.iconSize.sm}
              iconColor={theme.colors.primary}
              onPress={() => router.push(ROUTES.browseDeck(group.id))}
              accessibilityLabel={`Browse cards in deck ${group.name}`}
            />
            <IconButton
              icon="tune"
              size={TOKENS.iconSize.sm}
              iconColor={theme.colors.primary}
              onPress={() => router.push(ROUTES.deckSettings(group.id))}
              accessibilityLabel={`Configure settings for deck ${group.name}`}
            />
            <View style={styles.studyModeAction}>
              <StudyModeMenuButton
                group={group}
                onStudy={async () => {
                  if (Platform.OS === 'web') {
                    const activeMode = store.studyModes.find((m) => m.id === group.activeModeId);
                    const hasStt = activeMode?.steps.some((step) => step.type === 'listen_and_branch');
                    if (hasStt) {
                      await ensureWebMicrophonePermission();
                    }
                  }
                  router.push(ROUTES.studyDeck(group.id));
                }}
                onModeChange={(modeId) => onModeChange?.(modeId)}
              />
            </View>
          </View>
        )}
      </AppCard.Actions>
    </AppCard>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: TOKENS.radius.xl,
  },
  cardTitle: {
    fontWeight: TOKENS.typography.weight.bold,
    marginBottom: TOKENS.spacing.xs,
  },
  subtitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: TOKENS.spacing.md,
  },
  cardSubtitle: {
    fontWeight: TOKENS.typography.weight.medium,
  },
  expiryLabel: {
    marginTop: TOKENS.spacing.sm,
  },
  cardActions: {
    paddingHorizontal: TOKENS.spacing.sm,
    paddingVertical: TOKENS.spacing.sm,
  },
  cardActionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
    gap: TOKENS.spacing.xs,
  },
  archivedActionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    width: '100%',
    gap: TOKENS.spacing.xs,
  },
  studyModeAction: {
    flex: 1,
    maxWidth: 210,
    marginLeft: 'auto',
  },
});
