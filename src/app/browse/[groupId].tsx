import React, { useState, useMemo } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { Text, Portal, useTheme, FAB, ActivityIndicator } from 'react-native-paper';
import { useLocalSearchParams, router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useI18n } from '../../i18n';
import { SegmentedProgressBar, computeCardStats } from '../../components/SegmentedProgressBar';
import type { Flashcard } from '../../types/models';
import { getCardCategory } from '../../srs/srsEngine';
import { PageHeader } from '../../components/PageHeader';
import { GroupNotFound } from '../../components/GroupNotFound';

import { BrowseSearchBar } from '../../components/browse/BrowseSearchBar';
import { BrowseFilterChips } from '../../components/browse/BrowseFilterChips';
import { FlashcardListItem } from '../../components/browse/FlashcardListItem';
import { DeleteFlashcardDialog } from '../../components/browse/DeleteFlashcardDialog';

type BrowseFilter = 'all' | 'learning' | 'review' | 'new' | 'mastered';

export default function BrowsePage() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();

  const group = store.groups.find((g) => g.id === groupId);

  const activeGroup = group;

  const [search, setSearch] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editPages, setEditPages] = useState<string[]>([]);
  const [deleteCardId, setDeleteCardId] = useState<string | null>(null);
  const [browseFilter, setBrowseFilter] = useState<BrowseFilter>('all');

  const stats = useMemo(() => {
    return activeGroup
      ? computeCardStats(activeGroup.cards)
      : { total: 0, newCount: 0, learning: 0, review: 0, mastered: 0 };
  }, [activeGroup]);

  const filtered = useMemo(() => {
    if (!activeGroup) return [];
    let cards: Flashcard[];
    switch (browseFilter) {
      case 'new':
        cards = activeGroup.cards.filter((c) => getCardCategory(c.srsState) === 'new');
        break;
      case 'learning':
        cards = activeGroup.cards.filter((c) => getCardCategory(c.srsState) === 'learning');
        break;
      case 'review':
        cards = activeGroup.cards.filter((c) => getCardCategory(c.srsState) === 'review');
        break;
      case 'mastered':
        cards = activeGroup.cards.filter((c) => getCardCategory(c.srsState) === 'mastered');
        break;
      case 'all':
      default:
        cards = [...activeGroup.cards];
        break;
    }
    const q = search.toLowerCase();
    if (q) {
      cards = cards.filter((c) => c.pages.some((p) => p.toLowerCase().includes(q)));
    }
    return cards;
  }, [activeGroup, search, browseFilter]);

  const startEdit = (card: Flashcard) => {
    if (!activeGroup) return;
    setEditingId(card.id);
    const totalCount = Math.max(activeGroup.pageNames.length, card.pages.length);
    const pages = [...card.pages];
    while (pages.length < totalCount) {
      pages.push('');
    }
    setEditPages(pages);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditPages([]);
  };

  const saveEdit = () => {
    if (!editingId || !activeGroup) return;
    const filledCount = editPages.filter((p) => p.trim()).length;
    if (filledCount < 2) {
      store.deleteFlashcard(activeGroup.id, editingId);
      cancelEdit();
      return;
    }
    const card = activeGroup.cards.find((c) => c.id === editingId);
    if (card) {
      const updatedPages = [...editPages];
      store.updateFlashcard(activeGroup.id, { ...card, pages: updatedPages });
    }
    cancelEdit();
  };

  const addCard = () => {
    if (!activeGroup) return;
    const count = activeGroup.activePageCount ?? activeGroup.pageNames.length;
    const pages = Array(count).fill('');
    const id = store.addFlashcard(activeGroup.id, pages);
    setEditingId(id);
    setEditPages(pages);
  };

  const handleBack = () => {
    router.back();
  };

  const confirmDeleteCard = () => {
    if (deleteCardId && activeGroup) {
      store.deleteFlashcard(activeGroup.id, deleteCardId);
      setDeleteCardId(null);
    }
  };

  if (store.isLoading) {
    return (
      <View style={[styles.centerContainer, { backgroundColor: theme.colors.background }]}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
      </View>
    );
  }

  if (!activeGroup) {
    return <GroupNotFound onBack={handleBack} />;
  }

  return (
    <SafeAreaView style={[styles.root, { backgroundColor: theme.colors.background }]} edges={['top', 'left', 'right']}>
      <View style={styles.header}>
        <PageHeader title={activeGroup.name} onBack={handleBack} />
        <Text style={[styles.cardCountText, { color: theme.colors.onSurfaceVariant }]}>
          {t('dashboard.cards_count', { count: stats.total })}
        </Text>
      </View>

      {/* Segmented Progress bar */}
      <View style={styles.progressBarSection}>
        <SegmentedProgressBar stats={stats} showLegend />
      </View>

      {/* Search Input */}
      <BrowseSearchBar search={search} setSearch={setSearch} t={t} />

      {/* Filter Chips */}
      <BrowseFilterChips
        browseFilter={browseFilter}
        setBrowseFilter={setBrowseFilter}
        stats={stats}
        t={t}
      />

      {/* Cards list */}
      <ScrollView contentContainerStyle={styles.listContainer}>
        {filtered.map((card) => (
          <FlashcardListItem
            key={card.id}
            card={card}
            group={activeGroup}
            isEditing={editingId === card.id}
            editPages={editPages}
            setEditPages={setEditPages}
            onSave={saveEdit}
            onCancel={cancelEdit}
            onStartEdit={() => startEdit(card)}
            onDelete={() => setDeleteCardId(card.id)}
            t={t}
          />
        ))}
      </ScrollView>

      {/* FAB to add card */}
      <FAB icon="plus" style={styles.fab} onPress={addCard} accessibilityLabel="Add flashcard" />

      {/* Dialog confirmations */}
      <Portal>
        <DeleteFlashcardDialog
          visible={!!deleteCardId}
          onDismiss={() => setDeleteCardId(null)}
          onConfirm={confirmDeleteCard}
          t={t}
        />
      </Portal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    paddingHorizontal: 16,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  cardCountText: {
    fontSize: 12,
  },
  progressBarSection: {
    marginBottom: 16,
  },
  listContainer: {
    gap: 16,
    paddingBottom: 100,
  },
  fab: {
    position: 'absolute',
    margin: 16,
    right: 8,
    bottom: 8,
    borderRadius: 20,
  },
});
