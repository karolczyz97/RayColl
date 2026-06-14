import React, { useCallback, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import Animated, { ZoomIn } from 'react-native-reanimated';
import { Button, Dialog, HelperText, Portal, Text } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { getModeName } from '@/i18n/modeHelpers';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { useNavigationLeaveGuard } from '@/hooks/useNavigationLeaveGuard';
import { navigateUp } from '@/utils/navigation';
import { MAX_VISIBLE_PAGE_COUNT } from '@/constants/pages';
import { TOKENS, getTokenMotionEnterDelay } from '@/theme/tokens';
import { dialogStyles } from '@/theme/dialogStyles';
import { AppFloatingActionButton } from '@/components/AppFloatingActionButton';
import { AppScreen } from '@/components/layout/AppScreen';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { SectionCard } from '@/components/layout/SectionCard';
import { AppTextInput } from '@/components/forms/AppTextInput';
import { StudyModeStepsEditor } from '@/components/settings/StudyModeStepsEditor';
import { AddStepDialog } from '@/components/settings/AddStepDialog';
import { formatStepSummary, getModeCustomization } from './studyModeUtils';
import { useStudyModeDraftController } from './useStudyModeDraftController';

export function StudyModeDetailScreen() {
  const { modeId, selectForGroup } = useLocalSearchParams<{
    modeId: string;
    selectForGroup?: string;
  }>();
  const { t } = useI18n();
  const { formMaxWidth } = useResponsiveLayout();
  const [showValidation, setShowValidation] = useState(false);
  const [unsavedDialogVisible, setUnsavedDialogVisible] = useState(false);
  const [pendingNavigate, setPendingNavigate] = useState<(() => void) | null>(null);
  const [confirmedLeave, setConfirmedLeave] = useState(false);

  const draftController = useStudyModeDraftController({
    modeId,
    selectForGroup,
  });
  const {
    draft,
    isCreate,
    isDirty,
    isNameValid,
    areStepsValid,
    isValid,
    save,
    setName,
  } = draftController;

  const finishConfirmedLeave = useCallback((navigate: () => void) => {
    setUnsavedDialogVisible(false);
    setPendingNavigate(null);
    setConfirmedLeave(true);
    setTimeout(navigate, 0);
  }, []);

  const resolveLeave = useCallback(() => {
    if (selectForGroup) { router.back(); } else { navigateUp(); }
  }, [selectForGroup]);

  const handleSaveAndLeave = useCallback((navigate: () => void = resolveLeave) => {
    if (!isValid) {
      setShowValidation(true);
      return;
    }
    if (save()) {
      finishConfirmedLeave(navigate);
    }
  }, [finishConfirmedLeave, isValid, resolveLeave, save]);

  const handleAttemptLeave = useCallback((navigate: () => void) => {
    setPendingNavigate(() => navigate);
    setUnsavedDialogVisible(true);
  }, []);

  const navigateBack = useNavigationLeaveGuard({
    active: isDirty && !confirmedLeave,
    onAttemptLeave: handleAttemptLeave,
  });

  const handleDiscardChanges = useCallback(() => {
    finishConfirmedLeave(pendingNavigate ?? resolveLeave);
  }, [finishConfirmedLeave, pendingNavigate, resolveLeave]);

  const handleSaveDialog = useCallback(() => {
    handleSaveAndLeave(pendingNavigate ?? resolveLeave);
  }, [handleSaveAndLeave, pendingNavigate, resolveLeave]);

  const title = draft
    ? isCreate
      ? t('settings.create_mode_btn')
      : getModeName(t, draft.id, draft.name)
    : t('study_modes.title');

  if (!draft) {
    return (
      <AppScreen title={title} onBack={navigateBack} maxWidth={formMaxWidth}>
        {null}
      </AppScreen>
    );
  }

  const { isDefaultMode, hasCustomSteps } = getModeCustomization(draft);
  const showNameError = showValidation && !isNameValid;
  const showStepsError = showValidation && !areStepsValid;

  return (
    <AppScreen
      title={title}
      onBack={navigateBack}
      maxWidth={formMaxWidth}
      overlay={
        <Animated.View
          entering={ZoomIn.springify().delay(getTokenMotionEnterDelay(3))}
          style={styles.fabWrapper}
        >
          <AppFloatingActionButton
            icon="content-save"
            label={t('settings.save_mode_btn')}
            style={styles.fab}
            onPress={() => handleSaveAndLeave(resolveLeave)}
            accessibilityLabel={t('settings.save_mode_btn')}
          />
        </Animated.View>
      }
    >
      <AnimatedSection order={0}>
        <View style={styles.container}>
          {!draft.isBuiltIn && (
            <SectionCard title={t('study_modes.name_label')}>
              <AppTextInput
                label={t('study_modes.name_label')}
                value={draft.name}
                onChangeText={setName}
                error={showNameError}
              />
              {showNameError ? (
                <HelperText type="error" visible>
                  {t('validation.required')}
                </HelperText>
              ) : null}
            </SectionCard>
          )}

          <View>
            <StudyModeStepsEditor
              activeMode={draft}
              isDefaultMode={isDefaultMode}
              hasCustomSteps={hasCustomSteps}
              moveStep={draftController.moveStep}
              deleteStep={draftController.deleteStep}
              addStepToMode={draftController.addStepToMode}
              onResetMode={draftController.resetSteps}
              formatStepSummary={formatStepSummary}
            />
            {showStepsError ? (
              <HelperText type="error" visible>
                {t('settings.validation.mode_steps_required')}
              </HelperText>
            ) : null}
          </View>
        </View>
      </AnimatedSection>

      <AddStepDialog
        visible={draftController.stepDialogOpen}
        onDismiss={() => draftController.setStepDialogOpen(false)}
        newStepType={draftController.newStepType}
        setNewStepType={draftController.setNewStepType}
        newPageIdx={draftController.newPageIdx}
        pageCount={MAX_VISIBLE_PAGE_COUNT}
        setNewPageIdx={draftController.setNewPageIdx}
        newMs={draftController.newMs}
        setNewMs={draftController.setNewMs}
        newPauseMultiplier={draftController.newPauseMultiplier}
        setNewPauseMultiplier={draftController.setNewPauseMultiplier}
        newThreshold={draftController.newThreshold}
        setNewThreshold={draftController.setNewThreshold}
        newCondition={draftController.newCondition}
        setNewCondition={draftController.setNewCondition}
        confirmAddStep={draftController.confirmAddStep}
        stepLabels={draftController.stepLabels}
      />

      <Portal>
        <Dialog
          visible={unsavedDialogVisible}
          onDismiss={() => {
            setPendingNavigate(null);
            setUnsavedDialogVisible(false);
          }}
          style={dialogStyles.dialog}
        >
          <Dialog.Title>{t('study_modes.unsaved_title')}</Dialog.Title>
          <Dialog.Content>
            <Text selectable>{t('study_modes.unsaved_message')}</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={handleDiscardChanges}>{t('btn.discard')}</Button>
            <Button mode="contained" onPress={handleSaveDialog} disabled={!isValid}>
              {t('btn.save')}
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: TOKENS.spacing.lg,
    width: '100%',
  },
  fabWrapper: {
    position: 'absolute',
    right: TOKENS.spacing.sm,
    bottom: TOKENS.spacing.sm,
    margin: TOKENS.spacing.lg,
  },
  fab: {
    borderRadius: TOKENS.radius.lg,
  },
});
