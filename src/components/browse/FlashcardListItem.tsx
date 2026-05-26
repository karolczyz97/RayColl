import React, { useState } from 'react';
import { View, StyleSheet } from 'react-native';
import { Card, Text, Chip, IconButton, useTheme, Switch, MD3Theme } from 'react-native-paper';
import type { Flashcard, FlashcardGroup, SrsState } from '../../types/models';
import { getCardCategory } from '../../srs/srsEngine';
import { AppIcon } from '../../components/AppIcon';
import {
  SRS_CATEGORIES_TOKENS,
  getMasteryPercent,
  getDaysUntilReview,
} from '../../theme/srsTokens';
import { EditFlashcardForm } from './EditFlashcardForm';
import { getVisiblePages, getVisiblePageNames } from '../../store/selectors/pages';
import { getReviewStatusColor } from '../../theme/semanticColors';

interface Props {
  card: Flashcard;
  group: FlashcardGroup;
  isEditing: boolean;
  editPages: string[];
  setEditPages: React.Dispatch<React.SetStateAction<string[]>>;
  onSave: () => void;
  onCancel: () => void;
  onStartEdit: () => void;
  onDelete: () => void;
  t: (key: string, replacements?: any) => string;
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
    text: t(token.badgeTextKey),
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
  isEditing,
  editPages,
  setEditPages,
  onSave,
  onCancel,
  onStartEdit,
  onDelete,
  t,
}: Props) {
  const theme = useTheme();
  const [viewHidden, setViewHidden] = useState(false);

  const activeCount = group.activePageCount ?? group.pageNames.length;
  const totalCount = card.pages.length;
  const hasHiddenPages = totalCount > activeCount;

  if (isEditing) {
    return (
      <Card style={styles.card} mode="outlined">
        <EditFlashcardForm
          card={card}
          group={group}
          editPages={editPages}
          setEditPages={setEditPages}
          onSave={onSave}
          onCancel={onCancel}
          t={t}
        />
      </Card>
    );
  }

  const srs = srsChip(card.srsState, theme, t);
  const mastery = getMasteryPercent(card.srsState);
  const reviewIn = daysUntilReview(card.srsState, t);

  const visiblePages = getVisiblePages(card, group);
  const visiblePageNames = getVisiblePageNames(group);

  const primaryPage = viewHidden ? card.pages[0] || '—' : visiblePages[0] || '—';
  const otherPages = viewHidden ? card.pages.slice(1) : visiblePages.slice(1);

  const toggleLabel =
    t('browse.show_hidden_pages') === 'browse.show_hidden_pages'
      ? 'Show hidden pages'
      : t('browse.show_hidden_pages');

  return (
    <Card style={styles.card} mode="outlined">
      <Card.Content style={styles.cardContent}>
        {/* Primary page (front phrase) styled as a header */}
        <Text variant="titleMedium" style={[styles.primaryText, { color: theme.colors.primary }]}>
          {primaryPage}
        </Text>

        {/* Other pages listed with a cleaner layout */}
        {otherPages.map((page, i) => {
          const pageIdx = i + 1;
          const isHidden = pageIdx >= activeCount;
          const pageName = viewHidden
            ? group.pageNames[pageIdx] || t('import.page_label', { index: pageIdx + 1 })
            : visiblePageNames[pageIdx];
          return (
            <View key={pageIdx} style={styles.pageContentRow}>
              <Text
                variant="labelMedium"
                style={{ color: theme.colors.onSurfaceVariant, fontWeight: '600' }}
              >
                {pageName}
                {isHidden ? ' (Hidden)' : ''}:{' '}
              </Text>
              <Text
                variant="bodyMedium"
                style={{ color: theme.colors.onSurface, flex: 1, flexWrap: 'wrap' }}
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
              accessibilityLabel="Toggle viewing hidden pages"
            />
          </View>
        )}
      </Card.Content>
      <Card.Actions style={styles.cardActions}>
        <View style={styles.cardActionsLeft}>
          <Chip
            style={{ backgroundColor: srs.bg }}
            selectedColor={srs.color}
            textStyle={{ color: srs.color }}
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
      </Card.Actions>
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 20,
    overflow: 'hidden',
  },
  cardContent: {
    paddingBottom: 4,
    gap: 8,
  },
  primaryText: {
    fontWeight: 'bold',
    marginBottom: 4,
  },
  pageContentRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 4,
    marginTop: 2,
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 4,
    borderTopWidth: StyleSheet.hairlineWidth,
    paddingTop: 4,
  },
  switch: {
    transform: [{ scaleX: 0.8 }, { scaleY: 0.8 }],
  },
  cardActions: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    justifyContent: 'space-between',
  },
  cardActionsLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  statsIconsRow: {
    flexDirection: 'row',
    gap: 8,
  },
  statIconItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
  },
  cardActionsRight: {
    flexDirection: 'row',
  },
});
