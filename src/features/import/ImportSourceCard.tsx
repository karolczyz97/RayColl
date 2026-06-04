import React from 'react';
import { StyleSheet } from 'react-native';
import { Button } from 'react-native-paper';
import type { TranslationFn } from '@/i18n';
import { AppTextInput } from '@/components/forms/AppTextInput';
import { SectionCard } from '@/components/layout/SectionCard';
import { TOKENS } from '@/theme/tokens';

interface ImportSourceCardProps {
  name: string;
  rawText: string;
  onNameChange: (value: string) => void;
  onRawTextChange: (value: string) => void;
  onPickFile: () => void;
  onPaste?: () => void;
  t: TranslationFn;
}

export function ImportSourceCard({
  name,
  rawText,
  onNameChange,
  onRawTextChange,
  onPickFile,
  onPaste,
  t,
}: ImportSourceCardProps) {
  return (
    <SectionCard>
      <AppTextInput
        label={t('dashboard.name_label')}
        value={name}
        onChangeText={onNameChange}
        accessibilityLabel="Import deck name input"
      />
      <AppTextInput
        multiline
        label={t('import.csv_label')}
        value={rawText}
        onChangeText={onRawTextChange}
        onPaste={onPaste}
        placeholder={t('import.name_placeholder')}
        style={styles.csvInput}
        accessibilityLabel="CSV TSV raw text input"
      />
      <Button
        mode="outlined"
        icon="upload"
        onPress={onPickFile}
        style={{ borderRadius: TOKENS.radius.pill }}
        accessibilityLabel="Upload CSV, TXT, or MD file"
      >
        {t('import.upload_file')}
      </Button>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  csvInput: {
    minHeight: 120,
  },
});
