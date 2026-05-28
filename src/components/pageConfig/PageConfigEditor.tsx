import React from 'react';
import { StyleSheet, View } from 'react-native';
import { IconButton, Text } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import { AppSelect } from '../AppSelect';
import { AppFormRow } from '../forms/AppFormRow';
import { AppTextInput } from '../forms/AppTextInput';

type PageConfigEditorMode = 'import' | 'settings';

const SEP_CHAR_LABELS: Record<string, string> = {
  tab: 'Tab',
  semicolon: ';',
  comma: ',',
  pipe: '|',
};

interface PageConfigEditorProps {
  mode: PageConfigEditorMode;
  pageCount: number;
  pageNames: string[];
  pageLanguages: string[];
  popularLangs: { code: string; label: string }[];
  t: TranslationFn;
  onPageCountChange: (count: number) => void;
  onPageNameChange: (index: number, value: string) => void;
  onPageNameBlur?: (index: number) => void;
  onPageLanguageChange: (index: number, value: string) => void;
  onMovePage?: (index: number, direction: -1 | 1) => void;
  sepKey?: string;
  onSepKeyChange?: (key: string, customSep?: string) => void;
  customSep?: string;
}

export function PageConfigEditor({
  mode,
  pageCount,
  pageNames,
  pageLanguages,
  popularLangs,
  t,
  onPageCountChange,
  onPageNameChange,
  onPageNameBlur,
  onPageLanguageChange,
  onMovePage,
  sepKey,
  onSepKeyChange,
  customSep = '',
}: PageConfigEditorProps) {
  const languageOptions = popularLangs.map((lang) => ({
    label: t(`lang.${lang.code}`),
    value: lang.code,
  }));

  const separatorOptions = [
    { label: 'Tab', value: 'tab' },
    { label: ';', value: 'semicolon' },
    { label: ',', value: 'comma' },
    { label: '|', value: 'pipe' },
    { label: t('import.sep.custom'), value: 'custom' },
  ];

  const hasSeparator = sepKey !== undefined && onSepKeyChange !== undefined;

  return (
    <View style={styles.container}>
      {hasSeparator ? (
        <AppFormRow style={styles.pageRow}>
          <AppTextInput
            label={t('import.separator')}
            value={sepKey === 'custom' ? customSep : ''}
            onChangeText={(val) => {
              onSepKeyChange!('custom', val);
            }}
            editable={sepKey === 'custom'}
            style={styles.pageNameInput}
            accessibilityLabel="Custom separator input"
          />
          <View style={styles.languageInput}>
            <AppSelect
              value={sepKey}
              options={separatorOptions}
              onChange={(key) => {
                onSepKeyChange!(key, key === 'custom' ? customSep : undefined);
              }}
              accessibilityLabel="Select CSV separator"
            />
          </View>
        </AppFormRow>
      ) : null}

      <View style={styles.counterRow}>
        <Text>{t('import.pages_count')}</Text>
        <View style={styles.counterButtons}>
          <IconButton
            icon="minus-box"
            size={28}
            style={styles.counterButton}
            onPress={() => onPageCountChange(Math.max(2, pageCount - 1))}
            disabled={pageCount <= 2}
            accessibilityLabel={`${mode} decrease page count`}
          />
          <Text style={styles.counterText}>{pageCount}</Text>
          <IconButton
            icon="plus-box"
            size={28}
            style={styles.counterButton}
            onPress={() => onPageCountChange(Math.min(5, pageCount + 1))}
            disabled={pageCount >= 5}
            accessibilityLabel={`${mode} increase page count`}
          />
        </View>
      </View>

      {Array.from({ length: pageCount }).map((_, index) => (
        <AppFormRow key={index} style={styles.pageRow}>
          {onMovePage ? (
            <View style={styles.sortButtons}>
              <IconButton
                icon="arrow-up"
                size={18}
                style={styles.sortButton}
                onPress={() => onMovePage(index, -1)}
                disabled={index === 0}
                accessibilityLabel={`Move page ${index + 1} up`}
              />
              <IconButton
                icon="arrow-down"
                size={18}
                style={styles.sortButton}
                onPress={() => onMovePage(index, 1)}
                disabled={index === pageCount - 1}
                accessibilityLabel={`Move page ${index + 1} down`}
              />
            </View>
          ) : null}

          <AppTextInput
            label={t('import.page_label', { index: index + 1 })}
            value={pageNames[index] ?? ''}
            onChangeText={(value) => onPageNameChange(index, value)}
            onBlur={onPageNameBlur ? () => onPageNameBlur(index) : undefined}
            style={styles.pageNameInput}
            accessibilityLabel={`Edit page ${index + 1} name`}
          />

          <View style={styles.languageInput}>
            <AppSelect
              value={pageLanguages[index] ?? ''}
              options={languageOptions}
              onChange={(value) => onPageLanguageChange(index, value)}
              accessibilityLabel={`Select language for page ${index + 1}`}
            />
          </View>
        </AppFormRow>
      ))}
    </View>
  );
}

// SEP_CHAR_LABELS exported for tests / other consumers
export { SEP_CHAR_LABELS };

const styles = StyleSheet.create({
  container: {
    gap: TOKENS.spacing.sm,
  },
  counterRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: TOKENS.spacing.sm,
  },
  counterButtons: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  counterButton: {
    margin: 0,
  },
  counterText: {
    fontSize: 18,
    fontWeight: '700',
    minWidth: TOKENS.touchTarget.compact,
    textAlign: 'center',
  },
  pageRow: {
    alignItems: 'center',
  },
  sortButtons: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  sortButton: {
    margin: 0,
    width: TOKENS.touchTarget.compact,
    height: TOKENS.touchTarget.compact,
  },
  pageNameInput: {
    flex: 1,
  },
  languageInput: {
    width: 160,
  },
});
