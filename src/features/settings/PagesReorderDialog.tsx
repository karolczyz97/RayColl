import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, Dialog, Portal } from 'react-native-paper';
import { PageConfigEditor } from '@/components/pageConfig/PageConfigEditor';
import { TOKENS } from '@/theme/tokens';
import { useI18n } from '@/i18n';
import type { FlashcardGroup } from '@/types/models';

interface PagesReorderDialogProps {
  visible: boolean;
  onDismiss: () => void;
  activeGroup: FlashcardGroup;
  colNames: string[];
  setColNames: React.Dispatch<React.SetStateAction<string[]>>;
  popularLangs: string[];
  handleColBlur: (index: number) => void;
  updatePageLangValue: (index: number, value: string) => void;
  movePageSettingAll: (index: number, direction: -1 | 1) => void;
}

export function PagesReorderDialog({
  visible,
  onDismiss,
  activeGroup,
  colNames,
  setColNames,
  popularLangs,
  handleColBlur,
  updatePageLangValue,
  movePageSettingAll,
}: PagesReorderDialogProps) {
  const { t } = useI18n();
  const storedPageCount = activeGroup.pageNames.length;

  return (
    <Portal>
      <Dialog visible={visible} onDismiss={onDismiss} style={styles.dialog}>
        <Dialog.Title>{t('settings.reorder_columns')}</Dialog.Title>
        <Dialog.ScrollArea>
          <View style={styles.content}>
            <PageConfigEditor
              mode="settings"
              showCounter={false}
              pageCount={storedPageCount}
              pageNames={activeGroup.pageNames}
              pageLanguages={activeGroup.pageLanguages}
              popularLangs={popularLangs}
              onPageCountChange={() => {}}
              onPageNameChange={(index, value) => {
                setColNames((prev) => {
                  const next = [...prev];
                  next[index] = value;
                  return next;
                });
              }}
              onPageNameBlur={handleColBlur}
              onPageLanguageChange={updatePageLangValue}
              onMovePage={movePageSettingAll}
            />
          </View>
        </Dialog.ScrollArea>
        <Dialog.Actions>
          <Button onPress={onDismiss}>{t('btn.cancel')}</Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

const styles = StyleSheet.create({
  dialog: {
    maxHeight: '90%',
  },
  content: {
    minWidth: TOKENS.layout.actionMaxWidth,
  },
});
