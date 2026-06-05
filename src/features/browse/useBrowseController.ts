import { useCallback, useMemo, useState } from 'react';
import { useLocalSearchParams } from 'expo-router';
import type { SrsCardCategory } from '@/srs/srsEngine';
import { getCardCategory } from '@/srs/srsEngine';
import type { Flashcard } from '@/types/models';
import { safeBack } from '@/utils/navigation';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';
import { computeCardStats } from '@/store/selectors/stats';
import { CATEGORY_TO_STATS_KEY, SRS_CATEGORY_ORDER } from '@/theme/srsTokens';
import { useFlashcardListEditing } from '@/features/flashcards/useFlashcardListEditing';
import { shouldShowCard, toggleCategoryReducer } from './browseFilter';

export function useBrowseController() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const store = useFlashcardStore();
  const group =
    store.groups.find((item) => item.id === groupId) ??
    store.archivedGroups.find((item) => item.id === groupId);
  const activeGroup = group ?? null;
  const isReadOnly = activeGroup ? (activeGroup.archivedAt ?? 0) > 0 : false;
  const [search, setSearch] = useState('');
  const [activeCategories, setActiveCategories] = useState<SrsCardCategory[]>([]);

  const stats = useMemo(() => {
    return activeGroup
      ? computeCardStats(activeGroup.cards)
      : { total: 0, newCount: 0, learning: 0, review: 0, mastered: 0 };
  }, [activeGroup]);

  const nonEmptyCategories = useMemo(() => {
    return SRS_CATEGORY_ORDER.filter((category) => (stats[CATEGORY_TO_STATS_KEY[category]] as number) > 0);
  }, [stats]);

  const toggleCategory = useCallback((category: SrsCardCategory) => {
    setActiveCategories((prev) => toggleCategoryReducer(prev, category, nonEmptyCategories));
  }, [nonEmptyCategories]);

  const filteredCards = useMemo(() => {
    if (!activeGroup) {
      return [];
    }

    let cards: Flashcard[] = activeGroup.cards.filter((card) =>
      shouldShowCard(activeCategories, getCardCategory(card.srsState)),
    );

    const query = search.toLowerCase();
    if (query) {
      cards = cards.filter((card) => card.pages.some((page) => page.toLowerCase().includes(query)));
    }

    return cards;
  }, [activeGroup, activeCategories, search]);

  const {
    editingId,
    editPages,
    setEditPages,
    deleteCardId,
    setDeleteCardId,
    startEdit,
    startCreate,
    cancelEdit,
    saveEdit,
    confirmDeleteCard,
  } = useFlashcardListEditing({
    pageCount: activeGroup?.activePageCount ?? 0,
    onCreateCard: (pages) => {
      if (activeGroup) {
        store.addFlashcard(activeGroup.id, pages);
      }
    },
    onSaveCard: (cardId, pages) => {
      if (!activeGroup) {
        return;
      }

      const card = activeGroup.cards.find((item) => item.id === cardId);
      if (card) {
        store.updateFlashcard(activeGroup.id, { ...card, pages });
      }
    },
    onDeleteCard: (cardId) => {
      if (activeGroup) {
        store.deleteFlashcard(activeGroup.id, cardId);
      }
    },
  });

  const filledPageCount = useMemo(
    () => editPages.filter((page) => page.trim().length > 0).length,
    [editPages],
  );
  const canSaveEdit = filledPageCount >= 2;

  return {
    activeCategories,
    activeGroup,
    canSaveEdit,
    cardCountLabel: t('dashboard.cards_count', { count: stats.total }),
    confirmDeleteCard,
    deleteCardId,
    editPages,
    editingId,
    emptyLabel: t('browse.no_cards'),
    filteredCards,
    handleBack: safeBack,
    isLoading: store.isLoading,
    isReadOnly,
    minPagesMessage: t('browse.min_filled_pages'),
    saveEdit,
    search,
    setDeleteCardId,
    setEditPages,
    setSearch,
    startCreate,
    startEdit,
    cancelEdit,
    stats,
    toggleCategory,
  };
}
