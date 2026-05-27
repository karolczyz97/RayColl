import React, { useState } from 'react';
import { View, StyleSheet } from 'react-native';
import { Button, Switch, Text } from 'react-native-paper';
import type { Flashcard, FlashcardGroup } from '../../types/models';
import { getVisiblePageNames } from '../../store/selectors/pages';
import type { TranslationFn } from '../../i18n';
import { AppTextInput } from '../forms/AppTextInput';
import { TOKENS } from '../../theme/tokens';

interface Props {
  card: Flashcard;
  group: FlashcardGroup;
  editPages: string[];
  setEditPages: React.Dispatch<React.SetStateAction<string[]>>;
  onSave: () => void;
  onCancel: () => void;
  t: TranslationFn;
}

export function EditFlashcardForm({
  card,
  group,
  editPages,
  setEditPages,
  onSave,
  onCancel,
  t,
}: Props) {
  const [showHidden, setShowHidden] = useState(false);

  const activeCount = group.activePageCount ?? group.pageNames.length;
  const totalCount = editPages.length;
  const hasHiddenPages = totalCount > activeCount;

  const displayCount = showHidden ? totalCount : activeCount;

  const toggleLabel =
    t('browse.show_hidden_pages') === 'browse.show_hidden_pages'
      ? 'Show hidden pages'
      : t('browse.show_hidden_pages');

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

      <View style={styles.editingActions}>
        <Button onPress={onCancel}>{t('btn.cancel')}</Button>
        <Button mode="contained" onPress={onSave}>
          {t('btn.save')}
        </Button>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: TOKENS.spacing.lg,
    gap: TOKENS.spacing.md,
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  editInput: {
    minHeight: TOKENS.control.compactHeight,
  },
  editingActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: TOKENS.spacing.sm,
  },
});
