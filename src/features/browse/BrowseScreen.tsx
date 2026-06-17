import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { ZoomIn } from 'react-native-reanimated';
import { IconButton, useTheme } from 'react-native-paper';
import { AppFloatingActionButton } from '@/components/AppFloatingActionButton';
import { ActionConfirmDialog } from '@/components/dialogs/ActionConfirmDialog';
import { EditFlashcardDialog } from '@/components/browse/EditFlashcardDialog';
import { BrowseSearchField } from '@/components/browse/BrowseSearchField';
import { ExpressiveButtonGroup } from '@/components/expressive';
import { GroupNotFound } from '@/components/GroupNotFound';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { AppScreen } from '@/components/layout/AppScreen';
import { LoadingState } from '@/components/layout/LoadingState';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { useStableScrollbarProps } from '@/hooks/useStableScrollbarProps';
import { TOKENS, getTokenMotionEnterDelay } from '@/theme/tokens';
import { FlashcardList } from '@/features/flashcards/FlashcardList';
import { useBrowseController } from './useBrowseController';
import { useBrowseFilterButtons } from './useBrowseFilterButtons';
import { useI18n } from '@/i18n';
import { AppMenuButton } from '@/components/AppMenuButton';

export function BrowseScreen() {
  const scrollbarProps = useStableScrollbarProps();
  const theme = useTheme();
  const [searchActive, setSearchActive] = useState(false);
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
    viewHidden,
    setViewHidden,
    hasHiddenPages,
  } = controller;

  const { t } = useI18n();

  const { showNavigationRail } = useResponsiveLayout();
  const filterButtons = useBrowseFilterButtons(stats, showNavigationRail);

  const addCard = () => {
    startCreate();
  };

  const exitSearch = () => {
    setSearchActive(false);
    setSearch('');
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

  // Native-style search: a magnify icon in the top bar that expands into a text
  // field (rendered in place of the title) when tapped; the back arrow exits it.
  const topBarRight = searchActive ? (
    search ? (
      <IconButton
        icon="close"
        iconColor={theme.colors.onBackground}
        onPress={() => setSearch('')}
        accessibilityLabel="Clear search"
      />
    ) : undefined
  ) : (
    <View style={styles.topActionsRow}>
      <IconButton
        icon="magnify"
        iconColor={theme.colors.onBackground}
        onPress={() => setSearchActive(true)}
        accessibilityLabel="Search"
      />
      {hasHiddenPages && (
        <AppMenuButton
          align="right"
          items={[
            {
              label: t('browse.show_hidden_pages'),
              selected: viewHidden,
              onPress: () => setViewHidden((v) => !v),
            },
          ]}
          renderAnchor={({ open }) => (
            <IconButton
              icon="dots-vertical"
              iconColor={theme.colors.onBackground}
              onPress={open}
              accessibilityLabel={t('browse.options')}
            />
          )}
        />
      )}
    </View>
  );

  return (
    <AppScreen
      title={searchActive ? undefined : `${activeGroup.name}  •  ${cardCountLabel}`}
      brand={searchActive ? <BrowseSearchField search={search} setSearch={setSearch} /> : undefined}
      onBack={searchActive ? exitSearch : handleBack}
      right={topBarRight}
      scroll={false}
      edges={['top', 'left', 'right', 'bottom']}
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
          viewHidden={viewHidden}
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
  topActionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.xs,
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
    paddingBottom: TOKENS.layout.fabClearance,
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
