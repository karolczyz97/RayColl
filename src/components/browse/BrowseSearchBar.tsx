import React from 'react';
import { StyleSheet } from 'react-native';
import { TextInput } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';
import { TOKENS } from '../../theme/tokens';

interface Props {
  search: string;
  setSearch: (v: string) => void;
  t: TranslationFn;
}

export function BrowseSearchBar({ search, setSearch, t }: Props) {
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
  searchBar: {
    marginBottom: TOKENS.spacing.md,
  },
  outline: {
    borderRadius: TOKENS.radius.md,
  },
});
