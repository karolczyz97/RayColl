import React from 'react';
import { StyleSheet } from 'react-native';
import { Button, HelperText } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { AppTextInput } from '@/components/forms/AppTextInput';
import { SectionCard } from '@/components/layout/SectionCard';
import { TOKENS } from '@/theme/tokens';

interface ImportSourceCardProps {
  name: string;
  rawText: string;
  onNameChange: (value: string) => void;
  onRawTextChange: (value: string) => void;
  onNameBlur?: () => void;
  onRawTextBlur?: () => void;
  onPickFile: () => void;
  onPaste?: () => void;
  nameError?: string;
  rawTextError?: string;
}

export function ImportSourceCard({
  name,
  rawText,
  onNameChange,
  onRawTextChange,
  onNameBlur,
  onRawTextBlur,
  onPickFile,
  onPaste,
  nameError,
  rawTextError,
}: ImportSourceCardProps) {
  const { t } = useI18n();

  return (
    <SectionCard>
      <AppTextInput
        label={t('dashboard.name_label')}
        value={name}
        onChangeText={onNameChange}
        onBlur={onNameBlur}
        error={!!nameError}
        accessibilityLabel="Import deck name input"
      />
      {nameError ? (
        <HelperText type="error" visible>
          {nameError}
        </HelperText>
      ) : null}
      <AppTextInput
        multiline
        label={t('import.csv_label')}
        value={rawText}
        onChangeText={onRawTextChange}
        onBlur={onRawTextBlur}
        onPaste={onPaste}
        placeholder={t('import.name_placeholder')}
        error={!!rawTextError}
        style={styles.csvInput}
        accessibilityLabel="CSV TSV raw text input"
      />
      {rawTextError ? (
        <HelperText type="error" visible>
          {rawTextError}
        </HelperText>
      ) : null}
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
