import React, { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { HelperText } from 'react-native-paper';
import type { StudyMode, ModeStep } from '@/types/models';
import { useI18n, type TranslationFn } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { SectionCard } from '@/components/layout/SectionCard';
import { AppTextInput } from '@/components/forms/AppTextInput';
import { StudyModeStepsEditor } from './StudyModeStepsEditor';

interface StudyModeEditorProps {
  mode: StudyMode;
  isDefaultMode: boolean;
  hasCustomSteps: boolean;
  moveStep: (mode: StudyMode, index: number, dir: -1 | 1) => void;
  deleteStep: (mode: StudyMode, index: number) => void;
  addStepToMode: (mode: StudyMode) => void;
  onResetMode: (mode: StudyMode) => void;
  onRenameMode: (mode: StudyMode, name: string) => void;
  formatStepSummary: (step: ModeStep, t: TranslationFn) => string;
}

/**
 * Wspólne „okno" edycji trybu nauki: zmiana nazwy (tylko tryby użytkownika —
 * nazwy wbudowanych pochodzą z tłumaczeń) + edytor kroków. Używane w
 * ustawieniach talii i na ekranie „Tryby nauki".
 */
export function StudyModeEditor({
  mode,
  isDefaultMode,
  hasCustomSteps,
  moveStep,
  deleteStep,
  addStepToMode,
  onResetMode,
  onRenameMode,
  formatStepSummary,
}: StudyModeEditorProps) {
  const { t } = useI18n();
  const [draftName, setDraftName] = useState(mode.name);
  const [nameTouched, setNameTouched] = useState(false);
  const modeId = mode.id;
  const modeName = mode.name;

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- local draft resets only when the edited mode (or its committed name) changes
    setDraftName(modeName);
    setNameTouched(false);
  }, [modeId, modeName]);

  const showNameError = nameTouched && !draftName.trim();

  const handleNameBlur = () => {
    setNameTouched(true);
    onRenameMode(mode, draftName);
  };

  return (
    <View style={styles.container}>
      {!mode.isBuiltIn && (
        <SectionCard>
          <AppTextInput
            label={t('study_modes.name_label')}
            value={draftName}
            onChangeText={setDraftName}
            onBlur={handleNameBlur}
            error={showNameError}
            style={styles.nameInput}
          />
          {showNameError ? (
            <HelperText type="error" visible>
              {t('validation.required')}
            </HelperText>
          ) : null}
        </SectionCard>
      )}
      <StudyModeStepsEditor
        activeMode={mode}
        isDefaultMode={isDefaultMode}
        hasCustomSteps={hasCustomSteps}
        moveStep={moveStep}
        deleteStep={deleteStep}
        addStepToMode={addStepToMode}
        onResetMode={onResetMode}
        formatStepSummary={formatStepSummary}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: TOKENS.spacing.lg,
    width: '100%',
  },
  nameInput: {
    marginBottom: 0,
  },
});
