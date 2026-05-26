import React from 'react';
import { TextInput } from 'react-native-paper';

interface Props {
  deckName: string;
  onChangeText: (v: string) => void;
  onBlur: () => void;
  t: (key: string, replacements?: any) => string;
}

export function DeckNameSection({ deckName, onChangeText, onBlur, t }: Props) {
  return (
    <TextInput
      mode="outlined"
      label={t('settings.rename_label')}
      value={deckName}
      onChangeText={onChangeText}
      onBlur={onBlur}
      style={{ height: 48 }}
      outlineStyle={{ borderRadius: 12 }}
    />
  );
}
