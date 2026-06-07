import React from 'react';
import { HelperText } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { AppTextInput } from '@/components/forms/AppTextInput';

interface DeckNameSectionProps {
  deckName: string;
  onChangeText: (v: string) => void;
  onBlur: () => void;
  errorMessage?: string;
}

export function DeckNameSection({ deckName, onChangeText, onBlur, errorMessage }: DeckNameSectionProps) {
  const { t } = useI18n();

  return (
    <>
    <AppTextInput
      label={t('settings.rename_label')}
      value={deckName}
      onChangeText={onChangeText}
      onBlur={onBlur}
      error={!!errorMessage}
    />
      {errorMessage ? (
        <HelperText type="error" visible>
          {errorMessage}
        </HelperText>
      ) : null}
    </>
  );
}
