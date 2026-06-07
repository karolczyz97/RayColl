import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, Button, Dialog, HelperText, Portal } from 'react-native-paper';
import type { ModeStep } from '@/types/models';
import { useI18n, type TranslationFn } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { dialogStyles } from '@/theme/dialogStyles';
import { AppTextInput } from '@/components/forms/AppTextInput';
import { swapElements } from '@/utils/array';
import { StepReorderControls } from './StepReorderControls';

interface CreateStudyModeSectionProps {
  visible: boolean;
  onDismiss: () => void;
  newModeName: string;
  setNewModeName: (value: string) => void;
  customSteps: ModeStep[];
  setCustomSteps: React.Dispatch<React.SetStateAction<ModeStep[]>>;
  saveCustomMode: () => void;
  setStepDialogOpen: (value: boolean) => void;
  setEditingModeId: (id: string | null) => void;
  formatStepSummary: (step: ModeStep, t: TranslationFn) => string;
}

export function CreateStudyModeSection({
  visible,
  onDismiss,
  newModeName,
  setNewModeName,
  customSteps,
  setCustomSteps,
  saveCustomMode,
  setStepDialogOpen,
  setEditingModeId,
  formatStepSummary,
}: CreateStudyModeSectionProps) {
  const { t } = useI18n();
  const [nameTouched, setNameTouched] = React.useState(false);
  const [submitAttempted, setSubmitAttempted] = React.useState(false);
  const showNameError = (nameTouched || submitAttempted) && !newModeName.trim();
  const showStepsError = submitAttempted && customSteps.length === 0;

  const resetValidation = () => {
    setNameTouched(false);
    setSubmitAttempted(false);
  };

  const handleSave = () => {
    setSubmitAttempted(true);
    if (!newModeName.trim() || customSteps.length === 0) {
      return;
    }

    resetValidation();
    saveCustomMode();
  };

  const handleDismiss = () => {
    resetValidation();
    onDismiss();
  };

  return (
    <Portal>
      <Dialog visible={visible} onDismiss={handleDismiss} style={dialogStyles.dialog}>
        <Dialog.Title>{t('settings.create_mode_btn')}</Dialog.Title>
        <Dialog.Content>
          <AppTextInput
            label={t('settings.new_mode_name')}
            value={newModeName}
            onChangeText={setNewModeName}
            onBlur={() => setNameTouched(true)}
            error={showNameError}
            style={styles.nameInput}
          />
          {showNameError ? (
            <HelperText type="error" visible>
              {t('validation.required')}
            </HelperText>
          ) : null}
          {customSteps.map((step, index) => (
            <View key={step.id ?? index} style={styles.customStepRow}>
              <Text style={styles.stepText}>{`${index + 1}. ${formatStepSummary(step, t)}`}</Text>
              <StepReorderControls
                index={index}
                isFirst={index === 0}
                isLast={index === customSteps.length - 1}
                onMoveUp={() => setCustomSteps((steps) => swapElements(steps, index, index - 1))}
                onMoveDown={() => setCustomSteps((steps) => swapElements(steps, index, index + 1))}
                onDelete={() => setCustomSteps((steps) => steps.filter((_, i) => i !== index))}
              />
            </View>
          ))}
          <View style={styles.addStepAction}>
            <Button
              icon="plus"
              onPress={() => {
                setEditingModeId(null);
                setStepDialogOpen(true);
              }}
              accessibilityLabel="Add new step"
            >
              {t('settings.add_step_btn')}
            </Button>
          </View>
          {showStepsError ? (
            <HelperText type="error" visible>
              {t('settings.validation.mode_steps_required')}
            </HelperText>
          ) : null}
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={handleDismiss}>{t('btn.cancel')}</Button>
          <Button
            mode="contained"
            onPress={handleSave}
            accessibilityLabel="Save study mode"
          >
            {t('settings.save_mode_btn')}
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

const styles = StyleSheet.create({
  nameInput: {
    marginBottom: TOKENS.spacing.md,
  },
  customStepRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: TOKENS.spacing.xs,
  },
  stepText: {
    flex: 1,
  },
  addStepAction: {
    flexDirection: 'row',
    marginTop: TOKENS.spacing.md,
  },
});
