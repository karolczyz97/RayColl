import React from 'react';
import type { TranslationFn } from '@/i18n';
import { AppTextInput } from '@/components/forms/AppTextInput';

interface Props {
  deckName: string;
  onChangeText: (v: string) => void;
  onBlur: () => void;
  t: TranslationFn;
}

export function DeckNameSection({ deckName, onChangeText, onBlur, t }: Props) {
  return (
    <AppTextInput
      label={t('settings.rename_label')}
      value={deckName}
      onChangeText={onChangeText}
      onBlur={onBlur}
    />
  );
}
