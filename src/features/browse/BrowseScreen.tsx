import React, { useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { ZoomIn } from 'react-native-reanimated';
import { useTheme } from 'react-native-paper';
import { AppFloatingActionButton } from '@/components/AppFloatingActionButton';
import { ActionConfirmDialog } from '@/components/dialogs/ActionConfirmDialog';
import { EditFlashcardDialog } from '@/components/browse/EditFlashcardDialog';
import { BrowseSearchBar } from '@/components/browse/BrowseSearchBar';
import { ExpressiveButtonGroup } from '@/components/expressive';
import type { ExpressiveButtonGroupOption } from '@/components/expressive';
import { GroupNotFound } from '@/components/GroupNotFound';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { AppScreen } from '@/components/layout/AppScreen';
import { LoadingState } from '@/components/layout/LoadingState';
import { useI18n } from '@/i18n';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { useStableScrollbarProps } from '@/hooks/useStableScrollbarProps';
import type { SrsCardCategory } from '@/srs/srsEngine';
import { getReviewStatusColor } from '@/theme/semanticColors';
import { TOKENS, getTokenMotionEnterDelay } from '@/theme/tokens';
import { FlashcardList } from '@/features/flashcards/FlashcardList';
import { useBrowseController } from './useBrowseController';
import { useBrowseFilterButtons } from './useBrowseFilterButtons';

export function BrowseScreen() {
  const scrollbarProps = useStableScrollbarProps();
  const theme = useTheme();
  const { t, language } = useI18n();
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

  const { showNavigationRail } = useResponsiveLayout();
  const filterButtons = useBrowseFilterButtons(stats, showNavigationRail);

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
      <AnimatedSection order={1}>
        <BrowseSearchBar search={search} setSearch={setSearch} />
      </AnimatedSection>

      <AnimatedSection order={2}>
        <ExpressiveButtonGroup
          multiSelect
          value={activeCategories}
          onValueChange={toggleCategory}
          buttons={filterButtons}
          height={TOKENS.touchTarget.compact}
          accessibilityLabel="Filter by card status"
        />
      </AnimatedSection>
    </View>
  );

  return (
    <AppScreen
      title={`${activeGroup.name}  •  ${cardCountLabel}`}
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
  },

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
    // Vertical breathing room lives on the scroll container itself, so removing
    // any header element (count, search…) never leaves the content stuck to the
    // top bar.
    paddingTop: TOKENS.spacing.lg,
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
