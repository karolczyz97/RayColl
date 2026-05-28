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

  const renderConfigCard = () => (
    <ImportConfigCard
      style={styles.card}
      sepKey={draft.sepKey}
      onSepKeyChange={draft.handleSepKeyChange}
      customSep={draft.customSep}
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
      onMovePage={draft.handleMovePage}
      popularLangs={POPULAR_LANGS}
      t={t}
    />
  );

  const topCards = (
    <View style={styles.topCardsContainer}>
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
        <AnimatedSection index={2} style={styles.previewSection}>
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
    </View>
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
  card: {
    flex: 1,
  },
  topCardsContainer: {
    gap: TOKENS.spacing.lg,
    width: '100%',
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
