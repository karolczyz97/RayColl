import React from 'react';
import { FlatList, StyleSheet, View } from 'react-native';
import type { StyleProp, ViewStyle } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import type { Flashcard, FlashcardGroup } from '../../types/models';
import type { TranslationFn } from '../../i18n';
import { FlashcardListItem } from '../../components/browse/FlashcardListItem';
import { getVisiblePageNames } from '../../store/selectors/pages';
import { useResponsiveLayout } from '../../hooks/useResponsiveLayout';
import { TOKENS } from '../../theme/tokens';

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
}: FlashcardListProps) {
  const theme = useTheme();
  const { useTwoColumnLayout } = useResponsiveLayout();
  const numColumns = useTwoColumnLayout ? 2 : 1;

  const pageNames = getVisiblePageNames(group);

  const header =
    showHeader && cards.length > 0 ? (
      <View style={styles.headerRow}>
        <Text
          variant="labelMedium"
          style={[styles.headerText, { color: theme.colors.onSurfaceVariant }]}
        >
          {pageNames.filter(Boolean).join('  ·  ')}
        </Text>
      </View>
    ) : null;

  return (
    <FlatList
      key={`flashcard-list-${numColumns}`}
      data={cards}
      keyExtractor={(item) => item.id}
      numColumns={numColumns}
      columnWrapperStyle={numColumns > 1 ? styles.columnWrapper : undefined}
      renderItem={({ item }) => (
        <View style={numColumns > 1 ? styles.columnCell : undefined}>
          <FlashcardListItem
            card={item}
            group={group}
            onStartEdit={() => onStartEdit(item)}
            onDelete={() => onDelete(item.id)}
            t={t}
          />
        </View>
      )}
      ListHeaderComponent={header}
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
  listContainer: {
    gap: TOKENS.spacing.lg,
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
  },
});
