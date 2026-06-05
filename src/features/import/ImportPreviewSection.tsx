import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text } from 'react-native-paper';
import type { Flashcard, FlashcardGroup } from '@/types/models';
import { useI18n } from '@/i18n';
import { FlashcardList } from '@/features/flashcards/FlashcardList';
import { EditFlashcardDialog } from '@/components/browse/EditFlashcardDialog';
import { TOKENS } from '@/theme/tokens';

interface ImportPreviewSectionProps {
  cards: Flashcard[];
  group: FlashcardGroup;
  editingId: string | null;
  editPages: string[];
  onPagesChange: (pages: string[]) => void;
  onSave: () => void;
  onCancel: () => void;
  onStartEdit: (card: Flashcard) => void;
  onDelete: (cardId: string) => void;
}

export function ImportPreviewSection({
  cards,
  group,
  editingId,
  editPages,
  onPagesChange,
  onSave,
  onCancel,
  onStartEdit,
  onDelete,
}: ImportPreviewSectionProps) {
  const { t } = useI18n();

  if (cards.length === 0) {
    return null;
  }

  return (
    <View style={styles.container}>
      <Text variant="titleMedium" style={styles.title}>
        {`${t('import.preview')} (${cards.length})`}
      </Text>
      <FlashcardList
        cards={cards}
        group={group}
        onStartEdit={onStartEdit}
        onDelete={onDelete}
        scrollEnabled={false}
      />
      <EditFlashcardDialog
        visible={!!editingId}
        group={group}
        editPages={editPages}
        onPagesChange={onPagesChange}
        onSave={onSave}
        onCancel={onCancel}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: TOKENS.spacing.md,
  },
  title: {
    fontWeight: TOKENS.typography.weight.bold,
  },
});
