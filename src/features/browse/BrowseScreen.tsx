import React from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { ZoomIn } from 'react-native-reanimated';
import { Text, useTheme } from 'react-native-paper';
import { AppFloatingActionButton } from '@/components/AppFloatingActionButton';
import { ActionConfirmDialog } from '@/components/dialogs/ActionConfirmDialog';
import { EditFlashcardDialog } from '@/components/browse/EditFlashcardDialog';
import { BrowseSearchBar } from '@/components/browse/BrowseSearchBar';
import { GroupNotFound } from '@/components/GroupNotFound';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { AppScreen } from '@/components/layout/AppScreen';
import { LoadingState } from '@/components/layout/LoadingState';
import { SegmentedProgressBar } from '@/components/SegmentedProgressBar';
import { useStableScrollbarProps } from '@/hooks/useStableScrollbarProps';
import { TOKENS, getTokenMotionEnterDelay } from '@/theme/tokens';
import { FlashcardList } from '@/features/flashcards/FlashcardList';
import { useBrowseController } from './useBrowseController';

export function BrowseScreen() {
  const theme = useTheme();
  const scrollbarProps = useStableScrollbarProps();
  const controller = useBrowseController();
  const {
    activeCategories,
    activeGroup,
    cardCountLabel,
    confirmDeleteCard,
    deleteCardId,
    editingId,
    editPages,
    emptyLabel,
    filteredCards,
    handleBack,
    isLoading,
    isReadOnly,
    markEditPageTouched,
    minPagesMessage,
    saveEdit,
    search,
    setDeleteCardId,
    setEditPages,
    setSearch,
    showEditValidation,
    startEdit,
    startCreate,
    cancelEdit,
    stats,
    toggleCategory,
  } = controller;

  const addCard = () => {
    startCreate();
  };

  if (isLoading) {
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
            {cardCountLabel}
          </Text>
        </View>
      </AnimatedSection>

      <AnimatedSection order={1}>
        <BrowseSearchBar search={search} setSearch={setSearch} />
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
          cards={filteredCards}
          group={activeGroup}
          onStartEdit={startEdit}
          onDelete={setDeleteCardId}
          readOnly={isReadOnly}
          style={[styles.list, scrollbarProps.style]}
          className={scrollbarProps.className}
          contentContainerStyle={styles.listContainer}
          emptyLabel={emptyLabel}
          listHeaderContent={listHeaderContent}
          itemAnimationOffset={3}
        />
      </View>

      {!isReadOnly && (
        <Animated.View entering={ZoomIn.springify().delay(getTokenMotionEnterDelay(5))}>
          <AppFloatingActionButton
            icon="plus"
            style={styles.fab}
            onPress={addCard}
            accessibilityLabel="Add flashcard"
          />
        </Animated.View>
      )}

      {!isReadOnly && (
        <ActionConfirmDialog
          visible={!!deleteCardId}
          onDismiss={() => setDeleteCardId(null)}
          onConfirm={confirmDeleteCard}
          titleKey="browse.delete_card"
          messageKey="dialog.delete.desc"
          confirmLabelKey="btn.delete"
          cancelLabelKey="btn.cancel"
          destructive
        />
      )}

      {!isReadOnly && (
        <EditFlashcardDialog
          visible={!!editingId}
          group={activeGroup}
          editPages={editPages}
          onPagesChange={(pages) => setEditPages(pages)}
          onPageBlur={markEditPageTouched}
          onSave={saveEdit}
          onCancel={cancelEdit}
          validationMessage={showEditValidation ? minPagesMessage : undefined}
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
    paddingBottom: TOKENS.spacing.xxl * 3,
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
