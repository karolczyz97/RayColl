import React from 'react';
import { View, StyleSheet } from 'react-native';
import { TextInput, Text, IconButton, Button, useTheme, Dialog } from 'react-native-paper';
import type { ModeStep } from '../../types/models';
import type { TranslationFn } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import { dialogStyles } from '../../theme/dialogStyles';

interface Props {
  visible: boolean;
  onDismiss: () => void;
  newModeName: string;
  setNewModeName: (value: string) => void;
  customSteps: ModeStep[];
  setCustomSteps: React.Dispatch<React.SetStateAction<ModeStep[]>>;
  saveCustomMode: () => void;
  setStepDialogOpen: (value: boolean) => void;
  setEditingModeId: (id: string | null) => void;
  t: TranslationFn;
  stepSummary: (step: ModeStep, t: TranslationFn) => string;
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
  t,
  stepSummary,
}: Props) {
  const theme = useTheme();

  return (
    <Dialog visible={visible} onDismiss={onDismiss} style={dialogStyles.dialog}>
      <Dialog.Title>{t('settings.create_mode_btn')}</Dialog.Title>
      <Dialog.Content>
        <TextInput
          mode="outlined"
          label={t('settings.new_mode_name')}
          value={newModeName}
          onChangeText={setNewModeName}
          style={styles.nameInput}
          outlineStyle={styles.inputOutline}
        />
        {customSteps.map((step, index) => (
          <View key={index} style={styles.customStepRow}>
            <Text style={styles.stepText}>{`${index + 1}. ${stepSummary(step, t)}`}</Text>
            <IconButton
              icon="arrow-up"
              size={16}
              onPress={() => {
                const steps = [...customSteps];
                [steps[index], steps[Math.max(0, index - 1)]] = [
                  steps[Math.max(0, index - 1)],
                  steps[index],
                ];
                setCustomSteps(steps);
              }}
              disabled={index === 0}
              accessibilityLabel={`Move step ${index + 1} up`}
            />
            <IconButton
              icon="arrow-down"
              size={16}
              onPress={() => {
                const steps = [...customSteps];
                const nextIndex = Math.min(steps.length - 1, index + 1);
                [steps[index], steps[nextIndex]] = [steps[nextIndex], steps[index]];
                setCustomSteps(steps);
              }}
              disabled={index === customSteps.length - 1}
              accessibilityLabel={`Move step ${index + 1} down`}
            />
            <IconButton
              icon="delete"
              size={16}
              iconColor={theme.colors.error}
              onPress={() => setCustomSteps((steps) => steps.filter((_, i) => i !== index))}
              accessibilityLabel={`Delete step ${index + 1}`}
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
      </Dialog.Content>
      <Dialog.Actions>
        <Button onPress={onDismiss}>{t('btn.cancel')}</Button>
        <Button
          mode="contained"
          onPress={saveCustomMode}
          disabled={!newModeName.trim() || customSteps.length === 0}
          accessibilityLabel="Save study mode"
        >
          {t('settings.save_mode_btn')}
        </Button>
      </Dialog.Actions>
    </Dialog>
  );
}

const styles = StyleSheet.create({
  nameInput: {
    height: TOKENS.control.height,
    marginBottom: TOKENS.spacing.md,
  },
  inputOutline: {
    borderRadius: TOKENS.control.borderRadius,
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
