import React, { type ReactNode, useCallback, useLayoutEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Menu, useTheme, TouchableRipple, Text, Switch } from 'react-native-paper';
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
  isSwitch?: boolean;
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
      {items.map((item) => {
        if (item.isSwitch) {
          return (
            <TouchableRipple
              key={item.label}
              onPress={item.onPress}
              disabled={item.disabled}
              accessibilityRole="switch"
              accessibilityState={{ checked: !!item.selected, disabled: !!item.disabled }}
              accessibilityLabel={item.label}
              style={[styles.switchRow, { height: TOKENS.menu.itemHeight }]}
            >
              <View style={styles.switchInner}>
                <Text
                  variant="bodyLarge"
                  numberOfLines={1}
                  style={[
                    styles.switchLabel,
                    { color: item.destructive ? theme.colors.error : theme.colors.onSurface },
                  ]}
                >
                  {item.label}
                </Text>
                {/* Decorative: the row above is the accessible switch; this only mirrors
                    state and is hidden from touches and screen readers. */}
                <View
                  pointerEvents="none"
                  accessibilityElementsHidden
                  importantForAccessibility="no-hide-descendants"
                >
                  <Switch value={item.selected} disabled={item.disabled} />
                </View>
              </View>
            </TouchableRipple>
          );
        }
        return (
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
        );
      })}
    </Menu>
  );
}

const styles = StyleSheet.create({
  rightAlignedMenu: {
    alignSelf: 'flex-end',
  },
  switchRow: {
    paddingHorizontal: TOKENS.spacing.md,
    justifyContent: 'center',
  },
  switchInner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: TOKENS.spacing.md,
    width: '100%',
  },
  switchLabel: {
    flex: 1,
    fontWeight: TOKENS.typography.weight.medium,
  },
});
