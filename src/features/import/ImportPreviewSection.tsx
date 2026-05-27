import React from 'react';
import type { Flashcard, FlashcardGroup } from '../../types/models';
import type { TranslationFn } from '../../i18n';
import { SectionCard } from '../../components/layout/SectionCard';
import { FlashcardList } from '../flashcards/FlashcardList';

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
    <SectionCard title={`${t('import.preview') || 'Preview'} (${cards.length})`}>
      <FlashcardList
        cards={cards}
        group={group}
        editingId={editingId}
        editPages={editPages}
        setEditPages={setEditPages}
        onSave={onSave}
        onCancel={onCancel}
        onStartEdit={onStartEdit}
        onDelete={onDelete}
        t={t}
        scrollEnabled={false}
      />
    </SectionCard>
  );
}
