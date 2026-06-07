import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { HelperText, IconButton, Text } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { MIN_PAGE_COUNT, MAX_VISIBLE_PAGE_COUNT, MAX_STORED_PAGE_COUNT } from '@/constants/pages';
import { AppSelect } from '@/components/AppSelect';
import { AppFormRow } from '@/components/forms/AppFormRow';
import { AppTextInput } from '@/components/forms/AppTextInput';
import { TextEntryDialog } from '@/components/dialogs/TextEntryDialog';

type PageConfigEditorMode = 'import' | 'settings';

interface PageConfigEditorProps {
  mode: PageConfigEditorMode;
  pageCount: number;
  pageNames: string[];
  pageLanguages: string[];
  popularLangs: string[];
  onPageCountChange: (count: number) => void;
  onPageNameChange: (index: number, value: string) => void;
  onPageNameBlur?: (index: number) => void;
  onPageLanguageChange: (index: number, value: string) => void;
  onMovePage?: (index: number, direction: -1 | 1) => void;
  sepKey?: string;
  onSepKeyChange?: (key: string, customSep?: string) => void;
  customSep?: string;
  showCounter?: boolean;
  minPageCount?: number;
  activePageCount?: number;
  pageNameErrors?: boolean[];
  pageNameErrorMessage?: string;
}

export function PageConfigEditor({
  mode,
  pageCount,
  pageNames,
  pageLanguages,
  popularLangs,
  onPageCountChange,
  onPageNameChange,
  onPageNameBlur,
  onPageLanguageChange,
  onMovePage,
  sepKey,
  onSepKeyChange,
  customSep = '',
  showCounter = true,
  minPageCount,
  activePageCount,
  pageNameErrors,
  pageNameErrorMessage,
}: PageConfigEditorProps) {
  const { t } = useI18n();
  const [customDialogVisible, setCustomDialogVisible] = useState(false);
  const [customDraftValue, setCustomDraftValue] = useState('');

  const languageOptions = popularLangs.map((code) => ({
    label: t(`lang.${code}`),
    value: code,
  }));

  const separatorOptions = [
    { label: t('import.sep.tab'), value: 'tab' },
    { label: t('import.sep.semicolon'), value: 'semicolon' },
    { label: t('import.sep.comma'), value: 'comma' },
    { label: t('import.sep.pipe'), value: 'pipe' },
    ...(customSep ? [{ label: `${t('import.sep.current_custom')} (${customSep})`, value: 'custom' }] : []),
    { label: `${t('import.sep.set_custom')}…`, value: '__new_custom__' },
  ];

  const hasSeparator = sepKey !== undefined && onSepKeyChange !== undefined;
  const effectiveMin = mode === 'import' ? Math.max(MIN_PAGE_COUNT, minPageCount ?? MIN_PAGE_COUNT) : MIN_PAGE_COUNT;
  const maxPageCount = mode === 'import' ? MAX_STORED_PAGE_COUNT : MAX_VISIBLE_PAGE_COUNT;

  return (
    <View style={styles.container}>
      {showCounter ? (
        <View style={styles.counterRow}>
          <View style={styles.counterLeft}>
            <Text>{t('import.pages_count')}</Text>
            <View style={styles.counterButtons}>
              <IconButton
                icon="minus-box"
                size={28}
                style={styles.counterButton}
                onPress={() => onPageCountChange(Math.max(effectiveMin, pageCount - 1))}
                disabled={pageCount <= effectiveMin}
                accessibilityLabel={`${mode} decrease page count`}
              />
              <Text style={styles.counterText}>{pageCount}</Text>
              <IconButton
                icon="plus-box"
                size={28}
                style={styles.counterButton}
                onPress={() => onPageCountChange(Math.min(maxPageCount, pageCount + 1))}
                disabled={pageCount >= maxPageCount}
                accessibilityLabel={`${mode} increase page count`}
              />
            </View>
          </View>

          {hasSeparator ? (
            <View style={styles.separatorSelect}>
              <AppSelect
                value={sepKey ?? null}
                options={separatorOptions}
                onChange={(key) => {
                  if (key === '__new_custom__') {
                    setCustomDraftValue('');
                    setCustomDialogVisible(true);
                  } else {
                    onSepKeyChange!(key);
                  }
                }}
                accessibilityLabel="Select CSV separator"
              />
            </View>
          ) : null}
        </View>
      ) : null}

      {Array.from({ length: pageCount }).map((_, index) => {
        const isHidden = activePageCount !== undefined && index >= activePageCount;
        return (
        <AppFormRow key={index} style={[styles.pageRow, isHidden && { opacity: 0.4 }]}>
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

          <View style={styles.pageNameField}>
            <AppTextInput
              label={`${t('import.page_label', { index: index + 1 })}${isHidden ? t('import.page_hidden') : ''}`}
              value={pageNames[index] ?? ''}
              onChangeText={(value) => onPageNameChange(index, value)}
              onBlur={onPageNameBlur ? () => onPageNameBlur(index) : undefined}
              error={!!pageNameErrors?.[index]}
              accessibilityLabel={`Edit page ${index + 1} name`}
            />
            {pageNameErrors?.[index] && pageNameErrorMessage ? (
              <HelperText type="error" visible>
                {pageNameErrorMessage}
              </HelperText>
            ) : null}
          </View>

          <View style={styles.languageInput}>
            <AppSelect
              value={pageLanguages[index] ?? ''}
              options={languageOptions}
              onChange={(value) => onPageLanguageChange(index, value)}
              accessibilityLabel={`Select language for page ${index + 1}`}
            />
          </View>
        </AppFormRow>
        );
      })}

      {hasSeparator ? (
        <TextEntryDialog
          visible={customDialogVisible}
          title={t('import.separator')}
          value={customDraftValue}
          onChangeText={setCustomDraftValue}
          onDismiss={() => setCustomDialogVisible(false)}
          onConfirm={() => {
            onSepKeyChange!('custom', customDraftValue);
            setCustomDialogVisible(false);
          }}
          confirmLabel={t('btn.save')}
          cancelLabel={t('btn.cancel')}
          required
          requiredMessage={t('validation.required')}
        />
      ) : null}
    </View>
  );
}

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
  counterLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.sm,
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
    fontWeight: TOKENS.typography.weight.bold,
    minWidth: TOKENS.touchTarget.compact,
    textAlign: 'center',
  },
  separatorSelect: {
    width: TOKENS.layout.formSelectWidth,
  },
  pageRow: {
    alignItems: 'flex-end',
  },
  sortButtons: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'center',
  },
  sortButton: {
    margin: 0,
    width: TOKENS.touchTarget.compact,
    height: TOKENS.touchTarget.compact,
  },
  pageNameField: {
    flex: 1,
  },
  languageInput: {
    width: TOKENS.layout.formSelectWidth,
  },
});
