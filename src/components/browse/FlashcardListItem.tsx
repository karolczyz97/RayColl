import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, IconButton, useTheme, Switch, MD3Theme } from 'react-native-paper';
import type { Flashcard, FlashcardGroup, SrsState } from '@/types/models';
import { getCardCategory } from '@/srs/srsEngine';
import { AppIcon } from '@/components/AppIcon';
import { AppCard } from '@/components/AppCard';
import { useHiddenPagesToggle } from '@/hooks/useHiddenPagesToggle';
import { SRS_CATEGORIES_TOKENS, getMasteryPercent, getDaysUntilReview } from '@/theme/srsTokens';
import { TOKENS } from '@/theme/tokens';
import { getVisiblePages } from '@/store/selectors/pages';
import { getReviewStatusColor } from '@/theme/semanticColors';
import { useI18n } from '@/i18n';

interface FlashcardListItemProps {
  card: Flashcard;
  group: FlashcardGroup;
  onStartEdit: () => void;
  onDelete: () => void;
  readOnly?: boolean;
  fill?: boolean;
}

function srsChip(
  state: SrsState,
  theme: MD3Theme,
  t: (key: string) => string,
): { text: string; icon: string; color: string; bg: string } {
  const category = getCardCategory(state);
  const token = SRS_CATEGORIES_TOKENS[category];
  const srsColors = getReviewStatusColor(theme, category);
  return {
    text: t(token.labelKey),
    icon: token.iconName,
    color: srsColors.fg,
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
  readOnly,
  fill,
}: FlashcardListItemProps) {
  const theme = useTheme();
  const { t } = useI18n();
  const { showHidden: viewHidden, setShowHidden: setViewHidden } = useHiddenPagesToggle();

  const activeCount = group.activePageCount;
  const totalCount = card.pages.length;
  const hasHiddenPages = totalCount > activeCount;

  const srs = srsChip(card.srsState, theme, t);
  const mastery = getMasteryPercent(card.srsState);
  const reviewIn = daysUntilReview(card.srsState, t);

  const visiblePages = getVisiblePages(card, group);

  const displayPages = viewHidden ? card.pages : visiblePages;

  const toggleLabel = t('browse.show_hidden_pages');

  return (
    <AppCard
      style={[styles.card, fill && styles.cardFill]}
      contentStyle={fill ? styles.cardInnerFill : undefined}
      mode="elevated"
    >
      <AppCard.Content style={[styles.cardContent, fill && styles.contentFill]}>
        {displayPages.map((page, i) => {
          const isHidden = i >= activeCount;

          return (
            <View
              key={i}
              testID={`flashcard-page-slot-${card.id}-${i}`}
              style={styles.pageSlot}
            >
              <Text
                variant="bodyMedium"
                style={[
                  styles.pageLine,
                  { color: isHidden ? theme.colors.onSurfaceVariant : theme.colors.onSurface },
                ]}
              >
                {page || '—'}
              </Text>
            </View>
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
              accessibilityLabel={t('browse.show_hidden_pages')}
            />
          </View>
        )}
      </AppCard.Content>
      <AppCard.Actions style={[styles.cardActions, fill && styles.cardActionsPinned]}>
        <View style={styles.cardActionsLeft}>
          <View
            style={[styles.srsIconBadge, { backgroundColor: srs.bg }]}
            accessibilityRole="image"
            accessibilityLabel={srs.text}
          >
            <AppIcon name={srs.icon} size={TOKENS.iconSize.sm} color={srs.color} />
          </View>
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
              size={TOKENS.iconSize.sm}
              iconColor={theme.colors.primary}
              onPress={onStartEdit}
              accessibilityLabel={t('browse.edit_card')}
            />
            <IconButton
              icon="delete"
              size={TOKENS.iconSize.sm}
              iconColor={theme.colors.error}
              onPress={onDelete}
              accessibilityLabel={t('browse.delete_card')}
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
  cardFill: {
    flex: 1,
  },
  cardInnerFill: {
    flex: 1,
    justifyContent: 'space-between',
  },
  contentFill: {
    flexGrow: 1,
  },
  cardContent: {
    paddingVertical: TOKENS.spacing.sm,
    gap: TOKENS.spacing.sm,
  },
  pageSlot: {
    justifyContent: 'flex-start',
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
    padding: TOKENS.spacing.sm,
    justifyContent: 'space-between',
  },
  cardActionsPinned: {
    marginTop: 'auto',
  },
  cardActionsLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.md,
  },
  // Circular tinted badge so the category icon matches the segmented bar's
  // "colored icon on a tinted fill" look.
  srsIconBadge: {
    width: TOKENS.touchTarget.compact,
    height: TOKENS.touchTarget.compact,
    borderRadius: TOKENS.radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
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
