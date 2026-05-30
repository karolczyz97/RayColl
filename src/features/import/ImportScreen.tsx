import React from 'react';
import { FlatList, StyleSheet, View } from 'react-native';
import { Button } from 'react-native-paper';
import { router } from 'expo-router';
import { DeleteFlashcardDialog } from '../../components/browse/DeleteFlashcardDialog';
import { AppSnackbar } from '../../components/feedback/AppSnackbar';
import { SyncStatusBanner } from '../../components/feedback/SyncStatusBanner';
import { AnimatedSection } from '../../components/layout/AnimatedSection';
import { AppScreen } from '../../components/layout/AppScreen';
import { LoadingState } from '../../components/layout/LoadingState';
import { POPULAR_LANGS } from '../../constants/languages';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useResponsiveLayout } from '../../hooks/useResponsiveLayout';
import { useI18n } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import { ImportConfigCard } from './ImportConfigCard';
import { ImportPreviewSection } from './ImportPreviewSection';
import { ImportSourceCard } from './ImportSourceCard';
import { useImportDeckDraft } from './useImportDeckDraft';

export function ImportScreen() {
  const { t } = useI18n();
  const store = useFlashcardStore();
  const { isExpanded, contentMaxWidth } = useResponsiveLayout();
  const draft = useImportDeckDraft();

  if (store.isLoading) {
    return <LoadingState />;
  }

  const topCardsOrder = isExpanded
    ? { config: 0, preview: 1, source: 0 }
    : { config: 1, preview: 2, source: 0 };

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
      t={t}
    />
  );

  const topCards = (
    <>
      {isExpanded ? (
        <View style={styles.row}>
          <AnimatedSection order={topCardsOrder.source} style={styles.column}>
            <ImportSourceCard
              name={draft.name}
              rawText={draft.rawText}
              onNameChange={draft.setName}
              onRawTextChange={draft.handleTextChange}
              onPickFile={draft.handlePickFile}
              t={t}
            />
          </AnimatedSection>
          <AnimatedSection order={topCardsOrder.config} style={styles.column}>
            {renderConfigCard()}
          </AnimatedSection>
        </View>
      ) : (
        <View style={styles.singleColumn}>
          <AnimatedSection order={topCardsOrder.source}>
            <ImportSourceCard
              name={draft.name}
              rawText={draft.rawText}
              onNameChange={draft.setName}
              onRawTextChange={draft.handleTextChange}
              onPickFile={draft.handlePickFile}
              t={t}
            />
          </AnimatedSection>
          <AnimatedSection order={topCardsOrder.config}>{renderConfigCard()}</AnimatedSection>
        </View>
      )}

      {draft.cards.length > 0 ? (
        <AnimatedSection order={topCardsOrder.preview} style={styles.previewSection}>
          <ImportPreviewSection
            cards={draft.cards}
            group={draft.previewGroup}
            editingId={draft.editingId}
            editPages={draft.editPages}
            setEditPages={draft.setEditPages}
            onSave={draft.saveEdit}
            onCancel={draft.cancelEdit}
            onStartEdit={draft.startEdit}
            onDelete={draft.setDeleteCardId}
            t={t}
          />
        </AnimatedSection>
      ) : null}
    </>
  );

  return (
    <AppScreen
      title={t('import.title')}
      onBack={() => router.back()}
      maxWidth={contentMaxWidth}
      scroll={false}
      contentStyle={styles.screenContent}
    >
      <SyncStatusBanner
        syncStatus={store.syncStatus}
        lastSyncError={store.lastSyncError}
        lastPersistenceError={store.lastPersistenceError}
        lastStoreError={store.lastStoreError}
        t={t}
      />

      <FlatList
        data={[]}
        keyExtractor={(item: { key: string }) => item.key}
        style={styles.list}
        renderItem={null}
        ListHeaderComponent={topCards}
        contentContainerStyle={styles.listContent}
      />

      <View style={styles.floatingButtonContainer} pointerEvents="box-none">
        <Button
          mode="contained"
          onPress={() => void draft.submitImport()}
          disabled={!draft.name.trim() || draft.cards.length === 0 || draft.isImporting}
          loading={draft.isImporting}
          style={styles.importButton}
          accessibilityLabel="Perform flashcard import button"
        >
          {t('import.btn', { count: draft.cards.length })}
        </Button>
      </View>

      <DeleteFlashcardDialog
        visible={!!draft.deleteCardId}
        onDismiss={() => draft.setDeleteCardId(null)}
        onConfirm={draft.confirmDeleteCard}
        t={t}
      />

      <AppSnackbar
        visible={!!draft.importError}
        message={draft.importError ? t(draft.importError) : ''}
        onDismiss={() => draft.setImportError('')}
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  listContent: {
    gap: TOKENS.spacing.lg,
    paddingBottom: TOKENS.control.height + TOKENS.spacing.xl * 2 + TOKENS.spacing.xxl,
  },
  screenContent: {
    flex: 1,
    minHeight: 0,
  },
  list: {
    flex: 1,
  },
  row: {
    flexDirection: 'row',
    gap: TOKENS.spacing.xl,
    width: '100%',
    alignItems: 'stretch',
  },
  column: {
    flex: 1,
  },
  singleColumn: {
    width: '100%',
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
  previewSection: {
    maxWidth: 860,
    alignSelf: 'center',
    width: '100%',
  },
});
