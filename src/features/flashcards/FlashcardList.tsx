import React from 'react';
import { FlatList, StyleSheet } from 'react-native';
import type { StyleProp, ViewStyle } from 'react-native';
import type { Flashcard, FlashcardGroup } from '../../types/models';
import type { TranslationFn } from '../../i18n';
import { FlashcardListItem } from '../../components/browse/FlashcardListItem';
import { TOKENS } from '../../theme/tokens';

interface FlashcardListProps {
  cards: Flashcard[];
  group: FlashcardGroup;
  editingId: string | null;
  editPages: string[];
  setEditPages: React.Dispatch<React.SetStateAction<string[]>>;
  onSave: () => void;
  onCancel: () => void;
  onStartEdit: (card: Flashcard) => void;
  onDelete: (cardId: string) => void;
  t: TranslationFn;
  scrollEnabled?: boolean;
  contentContainerStyle?: StyleProp<ViewStyle>;
  keyboardShouldPersistTaps?: 'always' | 'never' | 'handled';
  initialNumToRender?: number;
  maxToRenderPerBatch?: number;
  windowSize?: number;
}

export function FlashcardList({
  cards,
  group,
  editingId,
  editPages,
  setEditPages,
  onSave,
  onCancel,
  onStartEdit,
  onDelete,
  t,
  scrollEnabled = true,
  contentContainerStyle,
  keyboardShouldPersistTaps = 'handled',
  initialNumToRender = 12,
  maxToRenderPerBatch = 12,
  windowSize = 7,
}: FlashcardListProps) {
  return (
    <FlatList
      data={cards}
      keyExtractor={(item) => item.id}
      renderItem={({ item }) => (
        <FlashcardListItem
          card={item}
          group={group}
          isEditing={editingId === item.id}
          editPages={editPages}
          setEditPages={setEditPages}
          onSave={onSave}
          onCancel={onCancel}
          onStartEdit={() => onStartEdit(item)}
          onDelete={() => onDelete(item.id)}
          t={t}
        />
      )}
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
});
