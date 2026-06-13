import React from 'react';
import { FlatList, StyleSheet, View } from 'react-native';
import { Button } from 'react-native-paper';
import { navigateUp } from '@/utils/navigation';
import { ActionConfirmDialog } from '@/components/dialogs/ActionConfirmDialog';
import { AppSnackbar } from '@/components/feedback/AppSnackbar';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { AppScreen } from '@/components/layout/AppScreen';
import { LoadingState } from '@/components/layout/LoadingState';
import { POPULAR_LANGS } from '@/constants/languages';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { useStableScrollbarProps } from '@/hooks/useStableScrollbarProps';
import { ImportConfigCard } from './ImportConfigCard';
import { ImportPreviewSection } from './ImportPreviewSection';
import { ImportSourceCard } from './ImportSourceCard';
import { useImportDeckDraft } from './useImportDeckDraft';
import { MAX_VISIBLE_PAGE_COUNT, MAX_STORED_PAGE_COUNT } from '@/constants/pages';

export function ImportScreen() {
  const { t } = useI18n();
  const store = useFlashcardStore();
  const scrollbarProps = useStableScrollbarProps();
  const draft = useImportDeckDraft();

  if (store.isLoading) {
    return <LoadingState />;
  }

  const renderConfigCard = () => (
    <ImportConfigCard
      sepKey={draft.sepKey}
      onSepKeyChange={draft.handleSepKeyChange}
      customSep={draft.customSep}
      firstRowIsHeader={draft.firstRowIsHeader}
      pageCount={draft.pageCount}
      pageNames={draft.pageNames}
      pageLanguages={draft.pageLangs}
      onFirstRowIsHeaderChange={draft.handleHeaderToggle}
      onPageCountChange={draft.handlePageCountChange}
      onPageNameChange={draft.handlePageNameChange}
      onPageLanguageChange={(index, value) => {
        draft.setPageLangs((prev) => {
          const next = [...prev];
          next[index] = value;
          return next;
        });
      }}
      onMovePage={draft.handleMovePage}
      popularLangs={POPULAR_LANGS}
      minPageCount={draft.rawColumnCount}
      activePageCount={Math.min(draft.pageCount, MAX_VISIBLE_PAGE_COUNT)}
    />
  );

  const topCards = (
    <>
      <View style={styles.singleColumn}>
        <AnimatedSection order={0}>
          <ImportSourceCard
            name={draft.name}
            rawText={draft.rawText}
            onNameChange={draft.setName}
            onRawTextChange={draft.handleTextChange}
            onNameBlur={draft.handleNameBlur}
            onRawTextBlur={draft.handleSourceBlur}
            nameError={draft.showNameRequiredError ? t('validation.required') : undefined}
            rawTextError={draft.showSourceRequiredError ? t('import.validation.source_required') : undefined}
            onPickFile={draft.handlePickFile}
            onPaste={draft.handlePaste}
          />
        </AnimatedSection>
        <AnimatedSection order={1}>{renderConfigCard()}</AnimatedSection>
      </View>

      {draft.cards.length > 0 ? (
        <AnimatedSection order={2} style={styles.singleColumn}>
          <ImportPreviewSection
            cards={draft.cards}
            group={draft.previewGroup}
            editingId={draft.editingId}
            editPages={draft.editPages}
            onPagesChange={(pages) => draft.setEditPages(pages)}
            onSave={draft.saveEdit}
            onCancel={draft.cancelEdit}
            onStartEdit={draft.startEdit}
            onDelete={draft.setDeleteCardId}
          />
        </AnimatedSection>
      ) : null}
    </>
  );

  return (
    <AppScreen
      title={t('import.title')}
      onBack={navigateUp}
      scroll={false}
      contentStyle={styles.screenContent}
    >
      <FlatList
        data={[]}
        keyExtractor={(item: { key: string }) => item.key}
        style={[styles.list, scrollbarProps.style]}
        className={scrollbarProps.className}
        renderItem={null}
        ListHeaderComponent={topCards}
        contentContainerStyle={styles.listContent}
      />

      <View style={styles.floatingButtonContainer} pointerEvents="box-none">
        <Button
          mode="contained"
          onPress={() => void draft.submitImport()}
          disabled={draft.isImporting}
          loading={draft.isImporting}
          style={styles.importButton}
          accessibilityLabel="Perform flashcard import button"
        >
          {t('import.btn', { count: draft.cards.length })}
        </Button>
      </View>

      <ActionConfirmDialog
        visible={!!draft.deleteCardId}
        onDismiss={() => draft.setDeleteCardId(null)}
        onConfirm={draft.confirmDeleteCard}
        titleKey="browse.delete_card"
        messageKey="dialog.delete.desc"
        confirmLabelKey="btn.delete"
        cancelLabelKey="btn.cancel"
        destructive
      />

      <AppSnackbar
        visible={!!draft.importError && !draft.errorDismissed}
        message={draft.importError ? t(draft.importError, { max: MAX_STORED_PAGE_COUNT }) : ''}
        onDismiss={draft.dismissImportError}
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  listContent: {
    gap: TOKENS.spacing.lg,
    // Top breathing room on the scroll container itself (matches Browse), so
    // removing any header card never leaves content stuck to the top bar.
    paddingTop: TOKENS.spacing.lg,
    paddingHorizontal: TOKENS.spacing.lg,
    paddingBottom: TOKENS.control.height + TOKENS.spacing.xl * 2 + TOKENS.spacing.xxl,
  },
  screenContent: {
    flex: 1,
    minHeight: 0,
  },
  list: {
    flex: 1,
  },
  singleColumn: {
    width: '100%',
    maxWidth: TOKENS.layout.formMaxWidth,
    alignSelf: 'center',
    gap: TOKENS.spacing.lg,
  },
  floatingButtonContainer: {
    position: 'absolute',
    bottom: TOKENS.spacing.xl,
    left: TOKENS.spacing.xl,
    right: TOKENS.spacing.xl,
    alignItems: 'center',
  },
  importButton: {
    maxWidth: 320,
    width: '100%',
    borderRadius: TOKENS.radius.pill,
    minHeight: TOKENS.control.height,
    justifyContent: 'center',
  },
});
