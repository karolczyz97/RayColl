import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text } from 'react-native-paper';
import type { Flashcard, FlashcardGroup } from '../../types/models';
import type { TranslationFn } from '../../i18n';
import { FlashcardList } from '../flashcards/FlashcardList';
import { EditFlashcardDialog } from '../../components/browse/EditFlashcardDialog';
import { TOKENS } from '../../theme/tokens';

interface ImportPreviewSectionProps {
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
}

export function ImportPreviewSection({
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
}: ImportPreviewSectionProps) {
  if (cards.length === 0) {
    return null;
  }

  return (
    <View style={styles.container}>
      <Text variant="titleMedium" style={styles.title}>
        {`${t('import.preview') || 'Preview'} (${cards.length})`}
      </Text>
      <FlashcardList
        cards={cards}
        group={group}
        onStartEdit={onStartEdit}
        onDelete={onDelete}
        t={t}
        scrollEnabled={false}
      />
      <EditFlashcardDialog
        visible={!!editingId}
        group={group}
        editPages={editPages}
        setEditPages={setEditPages}
        onSave={onSave}
        onCancel={onCancel}
        t={t}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: TOKENS.spacing.md,
  },
  title: {
    fontWeight: '700',
  },
});
