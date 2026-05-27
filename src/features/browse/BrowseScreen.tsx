import React, { useMemo, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { FAB, Portal, Text, useTheme } from 'react-native-paper';
import { DeleteFlashcardDialog } from '../../components/browse/DeleteFlashcardDialog';
import { BrowseFilterChips } from '../../components/browse/BrowseFilterChips';
import type { BrowseFilter } from '../../constants/browseFilters';
import { BrowseSearchBar } from '../../components/browse/BrowseSearchBar';
import { GroupNotFound } from '../../components/GroupNotFound';
import { AppScreen } from '../../components/layout/AppScreen';
import { LoadingState } from '../../components/layout/LoadingState';
import { SegmentedProgressBar } from '../../components/SegmentedProgressBar';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useI18n } from '../../i18n';
import { getCardCategory } from '../../srs/srsEngine';
import { computeCardStats } from '../../store/selectors/stats';
import { TOKENS } from '../../theme/tokens';
import type { Flashcard } from '../../types/models';
import { FlashcardList } from '../flashcards/FlashcardList';
import { useFlashcardListEditing } from '../flashcards/useFlashcardListEditing';

export function BrowseScreen() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const group = store.groups.find((item) => item.id === groupId);
  const activeGroup = group;
  const [search, setSearch] = useState('');
  const [browseFilter, setBrowseFilter] = useState<BrowseFilter>('all');

  const stats = useMemo(() => {
    return activeGroup
      ? computeCardStats(activeGroup.cards)
      : { total: 0, newCount: 0, learning: 0, review: 0, mastered: 0 };
  }, [activeGroup]);

  const filtered = useMemo(() => {
    if (!activeGroup) {
      return [];
    }

    let cards: Flashcard[];

    switch (browseFilter) {
      case 'new':
        cards = activeGroup.cards.filter((card) => getCardCategory(card.srsState) === 'new');
        break;
      case 'learning':
        cards = activeGroup.cards.filter((card) => getCardCategory(card.srsState) === 'learning');
        break;
      case 'review':
        cards = activeGroup.cards.filter((card) => getCardCategory(card.srsState) === 'review');
        break;
      case 'mastered':
        cards = activeGroup.cards.filter((card) => getCardCategory(card.srsState) === 'mastered');
        break;
      case 'all':
      default:
        cards = [...activeGroup.cards];
        break;
    }

    const query = search.toLowerCase();
    if (query) {
      cards = cards.filter((card) => card.pages.some((page) => page.toLowerCase().includes(query)));
    }

    return cards;
  }, [activeGroup, browseFilter, search]);

  const {
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
  } = useFlashcardListEditing({
    pageCount: activeGroup?.pageNames.length ?? 0,
    deleteIfLessThanTwoPages: true,
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

  const addCard = () => {
    if (!activeGroup) {
      return;
    }

    const count = activeGroup.activePageCount ?? activeGroup.pageNames.length;
    const pages = Array(count).fill('');
    const id = store.addFlashcard(activeGroup.id, pages);
    setEditingId(id);
    setEditPages(pages);
  };

  const handleBack = () => {
    router.back();
  };

  if (store.isLoading) {
    return <LoadingState />;
  }

  if (!activeGroup) {
    return <GroupNotFound onBack={handleBack} />;
  }

  return (
    <AppScreen title={activeGroup.name} onBack={handleBack} scroll={false}>
      <View style={styles.header}>
        <Text style={[styles.cardCountText, { color: theme.colors.onSurfaceVariant }]}>
          {t('dashboard.cards_count', { count: stats.total })}
        </Text>
      </View>

      <View style={styles.progressBarSection}>
        <SegmentedProgressBar stats={stats} showLegend />
      </View>

      <BrowseSearchBar search={search} setSearch={setSearch} t={t} />

      <BrowseFilterChips
        browseFilter={browseFilter}
        setBrowseFilter={setBrowseFilter}
        stats={stats}
        t={t}
      />

      <FlashcardList
        cards={filtered}
        group={activeGroup}
        editingId={editingId}
        editPages={editPages}
        setEditPages={setEditPages}
        onSave={saveEdit}
        onCancel={cancelEdit}
        onStartEdit={startEdit}
        onDelete={setDeleteCardId}
        t={t}
        contentContainerStyle={styles.listContainer}
      />

      <FAB icon="plus" style={styles.fab} onPress={addCard} accessibilityLabel="Add flashcard" />

      <Portal>
        <DeleteFlashcardDialog
          visible={!!deleteCardId}
          onDismiss={() => setDeleteCardId(null)}
          onConfirm={confirmDeleteCard}
          t={t}
        />
      </Portal>
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  header: {
    alignItems: 'flex-end',
    marginBottom: TOKENS.spacing.sm,
  },
  cardCountText: {
    fontSize: TOKENS.typography.size.xs,
  },
  progressBarSection: {
    marginBottom: TOKENS.spacing.lg,
  },
  listContainer: {
    gap: TOKENS.spacing.lg,
    paddingBottom: 100,
  },
  fab: {
    position: 'absolute',
    margin: TOKENS.spacing.lg,
    right: TOKENS.spacing.sm,
    bottom: TOKENS.spacing.sm,
    borderRadius: TOKENS.radius.xl,
  },
});
