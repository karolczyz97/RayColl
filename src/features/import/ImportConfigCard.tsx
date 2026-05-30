import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Switch, Text } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';
import { SectionCard } from '../../components/layout/SectionCard';
import { PageConfigEditor } from '../../components/pageConfig/PageConfigEditor';
import { TOKENS } from '../../theme/tokens';

interface ImportConfigCardProps {
  sepKey: string;
  onSepKeyChange: (key: string, customSep?: string) => void;
  customSep: string;
  pageCount: number;
  pageNames: string[];
  pageLanguages: string[];
  onPageCountChange: (count: number) => void;
  onPageNameChange: (index: number, value: string) => void;
  onPageLanguageChange: (index: number, value: string) => void;
  onMovePage: (index: number, direction: -1 | 1) => void;
  popularLangs: { code: string; label: string }[];
  firstRowIsHeader: boolean;
  onHeaderToggle: () => void;
  t: TranslationFn;
}

export function ImportConfigCard({
  sepKey,
  onSepKeyChange,
  customSep,
  pageCount,
  pageNames,
  pageLanguages,
  onPageCountChange,
  onPageNameChange,
  onPageLanguageChange,
  onMovePage,
  popularLangs,
  firstRowIsHeader,
  onHeaderToggle,
  t,
}: ImportConfigCardProps) {
  return (
    <SectionCard title={t('settings.pages_config')}>
      <View style={styles.headerRow}>
        <Text style={styles.headerLabel}>{t('import.first_row_header')}</Text>
        <Switch value={firstRowIsHeader} onValueChange={onHeaderToggle} />
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
      />
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: TOKENS.spacing.sm,
    paddingBottom: TOKENS.spacing.sm,
  },
  headerLabel: {
    fontSize: TOKENS.typography.size.sm,
  },
});
