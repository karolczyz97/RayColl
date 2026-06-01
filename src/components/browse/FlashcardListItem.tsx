import React, { useState } from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, Chip, IconButton, useTheme, Switch, MD3Theme } from 'react-native-paper';
import type { Flashcard, FlashcardGroup, SrsState } from '../../types/models';
import { getCardCategory } from '../../srs/srsEngine';
import { AppIcon } from '../../components/AppIcon';
import { AppCard } from '../../components/AppCard';
import {
  SRS_CATEGORIES_TOKENS,
  getMasteryPercent,
  getDaysUntilReview,
} from '../../theme/srsTokens';
import { TOKENS } from '../../theme/tokens';
import { getVisiblePages } from '../../store/selectors/pages';
import { getReviewStatusColor } from '../../theme/semanticColors';
import type { TranslationFn } from '../../i18n';

interface Props {
  card: Flashcard;
  group: FlashcardGroup;
  onStartEdit: () => void;
  onDelete: () => void;
  t: TranslationFn;
  readOnly?: boolean;
}

function srsChip(
  state: SrsState,
  theme: MD3Theme,
  t: (key: string) => string,
): { text: string; color: string; bg: string } {
  const category = getCardCategory(state);
  const token = SRS_CATEGORIES_TOKENS[category];
  const srsColors = getReviewStatusColor(theme, category);
  return {
    text: t(token.labelKey),
    color: srsColors.color,
    bg: srsColors.bg,
  };
}

function daysUntilReview(state: SrsState, t: (key: string) => string): string {
  if (state.state === 0) return t('srs.badge.new');
  const days = getDaysUntilReview(state);
  if (days <= 0) return t('srs.badge.now');
  return `${days}d`;
}

export function FlashcardListItem({
  card,
  group,
  onStartEdit,
  onDelete,
  t,
  readOnly,
}: Props) {
  const theme = useTheme();
  const [viewHidden, setViewHidden] = useState(false);

  const activeCount = group.activePageCount;
  const totalCount = card.pages.length;
  const hasHiddenPages = totalCount > activeCount;

  const srs = srsChip(card.srsState, theme, t);
  const mastery = getMasteryPercent(card.srsState);
  const reviewIn = daysUntilReview(card.srsState, t);

  const visiblePages = getVisiblePages(card, group);

  const displayPages = viewHidden ? card.pages : visiblePages;

  const toggleLabel =
    t('browse.show_hidden_pages') === 'browse.show_hidden_pages'
      ? 'Show hidden pages'
      : t('browse.show_hidden_pages');

  return (
    <AppCard style={styles.card} mode="elevated">
      <AppCard.Content style={styles.cardContent}>
        {displayPages.map((page, i) => {
          const isHidden = i >= activeCount;
          return (
            <Text
              key={i}
              variant="bodyMedium"
              style={[styles.pageLine, { color: isHidden ? theme.colors.onSurfaceVariant : theme.colors.onSurface }]}
            >
              {page || '—'}
            </Text>
          );
        })}

        {hasHiddenPages && (
          <View style={[styles.toggleRow, { borderTopColor: theme.colors.outlineVariant }]}>
            <Text variant="labelSmall" style={{ color: theme.colors.outline }}>
              {toggleLabel}
            </Text>
            <Switch
              value={viewHidden}
              onValueChange={setViewHidden}
              style={styles.switch}
              accessibilityLabel="Toggle viewing hidden pages"
            />
          </View>
        )}
      </AppCard.Content>
      <AppCard.Actions style={styles.cardActions}>
        <View style={styles.cardActionsLeft}>
          <Chip
            style={{ backgroundColor: srs.bg }}
            textStyle={{ color: theme.colors.onSurface }}
          >
            {srs.text}
          </Chip>
          {card.srsState.state > 0 && (
            <View style={styles.statsIconsRow}>
              <View style={styles.statIconItem}>
                <AppIcon name="thumb-up-outline" size={14} color={theme.colors.outline} />
                <Text variant="labelSmall" style={{ color: theme.colors.outline }}>
                  {mastery}%
                </Text>
              </View>
              <View style={styles.statIconItem}>
                <AppIcon name="sync" size={14} color={theme.colors.outline} />
                <Text variant="labelSmall" style={{ color: theme.colors.outline }}>
                  {card.srsState.repetitions}
                </Text>
              </View>
              <View style={styles.statIconItem}>
                <AppIcon name="calendar-month-outline" size={14} color={theme.colors.outline} />
                <Text variant="labelSmall" style={{ color: theme.colors.outline }}>
                  {reviewIn}
                </Text>
              </View>
            </View>
          )}
        </View>
        {!readOnly && (
          <View style={styles.cardActionsRight}>
            <IconButton
              icon="pencil"
              size={20}
              iconColor={theme.colors.primary}
              onPress={onStartEdit}
              accessibilityLabel="Edit card"
            />
            <IconButton
              icon="delete"
              size={20}
              iconColor={theme.colors.error}
              onPress={onDelete}
              accessibilityLabel="Delete card"
            />
          </View>
        )}
      </AppCard.Actions>
    </AppCard>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: TOKENS.radius.xl,
    overflow: 'hidden',
  },
  cardContent: {
    paddingTop: TOKENS.spacing.md,
    paddingBottom: TOKENS.spacing.xs,
    gap: TOKENS.spacing.sm,
  },
  pageLine: {
    flexWrap: 'wrap',
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: TOKENS.spacing.xs,
    borderTopWidth: StyleSheet.hairlineWidth,
    paddingTop: TOKENS.spacing.xs,
  },
  switch: {
    transform: [{ scaleX: 0.8 }, { scaleY: 0.8 }],
  },
  cardActions: {
    paddingHorizontal: TOKENS.spacing.md,
    paddingVertical: TOKENS.spacing.xs,
    justifyContent: 'space-between',
  },
  cardActionsLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.md,
  },
  statsIconsRow: {
    flexDirection: 'row',
    gap: TOKENS.spacing.sm,
  },
  statIconItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.xxs,
  },
  cardActionsRight: {
    flexDirection: 'row',
  },
});
