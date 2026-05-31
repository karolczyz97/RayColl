import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Switch, Text, useTheme } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';
import { SectionCard } from '../../components/layout/SectionCard';
import { PageConfigEditor } from '../../components/pageConfig/PageConfigEditor';
import { TOKENS } from '../../theme/tokens';

interface ImportConfigCardProps {
  sepKey: string;
  onSepKeyChange: (key: string, customSep?: string) => void;
  customSep: string;
  firstRowIsHeader: boolean;
  pageCount: number;
  pageNames: string[];
  pageLanguages: string[];
  onFirstRowIsHeaderChange: (value: boolean) => void;
  onPageCountChange: (count: number) => void;
  onPageNameChange: (index: number, value: string) => void;
  onPageLanguageChange: (index: number, value: string) => void;
  onMovePage: (index: number, direction: -1 | 1) => void;
  popularLangs: { code: string; label: string }[];
  t: TranslationFn;
  minPageCount?: number;
  activePageCount?: number;
}

export function ImportConfigCard({
  sepKey,
  onSepKeyChange,
  customSep,
  firstRowIsHeader,
  pageCount,
  pageNames,
  pageLanguages,
  onFirstRowIsHeaderChange,
  onPageCountChange,
  onPageNameChange,
  onPageLanguageChange,
  onMovePage,
  popularLangs,
  t,
  minPageCount,
  activePageCount,
}: ImportConfigCardProps) {
  const theme = useTheme();

  return (
    <SectionCard title={t('settings.pages_config')}>
      <View style={[styles.toggleRow, { borderBottomColor: theme.colors.outlineVariant }]}>
        <Text variant="labelLarge">{t('import.first_row_header')}</Text>
        <Switch
          value={firstRowIsHeader}
          onValueChange={onFirstRowIsHeaderChange}
          accessibilityLabel={t('import.first_row_header')}
        />
      </View>
      <PageConfigEditor
        mode="import"
        pageCount={pageCount}
        pageNames={pageNames}
        pageLanguages={pageLanguages}
        popularLangs={popularLangs}
        t={t}
        onPageCountChange={onPageCountChange}
        onPageNameChange={onPageNameChange}
        onPageLanguageChange={onPageLanguageChange}
        onMovePage={onMovePage}
        sepKey={sepKey}
        onSepKeyChange={onSepKeyChange}
        customSep={customSep}
        minPageCount={minPageCount}
        activePageCount={activePageCount}
      />
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  toggleRow: {
    alignItems: 'center',
    borderBottomWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: TOKENS.spacing.md,
    paddingBottom: TOKENS.spacing.md,
  },
});
