import React, { useState, useMemo, useRef } from 'react';
import { View, StyleSheet, ScrollView, Pressable } from 'react-native';
import { Text, TextInput, Card, Button, Chip, IconButton, Portal, Dialog, useTheme, FAB, ActivityIndicator } from 'react-native-paper';
import { useLocalSearchParams, router } from 'expo-router';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useI18n } from '../../i18n';
import { SegmentedProgressBar, computeCardStats } from '../../components/SegmentedProgressBar';
import type { Flashcard, SrsState } from '../../types/models';
import { getCardCategory, CATEGORIES } from '../../srs/srsEngine';
import { PageHeader } from '../../components/PageHeader';
import { GroupNotFound } from '../../components/GroupNotFound';
import { MaterialCommunityIcons } from '@expo/vector-icons';

type BrowseFilter = 'all' | 'learning' | 'review' | 'new' | 'mastered';

function srsChip(state: SrsState, t: (key: string) => string): { text: string; color: string } {
  const category = getCardCategory(state);
  const colors = {
    new: '#42a5f5',
    learning: '#ffa726',
    review: '#7c4dff',
    mastered: '#4caf50',
  };
  return {
    text: t(`srs.badge.${category}`),
    color: colors[category] || '#757575',
  };
}

function daysUntilReview(state: SrsState, t: (key: string) => string): string {
  if (state.state === 0) return t('srs.badge.new');
  const diff = state.nextReviewTimestamp - Date.now();
  if (diff <= 0) return t('srs.badge.now');
  const days = Math.ceil(diff / (1000 * 60 * 60 * 24));
  return `${days}d`;
}

function masteryPercent(state: SrsState): number {
  if (state.state === 0) return 0;
  return Math.min(100, Math.round(Math.min(state.stability / 30, 1) * 100));
}

export default function BrowsePage() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();

  const group = store.groups.find((g) => g.id === groupId);

  // Cache last valid group
  const lastValidGroupRef = useRef<typeof group>(undefined);
  if (group) {
    lastValidGroupRef.current = group;
  }
  const activeGroup = group || lastValidGroupRef.current;

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
    setEditingId(card.id);
    setEditPages([...card.pages]);
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
      store.updateFlashcard(activeGroup.id, { ...card, pages: editPages });
    }
    cancelEdit();
  };

  const addCard = () => {
    if (!activeGroup) return;
    const pages = activeGroup.pageNames.map(() => '');
    const id = store.addFlashcard(activeGroup.id, pages);
    setEditingId(id);
    setEditPages(pages);
  };

  const handleBack = () => {
    router.back();
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
    <View style={[styles.root, { backgroundColor: theme.colors.background }]}>
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
      <TextInput
        mode="outlined"
        placeholder={t('browse.search_placeholder')}
        value={search}
        onChangeText={setSearch}
        style={styles.searchBar}
        left={<TextInput.Icon icon="magnify" />}
      />

      {/* Filter Chips */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterChipsRow}>
        <Chip
          selected={browseFilter === 'all'}
          showSelectedCheck={false}
          onPress={() => setBrowseFilter('all')}
          style={styles.chip}
        >
          {`${t('filter.all')} (${stats.total})`}
        </Chip>
        <Chip
          selected={browseFilter === 'learning'}
          showSelectedCheck={false}
          onPress={() => setBrowseFilter('learning')}
          style={[styles.chip, browseFilter === 'learning' && { backgroundColor: '#ffa726' }]}
          selectedColor={browseFilter === 'learning' ? '#ffffff' : undefined}
        >
          {`${t('filter.learning')} (${stats.learning})`}
        </Chip>
        <Chip
          selected={browseFilter === 'review'}
          showSelectedCheck={false}
          onPress={() => setBrowseFilter('review')}
          style={[styles.chip, browseFilter === 'review' && { backgroundColor: '#7c4dff' }]}
          selectedColor={browseFilter === 'review' ? '#ffffff' : undefined}
        >
          {`${t('filter.review')} (${stats.review})`}
        </Chip>
        <Chip
          selected={browseFilter === 'new'}
          showSelectedCheck={false}
          onPress={() => setBrowseFilter('new')}
          style={[styles.chip, browseFilter === 'new' && { backgroundColor: '#42a5f5' }]}
          selectedColor={browseFilter === 'new' ? '#ffffff' : undefined}
        >
          {`${t('filter.new')} (${stats.newCount})`}
        </Chip>
        <Chip
          selected={browseFilter === 'mastered'}
          showSelectedCheck={false}
          onPress={() => setBrowseFilter('mastered')}
          style={[styles.chip, browseFilter === 'mastered' && { backgroundColor: '#4caf50' }]}
          selectedColor={browseFilter === 'mastered' ? '#ffffff' : undefined}
        >
          {`${t('filter.mastered')} (${stats.mastered})`}
        </Chip>
      </ScrollView>

      {/* Cards list */}
      <ScrollView contentContainerStyle={styles.listContainer}>
        {filtered.map((card) => {
          const srs = srsChip(card.srsState, t);
          const mastery = masteryPercent(card.srsState);
          const reviewIn = daysUntilReview(card.srsState, t);

          return (
            <Card key={card.id} style={styles.card} mode="outlined">
              {editingId === card.id ? (
                <View style={styles.editingContainer}>
                  {editPages.map((page, i) => (
                    <TextInput
                      key={i}
                      mode="outlined"
                      label={activeGroup.pageNames[i] || t('import.page_label', { index: i + 1 })}
                      value={page}
                      onChangeText={(text) => {
                        const next = [...editPages];
                        next[i] = text;
                        setEditPages(next);
                      }}
                      style={styles.editInput}
                    />
                  ))}
                  <View style={styles.editingActions}>
                    <Button onPress={cancelEdit}>{t('btn.cancel')}</Button>
                    <Button mode="contained" onPress={saveEdit}>
                      {t('btn.save')}
                    </Button>
                  </View>
                </View>
              ) : (
                <View>
                  <Card.Content style={styles.cardContent}>
                    {card.pages.map((page, i) => (
                      <View key={i} style={styles.pageContentRow}>
                        <Text variant="labelSmall" style={{ color: theme.colors.onSurfaceVariant }}>
                          {activeGroup.pageNames[i] || t('import.page_label', { index: i + 1 })}:
                        </Text>
                        <Text variant="bodyLarge" style={{ fontWeight: i === 0 ? 'bold' : 'normal' }}>
                          {page}
                        </Text>
                      </View>
                    ))}
                  </Card.Content>
                  <Card.Actions style={styles.cardActions}>
                    <View style={styles.cardActionsLeft}>
                      <Chip style={{ backgroundColor: srs.color }} selectedColor="#ffffff">
                        {srs.text}
                      </Chip>
                      {card.srsState.state > 0 && (
                        <View style={styles.statsIconsRow}>
                          <View style={styles.statIconItem}>
                            <MaterialCommunityIcons name="thumb-up-outline" size={14} color={theme.colors.outline} />
                            <Text variant="labelSmall" style={{ color: theme.colors.outline }}>
                              {mastery}%
                            </Text>
                          </View>
                          <View style={styles.statIconItem}>
                            <MaterialCommunityIcons name="sync" size={14} color={theme.colors.outline} />
                            <Text variant="labelSmall" style={{ color: theme.colors.outline }}>
                              {card.srsState.repetitions}
                            </Text>
                          </View>
                          <View style={styles.statIconItem}>
                            <MaterialCommunityIcons name="calendar-month-outline" size={14} color={theme.colors.outline} />
                            <Text variant="labelSmall" style={{ color: theme.colors.outline }}>
                              {reviewIn}
                            </Text>
                          </View>
                        </View>
                      )}
                    </View>
                    <View style={styles.cardActionsRight}>
                      <IconButton icon="pencil" size={20} iconColor={theme.colors.primary} onPress={() => startEdit(card)} />
                      <IconButton icon="delete" size={20} iconColor={theme.colors.error} onPress={() => setDeleteCardId(card.id)} />
                    </View>
                  </Card.Actions>
                </View>
              )}
            </Card>
          );
        })}
      </ScrollView>

      {/* FAB to add card */}
      <FAB icon="plus" style={styles.fab} onPress={addCard} />

      {/* Dialog confirmations */}
      <Portal>
        <Dialog visible={!!deleteCardId} onDismiss={() => setDeleteCardId(null)}>
          <Dialog.Title>{t('browse.delete_card')}</Dialog.Title>
          <Dialog.Content>
            <Text>{t('dialog.delete.desc')}</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setDeleteCardId(null)}>{t('btn.cancel')}</Button>
            <Button
              mode="contained"
              buttonColor={theme.colors.error}
              onPress={() => {
                if (deleteCardId) {
                  store.deleteFlashcard(groupId, deleteCardId);
                  setDeleteCardId(null);
                }
              }}
            >
              {t('btn.delete')}
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 48,
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
  searchBar: {
    marginBottom: 12,
    height: 48,
  },
  filterChipsRow: {
    flexDirection: 'row',
    gap: 8,
    height: 40,
    marginBottom: 16,
  },
  chip: {
    marginRight: 4,
    height: 32,
  },
  listContainer: {
    gap: 16,
    paddingBottom: 100,
  },
  card: {
    borderRadius: 16,
    overflow: 'hidden',
  },
  cardContent: {
    paddingBottom: 4,
    gap: 8,
  },
  pageContentRow: {
    gap: 2,
  },
  cardActions: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    justifyContent: 'space-between',
  },
  cardActionsLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  statsIconsRow: {
    flexDirection: 'row',
    gap: 8,
  },
  statIconItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
  },
  cardActionsRight: {
    flexDirection: 'row',
  },
  editingContainer: {
    padding: 16,
    gap: 12,
  },
  editInput: {
    height: 44,
  },
  editingActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 8,
  },
  fab: {
    position: 'absolute',
    margin: 16,
    right: 8,
    bottom: 8,
    borderRadius: 16,
  },
});
