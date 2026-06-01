import React, { useCallback, useMemo, useState } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { safeBack } from '../../utils/navigation';
import Animated, { ZoomIn } from 'react-native-reanimated';
import { FAB, Text, useTheme } from 'react-native-paper';
import { DeleteFlashcardDialog } from '../../components/browse/DeleteFlashcardDialog';
import { EditFlashcardDialog } from '../../components/browse/EditFlashcardDialog';
import { BrowseSearchBar } from '../../components/browse/BrowseSearchBar';
import { GroupNotFound } from '../../components/GroupNotFound';
import { AnimatedSection } from '../../components/layout/AnimatedSection';
import { AppScreen } from '../../components/layout/AppScreen';
import { LoadingState } from '../../components/layout/LoadingState';
import { SegmentedProgressBar } from '../../components/SegmentedProgressBar';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useI18n } from '../../i18n';
import type { SrsCardCategory } from '../../srs/srsEngine';
import { getCardCategory } from '../../srs/srsEngine';
import { computeCardStats } from '../../store/selectors/stats';
import { getEnterDelay } from '../../theme/motion';
import { SRS_CATEGORY_ORDER } from '../../theme/srsTokens';
import { TOKENS } from '../../theme/tokens';
import { shouldShowCard, toggleCategoryReducer } from './browseFilter';
import type { Flashcard } from '../../types/models';
import { FlashcardList } from '../flashcards/FlashcardList';
import { useFlashcardListEditing } from '../flashcards/useFlashcardListEditing';

export function BrowseScreen() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const group =
    store.groups.find((item) => item.id === groupId) ??
    store.archivedGroups.find((item) => item.id === groupId);
  const activeGroup = group;
  const isReadOnly = group ? (group.archivedAt ?? 0) > 0 : false;
  const [search, setSearch] = useState('');
  const [activeCategories, setActiveCategories] = useState<SrsCardCategory[]>([]);
  const minPagesMessage =
    t('browse.min_filled_pages') === 'browse.min_filled_pages'
      ? 'Fill at least 2 pages to save this flashcard.'
      : t('browse.min_filled_pages');

  const stats = useMemo(() => {
    return activeGroup
      ? computeCardStats(activeGroup.cards)
      : { total: 0, newCount: 0, learning: 0, review: 0, mastered: 0 };
  }, [activeGroup]);

  const nonEmptyCategories = useMemo(() => {
    const keyMap: Record<SrsCardCategory, keyof typeof stats> = {
      new: 'newCount',
      learning: 'learning',
      review: 'review',
      mastered: 'mastered',
    };
    return SRS_CATEGORY_ORDER.filter((cat) => (stats[keyMap[cat]] as number) > 0);
  }, [stats]);

  const toggleCategory = useCallback((category: SrsCardCategory) => {
    setActiveCategories((prev) => toggleCategoryReducer(prev, category, nonEmptyCategories));
  }, [nonEmptyCategories]);

  const filtered = useMemo(() => {
    if (!activeGroup) {
      return [];
    }

    let cards: Flashcard[];

    cards = activeGroup.cards.filter((card) =>
      shouldShowCard(activeCategories, getCardCategory(card.srsState))
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

  const addCard = () => {
    startCreate();
  };

  const handleBack = safeBack;

  if (store.isLoading) {
    return <LoadingState />;
  }

  if (!activeGroup) {
    return <GroupNotFound onBack={handleBack} />;
  }

  const listHeaderContent = (
    <View style={styles.listHeader}>
      <AnimatedSection order={0}>
        <View style={styles.header}>
          <Text style={[styles.cardCountText, { color: theme.colors.onSurfaceVariant }]}>
            {t('dashboard.cards_count', { count: stats.total })}
          </Text>
        </View>
      </AnimatedSection>

      <AnimatedSection order={1}>
        <BrowseSearchBar search={search} setSearch={setSearch} t={t} />
      </AnimatedSection>

      <AnimatedSection order={2}>
        <View style={styles.progressBarSection}>
          <SegmentedProgressBar
            stats={stats}
            showLegend
            selectedCategories={activeCategories}
            onCategoryToggle={toggleCategory}
          />
        </View>
      </AnimatedSection>
    </View>
  );

  return (
    <AppScreen
      title={activeGroup.name}
      onBack={handleBack}
      scroll={false}
      contentStyle={styles.screenContent}
    >
      <View style={styles.listSection}>
        <FlashcardList
          cards={filtered}
          group={activeGroup}
          onStartEdit={startEdit}
          onDelete={setDeleteCardId}
          readOnly={isReadOnly}
          t={t}
          style={styles.list}
          className={Platform.OS === 'web' ? 'raycoll-stable-scrollbar' : undefined}
          contentContainerStyle={styles.listContainer}
          emptyLabel={t('browse.no_cards')}
          listHeaderContent={listHeaderContent}
          itemAnimationOffset={3}
        />
      </View>

      {!isReadOnly && (
        <Animated.View entering={ZoomIn.springify().delay(getEnterDelay(5))}>
          <FAB icon="plus" style={styles.fab} onPress={addCard} accessibilityLabel="Add flashcard" />
        </Animated.View>
      )}

      {!isReadOnly && (
        <DeleteFlashcardDialog
          visible={!!deleteCardId}
          onDismiss={() => setDeleteCardId(null)}
          onConfirm={confirmDeleteCard}
          t={t}
        />
      )}

      {!isReadOnly && (
        <EditFlashcardDialog
          visible={!!editingId}
          group={activeGroup}
          editPages={editPages}
          setEditPages={setEditPages}
          onSave={() => {
            if (canSaveEdit) {
              saveEdit();
            }
          }}
          onCancel={cancelEdit}
          saveDisabled={!canSaveEdit}
          validationMessage={!canSaveEdit && editingId ? minPagesMessage : undefined}
          t={t}
        />
      )}
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  listHeader: {
    gap: TOKENS.spacing.lg,
    marginBottom: TOKENS.spacing.sm,
  },
  header: {
    alignItems: 'flex-end',
  },
  cardCountText: {
    fontSize: TOKENS.typography.size.xs,
  },
  progressBarSection: {},
  screenContent: {
    flex: 1,
    minHeight: 0,
  },
  list: {
    flex: 1,
    width: '100%',
    ...Platform.select({
      web: {
        scrollbarGutter: 'stable both-edges',
      },
    }),
  },
  listSection: {
    flex: 1,
    minHeight: 0,
  },
  listContainer: {
    width: '100%',
    maxWidth: TOKENS.layout.maxWidth,
    alignSelf: 'center',
    gap: TOKENS.spacing.lg,
    paddingBottom: 100,
    paddingHorizontal: TOKENS.spacing.lg,
  },
  fab: {
    position: 'absolute',
    margin: TOKENS.spacing.lg,
    right: TOKENS.spacing.sm,
    bottom: TOKENS.spacing.sm,
    borderRadius: TOKENS.radius.xl,
  },
});
