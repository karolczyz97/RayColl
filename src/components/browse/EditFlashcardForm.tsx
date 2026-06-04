import React, { useState } from 'react';
import { View, StyleSheet } from 'react-native';
import { Switch, Text, useTheme } from 'react-native-paper';
import type { FlashcardGroup } from '@/types/models';
import { getVisiblePageNames } from '@/store/selectors/pages';
import type { TranslationFn } from '@/i18n';
import { AppTextInput } from '@/components/forms/AppTextInput';
import { TOKENS } from '@/theme/tokens';

interface Props {
  group: FlashcardGroup;
  editPages: string[];
  setEditPages: React.Dispatch<React.SetStateAction<string[]>>;
  validationMessage?: string;
  t: TranslationFn;
}

export function EditFlashcardForm({
  group,
  editPages,
  setEditPages,
  validationMessage,
  t,
}: Props) {
  const theme = useTheme();
  const [showHidden, setShowHidden] = useState(false);

  const activeCount = group.activePageCount;
  const totalCount = editPages.length;
  const hasHiddenPages = totalCount > activeCount;

  const displayCount = showHidden ? totalCount : activeCount;

  const toggleLabel = t('browse.show_hidden_pages');

  const displayNames = showHidden ? group.pageNames : getVisiblePageNames(group);

  return (
    <View style={styles.container}>
      {hasHiddenPages && (
        <View style={styles.toggleRow}>
          <Text variant="labelMedium">{toggleLabel}</Text>
          <Switch
            value={showHidden}
            onValueChange={setShowHidden}
            accessibilityLabel="Toggle hidden pages visibility"
          />
        </View>
      )}

      {editPages.slice(0, displayCount).map((page, i) => (
        <AppTextInput
          key={i}
          label={displayNames[i] || t('import.page_label', { index: i + 1 })}
          value={page}
          onChangeText={(text) => {
            setEditPages((prev) => {
              const next = [...prev];
              next[i] = text;
              return next;
            });
          }}
          style={styles.editInput}
          accessibilityLabel={`Edit page ${i + 1}`}
        />
      ))}

      {validationMessage ? (
        <Text variant="bodySmall" style={[styles.validationText, { color: theme.colors.error }]}>
          {validationMessage}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingTop: TOKENS.spacing.lg,
    paddingHorizontal: TOKENS.spacing.lg,
    gap: TOKENS.spacing.md,
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: TOKENS.spacing.xs,
  },
  editInput: {
    minHeight: TOKENS.control.compactHeight,
  },
  validationText: {
    marginTop: -TOKENS.spacing.xs,
  },
});
