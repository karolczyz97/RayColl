import React from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import type { TranslationFn } from '../../i18n';
import { SectionCard } from '../../components/layout/SectionCard';
import { PageConfigEditor } from '../../components/pageConfig/PageConfigEditor';

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
  t: TranslationFn;
  style?: StyleProp<ViewStyle>;
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
  t,
  style,
}: ImportConfigCardProps) {
  return (
    <SectionCard title={t('settings.pages_config')} style={style}>
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
