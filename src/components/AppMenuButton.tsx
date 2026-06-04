import React, { type ReactNode, useCallback, useLayoutEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Menu, useTheme } from 'react-native-paper';
import { TOKENS } from '@/theme/tokens';
import { menuStyles } from '@/theme/menuStyles';
import { AppMenuItem } from './AppMenuItem';

export interface AppMenuButtonItem {
  label: string;
  onPress: () => void;
  leadingIcon?: string;
  destructive?: boolean;
  disabled?: boolean;
  selected?: boolean;
}

export interface AppMenuButtonProps {
  renderAnchor: (props: { open: () => void; visible: boolean }) => ReactNode;
  items: AppMenuButtonItem[];
  header?: ReactNode;
  menuWidth?: number;
  align?: 'left' | 'right';
}

export function AppMenuButton({
  renderAnchor,
  items,
  header,
  menuWidth = TOKENS.menu.minWidth,
  align = 'left',
}: AppMenuButtonProps) {
  const theme = useTheme();
  const [visible, setVisible] = useState(false);
  const anchorRef = useRef<View>(null);

  const close = () => setVisible(false);

  // Prime layout cache on mount so first Menu open animates from the correct position
  useLayoutEffect(() => {
    anchorRef.current?.measure(() => {});
  }, []);

  const handleOpen = useCallback(() => {
    setVisible(true);
  }, []);

  return (
    <Menu
      visible={visible}
      onDismiss={close}
      anchorPosition={align === 'right' ? 'bottom' : 'top'}
      style={[
        menuStyles.menu,
        align === 'right' && styles.rightAlignedMenu,
        { transformOrigin: align === 'right' ? 'top right' : 'top left' },
      ]}
      contentStyle={[
        menuStyles.menuContent,
        { backgroundColor: theme.colors.elevation.level2 },
        { width: menuWidth, maxWidth: menuWidth },
        { transformOrigin: align === 'right' ? 'top right' : 'top left' },
      ]}
      anchor={
        <View ref={anchorRef} collapsable={false}>
          {renderAnchor({ open: handleOpen, visible })}
        </View>
      }
    >
      {header}
      {items.map((item) => (
        <AppMenuItem
          key={item.label}
          label={item.label}
          icon={item.leadingIcon}
          selected={item.selected}
          destructive={item.destructive}
          disabled={item.disabled}
          onPress={() => {
            close();
            item.onPress();
          }}
        />
      ))}
    </Menu>
  );
}

const styles = StyleSheet.create({
  rightAlignedMenu: {
    alignSelf: 'flex-end',
  },
});
