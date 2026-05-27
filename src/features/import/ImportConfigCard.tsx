import React from 'react';
import { StyleSheet, View } from 'react-native';
import type { TranslationFn } from '../../i18n';
import { ImportSeparatorSelector } from '../../components/import/ImportSeparatorSelector';
import { SectionCard } from '../../components/layout/SectionCard';
import { PageConfigEditor } from '../../components/pageConfig/PageConfigEditor';
import { TOKENS } from '../../theme/tokens';

interface ImportConfigCardProps {
  sepKey: string;
  onSepKeyChange: (value: string) => void;
  pageCount: number;
  pageNames: string[];
  pageLanguages: string[];
  onPageCountChange: (count: number) => void;
  onPageNameChange: (index: number, value: string) => void;
  onPageLanguageChange: (index: number, value: string) => void;
  popularLangs: { code: string; label: string }[];
  t: TranslationFn;
}

export function ImportConfigCard({
  sepKey,
  onSepKeyChange,
  pageCount,
  pageNames,
  pageLanguages,
  onPageCountChange,
  onPageNameChange,
  onPageLanguageChange,
  popularLangs,
  t,
}: ImportConfigCardProps) {
  return (
    <SectionCard title={t('settings.pages_config')}>
      <View style={styles.headerRow}>
        <View />
        <ImportSeparatorSelector sepKey={sepKey} setSepKey={onSepKeyChange} t={t} />
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
      />
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: TOKENS.spacing.xs,
  },
});
