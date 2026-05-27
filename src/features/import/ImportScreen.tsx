import React from 'react';
import { FlatList, StyleSheet, View } from 'react-native';
import { Button, Portal } from 'react-native-paper';
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
  const { isCompact, isExpanded, contentMaxWidth, formMaxWidth } = useResponsiveLayout();
  const draft = useImportDeckDraft();

  if (store.isLoading) {
    return <LoadingState />;
  }

  const renderConfigCard = () => (
    <ImportConfigCard
      sepKey={draft.sepKey}
      onSepKeyChange={draft.handleSepKeyChange}
      pageCount={draft.pageCount}
      pageNames={draft.pageNames}
      pageLanguages={draft.pageLangs}
      onPageCountChange={draft.handlePageCountChange}
      onPageNameChange={(index, value) => {
        draft.setPageNames((prev) => {
          const next = [...prev];
          next[index] = value;
          return next;
        });
      }}
      onPageLanguageChange={(index, value) => {
        draft.setPageLangs((prev) => {
          const next = [...prev];
          next[index] = value;
          return next;
        });
      }}
      popularLangs={POPULAR_LANGS}
      t={t}
    />
  );

  const topCards = (
    <>
      {isExpanded ? (
        <View style={styles.row}>
          <AnimatedSection index={0} style={styles.column}>
            <ImportSourceCard
              name={draft.name}
              rawText={draft.rawText}
              onNameChange={draft.setName}
              onRawTextChange={draft.handleTextChange}
              onPickFile={draft.handlePickFile}
              t={t}
            />
          </AnimatedSection>
          <AnimatedSection index={1} style={styles.column}>
            {renderConfigCard()}
          </AnimatedSection>
        </View>
      ) : (
        <View style={styles.singleColumn}>
          <AnimatedSection index={0}>
            <ImportSourceCard
              name={draft.name}
              rawText={draft.rawText}
              onNameChange={draft.setName}
              onRawTextChange={draft.handleTextChange}
              onPickFile={draft.handlePickFile}
              t={t}
            />
          </AnimatedSection>
          <AnimatedSection index={1}>{renderConfigCard()}</AnimatedSection>
        </View>
      )}

      {draft.cards.length > 0 ? (
        <AnimatedSection index={2}>
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
    >
      <FlatList
        data={[{ key: 'content' }]}
        keyExtractor={(item) => item.key}
        renderItem={() => (
          <>
            <AnimatedSection index={3}>
              <SyncStatusBanner
                syncStatus={store.syncStatus}
                lastSyncError={store.lastSyncError}
                lastPersistenceError={store.lastPersistenceError}
                lastStoreError={store.lastStoreError}
                t={t}
              />
            </AnimatedSection>
            <AnimatedSection
              index={4}
              style={[styles.importButtonWrapper, !isCompact && { maxWidth: formMaxWidth }]}
            >
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
            </AnimatedSection>
          </>
        )}
        ListHeaderComponent={topCards}
        contentContainerStyle={styles.listContent}
      />

      <Portal>
        <DeleteFlashcardDialog
          visible={!!draft.deleteCardId}
          onDismiss={() => draft.setDeleteCardId(null)}
          onConfirm={draft.confirmDeleteCard}
          t={t}
        />
      </Portal>

      <AppSnackbar
        visible={!!draft.importError}
        message={draft.importError}
        onDismiss={() => draft.setImportError('')}
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  listContent: {
    gap: TOKENS.spacing.lg,
    paddingBottom: TOKENS.spacing.xxl * 2,
  },
  row: {
    flexDirection: 'row',
    gap: TOKENS.spacing.xl,
    width: '100%',
    alignItems: 'flex-start',
  },
  column: {
    flex: 1,
  },
  singleColumn: {
    width: '100%',
    gap: TOKENS.spacing.lg,
  },
  importButtonWrapper: {
    width: '100%',
    alignSelf: 'center',
  },
  importButton: {
    borderRadius: TOKENS.radius.pill,
    minHeight: TOKENS.control.height,
    justifyContent: 'center',
  },
});
