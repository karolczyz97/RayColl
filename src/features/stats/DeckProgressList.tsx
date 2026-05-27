import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';
import type { FlashcardGroup } from '../../types/models';
import { computeCardStats } from '../../store/selectors/stats';
import { SegmentedProgressBar } from '../../components/SegmentedProgressBar';
import { TOKENS } from '../../theme/tokens';

interface DeckProgressListProps {
  groups: FlashcardGroup[];
  t: TranslationFn;
}

export function DeckProgressList({ groups, t }: DeckProgressListProps) {
  const theme = useTheme();

  return (
    <>
      {groups.map((group) => {
        const groupStats = computeCardStats(group.cards);

        return (
          <View key={group.id} style={styles.deckRow}>
            <View style={styles.deckRowHeader}>
              <Text variant="bodyMedium" style={styles.deckName}>
                {group.name}
              </Text>
              <Text variant="bodySmall" style={{ color: theme.colors.onSurfaceVariant }}>
                {t('stats.cards_count', { count: group.cards.length })}
              </Text>
            </View>
            <SegmentedProgressBar stats={groupStats} />
          </View>
        );
      })}
    </>
  );
}

const styles = StyleSheet.create({
  deckRow: {
    marginBottom: TOKENS.spacing.lg,
  },
  deckRowHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: TOKENS.spacing.xs,
  },
  deckName: {
    fontWeight: TOKENS.typography.weight.bold,
  },
});
