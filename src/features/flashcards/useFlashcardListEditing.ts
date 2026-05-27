import { useCallback, useState } from 'react';
import type { Flashcard } from '../../types/models';

interface UseFlashcardListEditingOptions {
  pageCount: number;
  onSaveCard: (cardId: string, pages: string[]) => void;
  onDeleteCard: (cardId: string) => void;
  deleteIfLessThanTwoPages?: boolean;
}

export function useFlashcardListEditing({
  pageCount,
  onSaveCard,
  onDeleteCard,
  deleteIfLessThanTwoPages = false,
}: UseFlashcardListEditingOptions) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editPages, setEditPages] = useState<string[]>([]);
  const [deleteCardId, setDeleteCardId] = useState<string | null>(null);

  const startEdit = useCallback(
    (card: Flashcard) => {
      setEditingId(card.id);
      const pages = [...card.pages];

      while (pages.length < pageCount) {
        pages.push('');
      }

      setEditPages(pages);
    },
    [pageCount],
  );

  const cancelEdit = useCallback(() => {
    setEditingId(null);
    setEditPages([]);
  }, []);

  const saveEdit = useCallback(() => {
    if (!editingId) {
      return;
    }

    const nextPages = [...editPages];

    if (deleteIfLessThanTwoPages) {
      const filledCount = nextPages.filter((page) => page.trim()).length;

      if (filledCount < 2) {
        onDeleteCard(editingId);
        cancelEdit();
        return;
      }
    }

    onSaveCard(editingId, nextPages);
    cancelEdit();
  }, [cancelEdit, deleteIfLessThanTwoPages, editPages, editingId, onDeleteCard, onSaveCard]);

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
    cancelEdit,
    saveEdit,
    confirmDeleteCard,
  };
}
