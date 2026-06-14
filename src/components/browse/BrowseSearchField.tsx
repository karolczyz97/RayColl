import React from 'react';
import { Platform, StyleSheet, TextInput, type TextStyle } from 'react-native';
import { useTheme } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';

interface BrowseSearchFieldProps {
  search: string;
  setSearch: (v: string) => void;
}

const webFocusReset = Platform.select<TextStyle>({
  web: {
    outlineStyle: 'none',
    outlineWidth: 0,
  } as unknown as TextStyle,
});

/**
 * Borderless search input designed to live inside the top app bar (in place of
 * the screen title) once the user taps the search icon — the native Android
 * "icon expands into a field" pattern.
 */
export function BrowseSearchField({ search, setSearch }: BrowseSearchFieldProps) {
  const theme = useTheme();
  const { t } = useI18n();

  return (
    <TextInput
      autoFocus
      value={search}
      onChangeText={setSearch}
      placeholder={t('browse.search_placeholder')}
      placeholderTextColor={theme.colors.onSurfaceVariant}
      selectionColor={theme.colors.primary}
      returnKeyType="search"
      underlineColorAndroid="transparent"
      style={[styles.input, webFocusReset, { color: theme.colors.onBackground }]}
      accessibilityLabel="Search flashcards input"
    />
  );
}

const styles = StyleSheet.create({
  input: {
    flex: 1,
    fontSize: TOKENS.typography.size.lg,
    borderWidth: 0,
    paddingVertical: 0,
  },
});
