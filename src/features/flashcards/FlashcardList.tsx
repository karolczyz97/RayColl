import React, { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import type { StyleProp, ViewStyle } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import Animated, { LinearTransition } from 'react-native-reanimated';
import type { Flashcard, FlashcardGroup } from '@/types/models';
import { FlashcardListItem } from '@/components/browse/FlashcardListItem';
import { AppIcon } from '@/components/AppIcon';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { getVisiblePageNames } from '@/store/selectors/pages';
import { useNavigationShell } from '@/contexts/NavigationShellContext';
import { TOKENS } from '@/theme/tokens';
import { isExpandedWindowSize } from '@/utils/windowSizeClass';

export interface FlashcardPairRow {
  id: string;
  rowIndex: number;
  cards: [Flashcard, Flashcard?];
}

type FlashcardListEntry = Flashcard | FlashcardPairRow;

interface FlashcardListProps {
  cards: Flashcard[];
  group: FlashcardGroup;
  onStartEdit: (card: Flashcard) => void;
  onDelete: (cardId: string) => void;
  style?: StyleProp<ViewStyle>;
  scrollEnabled?: boolean;
  contentContainerStyle?: StyleProp<ViewStyle>;
  keyboardShouldPersistTaps?: 'always' | 'never' | 'handled';
  initialNumToRender?: number;
  maxToRenderPerBatch?: number;
  windowSize?: number;
  showHeader?: boolean;
  emptyLabel?: string;
  listHeaderContent?: React.ReactNode;
  itemAnimationOffset?: number;
  className?: string;
  readOnly?: boolean;
}

export function pairFlashcardRows(cards: Flashcard[]): FlashcardPairRow[] {
  const rows: FlashcardPairRow[] = [];

  for (let index = 0; index < cards.length; index += 2) {
    const left = cards[index];
    const right = cards[index + 1];

    rows.push({
      id: `row-${left.id}`,
      rowIndex: index / 2,
      cards: [left, right],
    });
  }

  return rows;
}

function isFlashcardPairRow(item: FlashcardListEntry): item is FlashcardPairRow {
  return 'cards' in item;
}

export function FlashcardList({
  cards,
  group,
  onStartEdit,
  onDelete,
  style,
  scrollEnabled = true,
  contentContainerStyle,
  keyboardShouldPersistTaps = 'handled',
  initialNumToRender = 12,
  maxToRenderPerBatch = 12,
  windowSize = 7,
  showHeader = true,
  emptyLabel,
  listHeaderContent,
  itemAnimationOffset = 0,
  className,
  readOnly,
}: FlashcardListProps) {
  const theme = useTheme();
  const { contentWidth } = useNavigationShell();
  const useTwoColumnLayout = isExpandedWindowSize(contentWidth);

  const data = useMemo<FlashcardListEntry[]>(
    () => (useTwoColumnLayout ? pairFlashcardRows(cards) : cards),
    [cards, useTwoColumnLayout],
  );


  const pageNames = getVisiblePageNames(group);

  const pageNamesHeader =
    showHeader && cards.length > 0 ? (
      <AnimatedSection order={itemAnimationOffset}>
        <View style={styles.headerRow}>
          {pageNames.filter(Boolean).map((name, i) => (
            <React.Fragment key={i}>
              {i > 0 && (
                <View style={styles.separatorContainer}>
                  <AppIcon
                    name="chevron-right"
                    size={TOKENS.iconSize.xs}
                    color={theme.colors.outline}
                    style={styles.separatorIcon}
                  />
                </View>
              )}
              <View
                style={[
                  styles.pageNameChip,
                  {
                    backgroundColor: theme.colors.surfaceVariant,
                    borderColor: theme.colors.outlineVariant,
                  },
                ]}
              >
                <Text
                  variant="labelSmall"
                  style={[styles.pageNameText, { color: theme.colors.onSurfaceVariant }]}
                >
                  {name}
                </Text>
              </View>
            </React.Fragment>
          ))}
        </View>
      </AnimatedSection>
    ) : null;

  // The page-names header animates at itemAnimationOffset, so the list items
  // start one step later.
  const itemOrderOffset = itemAnimationOffset + 1;



  const listHeader =
    listHeaderContent || pageNamesHeader ? (
      <View style={styles.listHeaderWrapper}>
        {listHeaderContent}
        {pageNamesHeader}
      </View>
    ) : null;

  return (
    <Animated.FlatList
      key={`flashcard-list-${useTwoColumnLayout ? 'grid' : 'list'}`}
      className={className}
      data={data}
      keyExtractor={(item: FlashcardListEntry) => item.id}
      itemLayoutAnimation={LinearTransition.springify()}
      renderItem={({ item, index }) => {
        if (isFlashcardPairRow(item)) {
          return (
            <FlashcardPairRowView
              row={item}
              group={group}
              onStartEdit={onStartEdit}
              onDelete={onDelete}
              readOnly={readOnly}
              itemAnimationOffset={itemOrderOffset}
            />
          );
        }

        return (
          <AnimatedSection order={itemOrderOffset + index}>
            <FlashcardListItem
              card={item}
              group={group}
              onStartEdit={() => onStartEdit(item)}
              onDelete={() => onDelete(item.id)}
              readOnly={readOnly}
            />
          </AnimatedSection>
        );
      }}
      ListHeaderComponent={listHeader}
      ListEmptyComponent={
        emptyLabel ? (
          <View style={styles.emptyState}>
            <Text style={[styles.emptyText, { color: theme.colors.onSurfaceVariant }]}>
              {emptyLabel}
            </Text>
          </View>
        ) : null
      }
      style={style}
      scrollEnabled={scrollEnabled}
      contentContainerStyle={[styles.listContainer, contentContainerStyle]}
      keyboardShouldPersistTaps={keyboardShouldPersistTaps}
      initialNumToRender={initialNumToRender}
      maxToRenderPerBatch={maxToRenderPerBatch}
      windowSize={windowSize}
    />
  );
}

interface FlashcardPairRowViewProps {
  row: FlashcardPairRow;
  group: FlashcardGroup;
  onStartEdit: (card: Flashcard) => void;
  onDelete: (cardId: string) => void;
  readOnly?: boolean;
  itemAnimationOffset: number;
}

function FlashcardPairRowView({
  row,
  group,
  onStartEdit,
  onDelete,
  readOnly,
  itemAnimationOffset,
}: FlashcardPairRowViewProps) {
  const [leftCard, rightCard] = row.cards;

  return (
    <View style={styles.gridRow}>
      <View style={styles.columnCell}>
        <AnimatedSection order={itemAnimationOffset + row.rowIndex} style={styles.itemAnimation}>
          <FlashcardListItem
            card={leftCard}
            group={group}
            onStartEdit={() => onStartEdit(leftCard)}
            onDelete={() => onDelete(leftCard.id)}
            readOnly={readOnly}
            fill
          />
        </AnimatedSection>
      </View>
      <View style={styles.columnCell}>
        {rightCard ? (
          <AnimatedSection order={itemAnimationOffset + row.rowIndex} style={styles.itemAnimation}>
            <FlashcardListItem
              card={rightCard}
              group={group}
              onStartEdit={() => onStartEdit(rightCard)}
              onDelete={() => onDelete(rightCard.id)}
              readOnly={readOnly}
              fill
            />
          </AnimatedSection>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  listHeaderWrapper: {
    gap: TOKENS.spacing.lg,
  },
  listContainer: {},
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    flexWrap: 'wrap',
    paddingHorizontal: TOKENS.spacing.sm,
    gap: TOKENS.spacing.xs,
  },
  separatorContainer: {
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: TOKENS.spacing.xxs,
  },
  separatorIcon: {
    opacity: 0.5,
  },
  pageNameChip: {
    borderWidth: 1,
    borderRadius: TOKENS.radius.pill,
    paddingVertical: TOKENS.spacing.xs,
    paddingHorizontal: TOKENS.spacing.sm,
  },
  pageNameText: {
    fontWeight: TOKENS.typography.weight.medium,
  },
  gridRow: {
    flexDirection: 'row',
    gap: TOKENS.spacing.lg,
    alignItems: 'stretch',
  },
  columnCell: {
    flex: 1,
    flexBasis: 0,
    minWidth: 0,
    alignSelf: 'stretch',
  },
  itemAnimation: {
    flex: 1,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: TOKENS.spacing.xxl,
  },
  emptyText: {
    textAlign: 'center',
  },
});
