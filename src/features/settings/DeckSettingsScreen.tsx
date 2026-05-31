import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, Dialog, IconButton, Portal, useTheme } from 'react-native-paper';
import { getTopBarColors } from '../../theme/semanticColors';
import { DeckNameSection } from '../../components/settings/DeckNameSection';
import { StudyScopeSection } from '../../components/settings/StudyScopeSection';
import { StudyModeSelector } from '../../components/settings/StudyModeSelector';
import { StudyModeStepsEditor } from '../../components/settings/StudyModeStepsEditor';
import { CreateStudyModeSection } from '../../components/settings/CreateStudyModeSection';
import { DeleteDeckDialog } from '../../components/settings/DeleteDeckDialog';
import { AddStepDialog } from '../../components/settings/AddStepDialog';
import { TOKENS } from '../../theme/tokens';
import { AppScreen } from '../../components/layout/AppScreen';
import { AnimatedSection } from '../../components/layout/AnimatedSection';
import { SectionCard } from '../../components/layout/SectionCard';
import { PageConfigEditor } from '../../components/pageConfig/PageConfigEditor';

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
    deleteConfirmText,
    deleteDialogOpen,
    handleColBlur,
    handleDeleteGroup,
    handleNameBlur,
    isCompact,
    isDefaultMode,
    hasCustomSteps,
    isExpanded,
    movePageSetting,
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
    setDeleteConfirmText,
    setDeleteDialogOpen,
    setEditingModeId,
    setNewModeName,
    setNewMs,
    setNewPageIdx,
    setNewStepType,
    setNewThreshold,
    setStepDialogOpen,
    stepDialogOpen,
    stepLabels,
    stepSummary,
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

  const sectionOrder = isExpanded
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
            t={t}
          />
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection order={sectionOrder.scope}>
        <SectionCard>
          <StudyScopeSection
            studyFilter={activeGroup.studyFilter}
            onFilterChange={onFilterChange}
            t={t}
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
            t={t}
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
            t={t}
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
            t={t}
            stepSummary={stepSummary}
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
          icon="delete-outline"
          iconColor={topBarFg}
          onPress={() => setDeleteDialogOpen(true)}
          accessibilityLabel="Delete deck button"
        />
      }
      maxWidth={responsiveLayout.contentMaxWidth}
    >
      {isExpanded ? (
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

      <Portal>
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
          t={t}
          stepSummary={stepSummary}
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
          t={t}
          stepLabels={stepLabels}
        />

      </Portal>

      <Portal>
        <Dialog visible={showReorderDialog} onDismiss={() => setShowReorderDialog(false)} style={styles.reorderDialog}>
          <Dialog.Title>{t('settings.reorder_columns')}</Dialog.Title>
          <Dialog.ScrollArea>
            <View style={styles.reorderContent}>
              <PageConfigEditor
                mode="settings"
                showCounter={false}
                pageCount={storedPageCount}
                pageNames={activeGroup.pageNames}
                pageLanguages={activeGroup.pageLanguages}
                popularLangs={popularLangs}
                t={t}
                onPageCountChange={() => {}}
                onPageNameChange={(index, value) => {
                  setColNames((prev) => {
                    const next = [...prev];
                    next[index] = value;
                    return next;
                  });
                }}
                onPageLanguageChange={updatePageLangValue}
                onMovePage={movePageSetting}
              />
            </View>
          </Dialog.ScrollArea>
          <Dialog.Actions>
            <Button onPress={() => setShowReorderDialog(false)}>{t('btn.cancel')}</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>

      <DeleteDeckDialog
        visible={deleteDialogOpen}
        onDismiss={() => setDeleteDialogOpen(false)}
        deckName={activeGroup.name}
        deleteConfirmText={deleteConfirmText}
        setDeleteConfirmText={setDeleteConfirmText}
        onDelete={handleDeleteGroup}
        t={t}
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
    maxWidth: 600,
    alignSelf: 'center',
  },
  reorderButton: {
    alignSelf: 'flex-start',
    marginTop: TOKENS.spacing.sm,
  },
  reorderDialog: {
    maxHeight: '90%',
  },
  reorderContent: {
    minWidth: 320,
  },
});
