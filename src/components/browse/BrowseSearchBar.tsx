import React from 'react';
import { StyleSheet } from 'react-native';
import { TextInput } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';

interface BrowseSearchBarProps {
  search: string;
  setSearch: (v: string) => void;
}

export function BrowseSearchBar({ search, setSearch }: BrowseSearchBarProps) {
  const { t } = useI18n();

  return (
    <TextInput
      mode="outlined"
      placeholder={t('browse.search_placeholder')}
      value={search}
      onChangeText={setSearch}
      style={styles.searchBar}
      left={<TextInput.Icon icon="magnify" />}
      outlineStyle={styles.outline}
      accessibilityLabel="Search flashcards input"
    />
  );
}

const styles = StyleSheet.create({
  searchBar: {},
  outline: {
    borderRadius: TOKENS.radius.md,
  },
});
