import React from 'react';
import { useI18n } from '@/i18n';
import { AppTextInput } from '@/components/forms/AppTextInput';

interface DeckNameSectionProps {
  deckName: string;
  onChangeText: (v: string) => void;
  onBlur: () => void;
}

export function DeckNameSection({ deckName, onChangeText, onBlur }: DeckNameSectionProps) {
  const { t } = useI18n();

  return (
    <AppTextInput
      label={t('settings.rename_label')}
      value={deckName}
      onChangeText={onChangeText}
      onBlur={onBlur}
    />
  );
}
