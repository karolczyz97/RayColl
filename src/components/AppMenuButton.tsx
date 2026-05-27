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
  menuWidth = 200,
  align = 'left',
}: AppMenuButtonProps) {
  const theme = useTheme();
  const [visible, setVisible] = useState(false);

  const close = () => setVisible(false);

  return (
    <View style={styles.container}>
      {renderAnchor({ open: () => setVisible(true), visible })}
      <Menu
        visible={visible}
        onDismiss={close}
        style={[
          styles.menu,
          { transformOrigin: align === 'right' ? 'top right' : 'top left' },
        ]}
        contentStyle={[
          styles.menuContent,
          { backgroundColor: theme.colors.elevation.level2 },
          { width: menuWidth, maxWidth: menuWidth },
          { transformOrigin: align === 'right' ? 'top right' : 'top left' },
        ]}
        anchor={
          <View
            style={[
              styles.dummyAnchor,
              { width: menuWidth },
              align === 'right' ? { right: 0 } : { left: 0 },
            ]}
          />
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
              titleStyle={{
                color: textColor,
                fontWeight: item.selected
                  ? TOKENS.typography.weight.bold
                  : TOKENS.typography.weight.medium,
              }}
            />
          );
        })}
      </Menu>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'relative',
  },
  dummyAnchor: {
    position: 'absolute',
    top: 0,
    height: 1,
    backgroundColor: 'transparent',
  },
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
});
