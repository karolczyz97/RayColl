import { useCallback, useState } from 'react';
import type { Flashcard } from '../../types/models';
import { padArray } from '../../utils/array';

export const FLASHCARD_DRAFT_ID = '__draft_flashcard__';

interface UseFlashcardListEditingOptions {
  pageCount: number;
  onSaveCard: (cardId: string, pages: string[]) => void;
  onCreateCard?: (pages: string[]) => void;
  onDeleteCard: (cardId: string) => void;
}

export function useFlashcardListEditing({
  pageCount,
  onSaveCard,
  onCreateCard,
  onDeleteCard,
}: UseFlashcardListEditingOptions) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editPages, setEditPages] = useState<string[]>([]);
  const [deleteCardId, setDeleteCardId] = useState<string | null>(null);

  const startEdit = useCallback(
    (card: Flashcard) => {
      setEditingId(card.id);
      const pages = padArray([...card.pages], pageCount, '');

      setEditPages(pages);
    },
    [pageCount],
  );

  const cancelEdit = useCallback(() => {
    setEditingId(null);
    setEditPages([]);
  }, []);

  const startCreate = useCallback(() => {
    setEditingId(FLASHCARD_DRAFT_ID);
    setEditPages(Array(pageCount).fill(''));
  }, [pageCount]);

  const saveEdit = useCallback(() => {
    if (!editingId) {
      return;
    }

    const nextPages = [...editPages];

    if (editingId === FLASHCARD_DRAFT_ID) {
      onCreateCard?.(nextPages);
      cancelEdit();
      return;
    }

    onSaveCard(editingId, nextPages);
    cancelEdit();
  }, [cancelEdit, editPages, editingId, onCreateCard, onSaveCard]);

  const confirmDeleteCard = useCallback(() => {
    if (!deleteCardId) {
      return;
    }

    onDeleteCard(deleteCardId);
    setDeleteCardId(null);
  }, [deleteCardId, onDeleteCard]);

  return {
    editingId,
    setEditingId,
    editPages,
    setEditPages,
    deleteCardId,
    setDeleteCardId,
    startEdit,
    startCreate,
    cancelEdit,
    saveEdit,
    confirmDeleteCard,
  };
}
