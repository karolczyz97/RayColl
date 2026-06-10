import React from 'react';
import { useStoreActionsContext } from '@/store/StoreContexts';
import { useI18n } from '@/i18n';
import type { FlashcardGroup, StudyMode } from '@/types/models';
import { AppSplitButton } from '@/components/AppSplitButton';
import { getModeName } from '@/i18n/modeHelpers';

interface StudyModeMenuButtonProps {
  group: FlashcardGroup;
  studyModes: StudyMode[];
  dueCount: number;
  onStudy: () => void;
}

export function StudyModeMenuButton({ group, studyModes, dueCount, onStudy }: StudyModeMenuButtonProps) {
  const actions = useStoreActionsContext();
  const { t } = useI18n();

  const activeMode = studyModes.find((m) => m.id === group.activeModeId);

  const modeName = activeMode
    ? getModeName(t, activeMode.id, activeMode.name)
    : t('mode.classic.name');

  const options = studyModes.map((mode) => ({
    value: mode.id,
    label: getModeName(t, mode.id, mode.name),
  }));

  return (
    <AppSplitButton
      label={modeName}
      leadingIcon="play"
      disabled={dueCount === 0}
      onPress={onStudy}
      options={options}
      selectedValue={group.activeModeId}
      onSelect={(modeId) => actions.setActiveStudyMode(group.id, modeId)}
      accessibilityLabel={`Start study in ${modeName} mode`}
      menuAccessibilityLabel="Select study mode"
    />
  );
}
