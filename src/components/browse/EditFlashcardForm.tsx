import React, { useState } from 'react';
import { View, StyleSheet } from 'react-native';
import { Button, TextInput, Switch, Text } from 'react-native-paper';
import type { Flashcard, FlashcardGroup } from '../../types/models';

interface Props {
  card: Flashcard;
  group: FlashcardGroup;
  editPages: string[];
  setEditPages: React.Dispatch<React.SetStateAction<string[]>>;
  onSave: () => void;
  onCancel: () => void;
  t: (key: string, replacements?: any) => string;
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

  const toggleLabel = t('browse.show_hidden_pages') === 'browse.show_hidden_pages'
    ? 'Show hidden pages'
    : t('browse.show_hidden_pages');

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
        <TextInput
          key={i}
          mode="outlined"
          label={group.pageNames[i] || t('import.page_label', { index: i + 1 })}
          value={page}
          onChangeText={(text) => {
            setEditPages((prev) => {
              const next = [...prev];
              next[i] = text;
              return next;
            });
          }}
          style={styles.editInput}
          outlineStyle={{ borderRadius: 12 }}
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
    padding: 16,
    gap: 12,
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  editInput: {
    height: 44,
  },
  editingActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 8,
  },
});
