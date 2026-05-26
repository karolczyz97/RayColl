import React, { useState } from 'react';
import { Button, Menu } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';

interface Props {
  sepKey: string;
  setSepKey: (key: string) => void;
  t: TranslationFn;
}

export function ImportSeparatorSelector({ sepKey, setSepKey, t }: Props) {
  const [sepMenuVisible, setSepMenuVisible] = useState(false);

  return (
    <Menu
      visible={sepMenuVisible}
      onDismiss={() => setSepMenuVisible(false)}
      anchor={
        <Button
          mode="outlined"
          onPress={() => setSepMenuVisible(true)}
          accessibilityLabel="Select CSV separator"
        >
          {`${t('import.separator')}: ${t(`import.sep.${sepKey}`)}`}
        </Button>
      }
    >
      <Menu.Item
        onPress={() => {
          setSepKey('tab');
          setSepMenuVisible(false);
        }}
        title={t('import.sep.tab')}
      />
      <Menu.Item
        onPress={() => {
          setSepKey('semicolon');
          setSepMenuVisible(false);
        }}
        title={t('import.sep.semicolon')}
      />
      <Menu.Item
        onPress={() => {
          setSepKey('comma');
          setSepMenuVisible(false);
        }}
        title={t('import.sep.comma')}
      />
      <Menu.Item
        onPress={() => {
          setSepKey('pipe');
          setSepMenuVisible(false);
        }}
        title={t('import.sep.pipe')}
      />
    </Menu>
  );
}
