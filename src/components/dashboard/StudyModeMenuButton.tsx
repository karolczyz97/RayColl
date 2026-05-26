import React from 'react';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useI18n } from '../../i18n';
import type { FlashcardGroup } from '../../types/models';
import { AppSplitButton } from '../AppSplitButton';

interface Props {
  group: FlashcardGroup;
  onStudy: () => void;
  onModeChange: (modeId: string) => void;
}

export function StudyModeMenuButton({ group, onStudy, onModeChange }: Props) {
  const store = useFlashcardStore();
  const { t } = useI18n();

  const dueCount = store.getDueCards(group.id).length;
  const activeMode = store.studyModes.find((m) => m.id === group.activeModeId);

  const getModeName = (modeId: string, defaultName: string) => {
    const key = `mode.${modeId}.name`;
    const translated = t(key);
    return translated === key ? defaultName : translated;
  };

  const modeName = activeMode
    ? getModeName(activeMode.id, activeMode.name)
    : t('mode.classic.name');

  const options = store.studyModes.map((mode) => ({
    value: mode.id,
    label: getModeName(mode.id, mode.name),
  }));

  return (
    <AppSplitButton
      label={modeName}
      leadingIcon="play"
      disabled={dueCount === 0}
      onPress={onStudy}
      options={options}
      selectedValue={group.activeModeId}
      onSelect={onModeChange}
      accessibilityLabel={`Start study in ${modeName} mode`}
      menuAccessibilityLabel="Select study mode"
    />
  );
}
