import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, IconButton, useTheme } from 'react-native-paper';
import { getTopBarColors } from '@/theme/semanticColors';
import { DeckNameSection } from '@/components/settings/DeckNameSection';
import { StudyScopeSection } from '@/components/settings/StudyScopeSection';
import { CardOrderSection } from '@/components/settings/CardOrderSection';
import { StudyModeSelector } from '@/components/settings/StudyModeSelector';
import { ActionConfirmDialog } from '@/components/dialogs/ActionConfirmDialog';
import { TOKENS } from '@/theme/tokens';
import { ARCHIVE_RETENTION_DAYS } from '@/constants/archive';
import { AppScreen } from '@/components/layout/AppScreen';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { SectionCard } from '@/components/layout/SectionCard';
import { PageConfigEditor } from '@/components/pageConfig/PageConfigEditor';
import { PagesReorderDialog } from './PagesReorderDialog';

export function DeckSettingsScreen(
  controller: ReturnType<typeof import('./useDeckSettingsController').useDeckSettingsController>,
) {
  const { fg: topBarFg } = getTopBarColors(useTheme());
  const {
    activeGroup,
    colNames,
    deckName,
    deckNameError,
    archiveDialogOpen,
    handleColBlur,
    handleArchiveGroup,
    handleNameBlur,
    handleCreateMode,
    handleEditMode,
    isCompact,
    useTwoColumnLayout,
    movePageSetting,
    movePageSettingAll,
    onFilterChange,
    onCardOrderChange,
    onModeChange,
    pageCount,
    pageNameErrors,
    popularLangs,
    responsiveLayout,
    setColNames,
    setDeckName,
    setArchiveDialogOpen,
    store,
    t,
    updatePageLangValue,
    adjustPageCount,
    handleBack,
  } = controller;

  const [showReorderDialog, setShowReorderDialog] = useState(false);

  if (!activeGroup) {
    return null;
  }

  const storedPageCount = activeGroup.pageNames.length;
  const hasHiddenColumns = storedPageCount > (activeGroup.activePageCount ?? 0);

  const sectionOrder = useTwoColumnLayout
    ? { name: 0, pageConfig: 3, scope: 1, cardOrder: 2, modeSelector: 1 }
    : { name: 0, scope: 1, cardOrder: 2, pageConfig: 3, modeSelector: 4 };

  const leftColumnContent = (
    <>
      <AnimatedSection order={sectionOrder.name}>
        <SectionCard>
          <DeckNameSection
            deckName={deckName}
            onChangeText={setDeckName}
            onBlur={handleNameBlur}
            errorMessage={deckNameError ? t('validation.required') : undefined}
          />
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection order={sectionOrder.scope}>
        <SectionCard>
          <StudyScopeSection
            studyFilter={activeGroup.studyFilter}
            onFilterChange={onFilterChange}
          />
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection order={sectionOrder.cardOrder}>
        <SectionCard>
          <CardOrderSection
            cardOrder={activeGroup.cardOrder}
            onCardOrderChange={onCardOrderChange}
          />
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection order={sectionOrder.pageConfig}>
        <SectionCard title={t('settings.pages_config')}>
          <PageConfigEditor
            mode="settings"
            pageCount={pageCount}
            pageNames={colNames}
            pageLanguages={activeGroup.pageLanguages}
            popularLangs={popularLangs}
            onPageCountChange={adjustPageCount}
            onPageNameChange={(index, value) => {
              setColNames((prev) => {
                const next = [...prev];
                next[index] = value;
                return next;
              });
            }}
            onPageNameBlur={handleColBlur}
            pageNameErrors={pageNameErrors}
            pageNameErrorMessage={t('validation.required')}
            onPageLanguageChange={updatePageLangValue}
            onMovePage={movePageSetting}
          />
          {hasHiddenColumns ? (
            <Button
              mode="text"
              compact
              onPress={() => setShowReorderDialog(true)}
              style={styles.reorderButton}
            >
              {t('settings.reorder_columns')}
            </Button>
          ) : null}
        </SectionCard>
      </AnimatedSection>
    </>
  );

  const rightColumnContent = (
    <AnimatedSection order={sectionOrder.modeSelector}>
      <SectionCard>
        <StudyModeSelector
          activeModeId={activeGroup.activeModeId}
          onModeChange={onModeChange}
          studyModes={store.studyModes}
          onCreateMode={handleCreateMode}
          onEditMode={handleEditMode}
        />
      </SectionCard>
    </AnimatedSection>
  );

  return (
    <AppScreen
      title={t('settings.title', { name: activeGroup.name })}
      onBack={handleBack}
      right={
        <IconButton
          icon="archive-arrow-down-outline"
          iconColor={topBarFg}
          onPress={() => setArchiveDialogOpen(true)}
          accessibilityLabel={t('settings.archive_btn')}
        />
      }
      maxWidth={responsiveLayout.contentMaxWidth}
    >
      {useTwoColumnLayout ? (
        <View style={styles.row}>
          <View style={styles.column}>{leftColumnContent}</View>
          <View style={styles.column}>{rightColumnContent}</View>
        </View>
      ) : (
        <View style={[styles.singleColumn, !isCompact && styles.singleColumnWide]}>
          {leftColumnContent}
          {rightColumnContent}
        </View>
      )}

      <PagesReorderDialog
        visible={showReorderDialog}
        onDismiss={() => setShowReorderDialog(false)}
        activeGroup={activeGroup}
        colNames={colNames}
        setColNames={setColNames}
        popularLangs={popularLangs}
        handleColBlur={handleColBlur}
        pageNameErrors={pageNameErrors}
        pageNameErrorMessage={t('validation.required')}
        updatePageLangValue={updatePageLangValue}
        movePageSettingAll={movePageSettingAll}
      />

      <ActionConfirmDialog
        visible={archiveDialogOpen}
        onDismiss={() => setArchiveDialogOpen(false)}
        onConfirm={handleArchiveGroup}
        titleKey="dialog.archive.title"
        messageKey="dialog.archive.confirm"
        messageReplacements={{ days: ARCHIVE_RETENTION_DAYS }}
        confirmLabelKey="btn.archive"
        cancelLabelKey="btn.cancel"
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    gap: TOKENS.spacing.xl,
    width: '100%',
    alignItems: 'flex-start',
  },
  column: {
    flex: 1,
    gap: TOKENS.spacing.lg,
  },
  singleColumn: {
    width: '100%',
    gap: TOKENS.spacing.lg,
  },
  singleColumnWide: {
    maxWidth: TOKENS.layout.deckSettingsSingleColumnMaxWidth,
    alignSelf: 'center',
  },
  reorderButton: {
    alignSelf: 'flex-start',
    marginTop: TOKENS.spacing.sm,
  },
});
