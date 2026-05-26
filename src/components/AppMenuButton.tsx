import React, { type ReactNode, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Menu, useTheme } from 'react-native-paper';
import { TOKENS } from '../theme/tokens';

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
  menuWidth,
  align = 'left',
}: AppMenuButtonProps) {
  const theme = useTheme();
  const [visible, setVisible] = useState(false);
  const [anchorWidth, setAnchorWidth] = useState(0);

  const close = () => setVisible(false);

  const offset = align === 'right' && menuWidth && anchorWidth ? menuWidth - anchorWidth : 0;

  return (
    <Menu
      visible={visible}
      onDismiss={close}
      style={[
        styles.menu,
        offset > 0 ? { marginLeft: -offset } : undefined,
        ({ transformOrigin: align === 'right' ? 'top right' : 'top left' } as any),
      ]}
      contentStyle={[
        styles.menuContent,
        { backgroundColor: theme.colors.elevation.level2 },
        menuWidth ? { width: menuWidth, maxWidth: menuWidth } : undefined,
        ({ transformOrigin: align === 'right' ? 'top right' : 'top left' } as any),
      ]}
      anchor={
        <View
          onLayout={(e) => setAnchorWidth(e.nativeEvent.layout.width)}
          style={styles.anchorWrapper}
        >
          {renderAnchor({ open: () => setVisible(true), visible })}
        </View>
      }
    >
      {header}
      {items.map((item) => {
        const textColor = item.destructive
          ? theme.colors.error
          : item.selected
            ? theme.colors.onSecondaryContainer
            : theme.colors.onSurface;

        return (
          <Menu.Item
            key={item.label}
            title={item.label}
            leadingIcon={item.selected ? 'check' : item.leadingIcon}
            disabled={item.disabled}
            onPress={() => {
              close();
              item.onPress();
            }}
            style={[
              styles.menuItem,
              item.selected && { backgroundColor: theme.colors.secondaryContainer },
            ]}
            titleStyle={{ color: textColor, fontWeight: item.selected ? '700' : '500' }}
          />
        );
      })}
    </Menu>
  );
}

const styles = StyleSheet.create({
  menu: {
    marginTop: TOKENS.menu.gap,
  },
  menuContent: {
    borderRadius: TOKENS.radius.lg,
    overflow: 'hidden',
  },
  menuItem: {
    minHeight: TOKENS.menu.itemHeight,
    width: '100%',
    maxWidth: '100%',
  },
  anchorWrapper: {
    alignSelf: 'flex-start',
  },
});

