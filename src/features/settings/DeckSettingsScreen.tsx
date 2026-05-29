import React from 'react';
import { StyleSheet, View } from 'react-native';
import { IconButton, Portal } from 'react-native-paper';
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

  if (!activeGroup) {
    return null;
  }

  const leftColumnContent = (
    <>
      <AnimatedSection index={0}>
        <SectionCard>
          <DeckNameSection
            deckName={deckName}
            onChangeText={setDeckName}
            onBlur={handleNameBlur}
            t={t}
          />
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection index={1}>
        <SectionCard>
          <StudyScopeSection
            studyFilter={activeGroup.studyFilter}
            onFilterChange={onFilterChange}
            t={t}
          />
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection index={2}>
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
        </SectionCard>
      </AnimatedSection>
    </>
  );

  const rightColumnContent = (
    <>
      <AnimatedSection index={3}>
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
        <AnimatedSection index={4}>
          <StudyModeStepsEditor
            activeMode={activeMode}
            isDefaultMode={isDefaultMode}
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
      action={
        <IconButton
          icon="delete-outline"
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
});
