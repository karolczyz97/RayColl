import React, { useCallback, useMemo } from 'react';
import { FlatList, StyleSheet, View } from 'react-native';
import type { StyleProp, ViewStyle } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import type { Flashcard, FlashcardGroup } from '@/types/models';
import type { TranslationFn } from '@/i18n';
import { FlashcardListItem } from '@/components/browse/FlashcardListItem';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { getVisiblePageNames } from '@/store/selectors/pages';
import { useNavigationShell } from '@/contexts/NavigationShellContext';
import { TOKENS } from '@/theme/tokens';
import { isExpandedWindowSize } from '@/utils/windowSizeClass';

type FlashcardRow = Flashcard | { id: string; placeholder: true };

interface FlashcardListProps {
  cards: Flashcard[];
  group: FlashcardGroup;
  onStartEdit: (card: Flashcard) => void;
  onDelete: (cardId: string) => void;
  t: TranslationFn;
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

export function FlashcardList({
  cards,
  group,
  onStartEdit,
  onDelete,
  t,
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
  const numColumns = useTwoColumnLayout ? 2 : 1;

  // Pad an odd card count with an invisible cell so the lone last card keeps
  // the same width as paired cards instead of stretching toward the center.
  const data = useMemo<FlashcardRow[]>(() => {
    if (numColumns === 2 && cards.length % 2 === 1) {
      return [...cards, { id: '__spacer__', placeholder: true }];
    }
    return cards;
  }, [cards, numColumns]);

  const ItemSeparator = useCallback(
    () => <View style={styles.itemSeparator} />,
    [],
  );

  const pageNames = getVisiblePageNames(group);

  const pageNamesHeader =
    showHeader && cards.length > 0 ? (
      <View style={styles.headerRow}>
        <Text
          variant="labelMedium"
          style={[styles.headerText, { color: theme.colors.onSurfaceVariant }]}
        >
          {pageNames.filter(Boolean).join('  •  ')}
        </Text>
      </View>
    ) : null;

  const listHeader =
    listHeaderContent || pageNamesHeader ? (
      <View>
        {listHeaderContent}
        {pageNamesHeader}
      </View>
    ) : null;

  return (
    <FlatList
      key={`flashcard-list-${numColumns}`}
      className={className}
      data={data}
      keyExtractor={(item) => item.id}
      numColumns={numColumns}
      columnWrapperStyle={numColumns > 1 ? styles.columnWrapper : undefined}
      ItemSeparatorComponent={ItemSeparator}
      renderItem={({ item, index }) => {
        if ('placeholder' in item) {
          return <View style={styles.columnCell} />;
        }
        const row = Math.floor(index / numColumns);
        return (
          <View style={numColumns > 1 ? styles.columnCell : undefined}>
            <AnimatedSection order={itemAnimationOffset + row}>
              <FlashcardListItem
                card={item}
                group={group}
                onStartEdit={() => onStartEdit(item)}
                onDelete={() => onDelete(item.id)}
                readOnly={readOnly}
                t={t}
              />
            </AnimatedSection>
          </View>
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

const styles = StyleSheet.create({
  listContainer: {},
  itemSeparator: {
    height: TOKENS.spacing.lg,
  },
  headerRow: {
    paddingHorizontal: TOKENS.spacing.sm,
    paddingBottom: TOKENS.spacing.xs,
  },
  headerText: {
    fontWeight: TOKENS.typography.weight.medium,
  },
  columnWrapper: {
    gap: TOKENS.spacing.lg,
  },
  columnCell: {
    flex: 1,
    maxWidth: '50%',
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: TOKENS.spacing.xxl,
  },
  emptyText: {
    textAlign: 'center',
  },
});
