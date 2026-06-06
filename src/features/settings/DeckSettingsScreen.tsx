import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, IconButton, useTheme } from 'react-native-paper';
import { getTopBarColors } from '@/theme/semanticColors';
import { DeckNameSection } from '@/components/settings/DeckNameSection';
import { StudyScopeSection } from '@/components/settings/StudyScopeSection';
import { StudyModeSelector } from '@/components/settings/StudyModeSelector';
import { StudyModeStepsEditor } from '@/components/settings/StudyModeStepsEditor';
import { CreateStudyModeSection } from '@/components/settings/CreateStudyModeSection';
import { ActionConfirmDialog } from '@/components/dialogs/ActionConfirmDialog';
import { AddStepDialog } from '@/components/settings/AddStepDialog';
import { TOKENS } from '@/theme/tokens';
import { ARCHIVE_RETENTION_DAYS } from '@/constants/archive';
import { AppScreen } from '@/components/layout/AppScreen';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { SectionCard } from '@/components/layout/SectionCard';
import { PageConfigEditor } from '@/components/pageConfig/PageConfigEditor';
import { PagesReorderDialog } from './PagesReorderDialog';

export function DeckSettingsScreen(controller: ReturnType<typeof import('./useDeckSettingsController').useDeckSettingsController>) {
  const { fg: topBarFg } = getTopBarColors(useTheme());
  const {
    activeGroup,
    activeMode,
    colNames,
    confirmAddStep,
    creatingMode,
    customSteps,
    deckName,
    archiveDialogOpen,
    handleColBlur,
    handleArchiveGroup,
    handleNameBlur,
    isCompact,
    isDefaultMode,
    hasCustomSteps,
    useTwoColumnLayout,
    movePageSetting,
    movePageSettingAll,
    moveStep,
    newModeName,
    newMs,
    newPageIdx,
    newStepType,
    newThreshold,
    onFilterChange,
    onModeChange,
    openCreateModeDialog,
    pageCount,
    popularLangs,
    responsiveLayout,
    saveCustomMode,
    setColNames,
    setCustomSteps,
    setDeckName,
    setArchiveDialogOpen,
    setEditingModeId,
    setNewModeName,
    setNewMs,
    setNewPageIdx,
    setNewStepType,
    setNewThreshold,
    setStepDialogOpen,
    stepDialogOpen,
    stepLabels,
    formatStepSummary,
    store,
    t,
    updatePageLangValue,
    adjustPageCount,
    closeCreateModeDialog,
    deleteStep,
    addStepToMode,
    resetMode,
    handleBack,
  } = controller;

  const [showReorderDialog, setShowReorderDialog] = useState(false);

  if (!activeGroup) {
    return null;
  }

  const storedPageCount = activeGroup.pageNames.length;
  const hasHiddenColumns = storedPageCount > (activeGroup.activePageCount ?? 0);

  const sectionOrder = useTwoColumnLayout
    ? { name: 0, pageConfig: 2, scope: 1, modeEditor: 1, modeSelector: 0 }
    : { name: 0, pageConfig: 2, scope: 1, modeEditor: 4, modeSelector: 3 };

  const leftColumnContent = (
    <>
      <AnimatedSection order={sectionOrder.name}>
        <SectionCard>
          <DeckNameSection
            deckName={deckName}
            onChangeText={setDeckName}
            onBlur={handleNameBlur}
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
    <>
      <AnimatedSection order={sectionOrder.modeSelector}>
        <SectionCard>
          <StudyModeSelector
            activeModeId={activeGroup.activeModeId}
            onModeChange={onModeChange}
            studyModes={store.studyModes}
            onCreateMode={openCreateModeDialog}
          />
        </SectionCard>
      </AnimatedSection>

      {activeMode ? (
        <AnimatedSection order={sectionOrder.modeEditor}>
          <StudyModeStepsEditor
            activeMode={activeMode}
            isDefaultMode={isDefaultMode}
            hasCustomSteps={hasCustomSteps}
            moveStep={moveStep}
            deleteStep={deleteStep}
            addStepToMode={addStepToMode}
            onResetMode={resetMode}
            formatStepSummary={formatStepSummary}
          />
        </AnimatedSection>
      ) : null}
    </>
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

      <CreateStudyModeSection
        visible={creatingMode}
        onDismiss={closeCreateModeDialog}
        newModeName={newModeName}
        setNewModeName={setNewModeName}
        customSteps={customSteps}
        setCustomSteps={setCustomSteps}
        saveCustomMode={saveCustomMode}
        setStepDialogOpen={setStepDialogOpen}
        setEditingModeId={setEditingModeId}
        formatStepSummary={formatStepSummary}
      />

      <AddStepDialog
        visible={stepDialogOpen}
        onDismiss={() => setStepDialogOpen(false)}
        newStepType={newStepType}
        setNewStepType={setNewStepType}
        newPageIdx={newPageIdx}
        pageCount={pageCount}
        setNewPageIdx={setNewPageIdx}
        newMs={newMs}
        setNewMs={setNewMs}
        newThreshold={newThreshold}
        setNewThreshold={setNewThreshold}
        confirmAddStep={confirmAddStep}
        stepLabels={stepLabels}
      />

      <PagesReorderDialog
        visible={showReorderDialog}
        onDismiss={() => setShowReorderDialog(false)}
        activeGroup={activeGroup}
        colNames={colNames}
        setColNames={setColNames}
        popularLangs={popularLangs}
        handleColBlur={handleColBlur}
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
