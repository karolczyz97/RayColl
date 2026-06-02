import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Avatar, Button, IconButton, Text, TouchableRipple, useTheme } from 'react-native-paper';

import { useI18n } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import type { FlashcardStoreState } from '../../store/FlashcardStoreTypes';
import { AppMenuButton } from '../AppMenuButton';

interface NavigationAccountMenuProps {
  user: FlashcardStoreState['user'];
  expanded: boolean;
  onLogin: () => void;
  onLogout: () => void;
}

export function NavigationAccountMenu({
  user,
  expanded,
  onLogin,
  onLogout,
}: NavigationAccountMenuProps) {
  const { t } = useI18n();
  const theme = useTheme();
  const source = user?.photoURL
    ? { uri: user.photoURL }
    : require('../../../assets/images/icon.png');

  if (!user) {
    if (expanded) {
      return (
        <Button
          icon="account-circle"
          mode="outlined"
          onPress={onLogin}
          style={styles.loginButton}
          contentStyle={styles.loginContent}
          accessibilityLabel={t('btn.login')}
        >
          {t('btn.login')}
        </Button>
      );
    }

    return (
      <IconButton
        icon="account-circle"
        size={TOKENS.iconSize.md}
        onPress={onLogin}
        accessibilityLabel={t('btn.login')}
      />
    );
  }

  return (
    <AppMenuButton
      align={expanded ? 'left' : 'right'}
      menuWidth={TOKENS.menu.minWidth}
      renderAnchor={({ open, visible }) => (
        <TouchableRipple
          onPress={open}
          style={[
            styles.anchor,
            expanded ? styles.expandedAnchor : styles.collapsedAnchor,
          ]}
          accessibilityLabel="Open user profile menu"
          accessibilityState={{ expanded: visible }}
        >
          <View style={styles.anchorContent}>
            <Avatar.Image size={expanded ? 40 : 36} source={source} />
            {expanded ? (
              <View style={styles.accountText}>
                <Text variant="labelLarge" numberOfLines={1}>
                  {user.displayName || t('auth.local')}
                </Text>
                <Text
                  variant="bodySmall"
                  numberOfLines={1}
                  style={{ color: theme.colors.onSurfaceVariant }}
                >
                  {user.email || t('auth.local')}
                </Text>
              </View>
            ) : null}
          </View>
        </TouchableRipple>
      )}
      header={
        <View style={styles.menuHeader}>
          <Avatar.Image size={48} source={source} style={styles.menuAvatar} />
          <Text variant="titleMedium" style={styles.menuName}>
            {user.displayName || t('auth.local')}
          </Text>
          <Text
            variant="bodySmall"
            style={[styles.menuEmail, { color: theme.colors.onSurfaceVariant }]}
          >
            {user.email}
          </Text>
        </View>
      }
      items={[
        {
          label: t('btn.logout'),
          leadingIcon: 'logout',
          destructive: true,
          onPress: onLogout,
        },
      ]}
    />
  );
}

const styles = StyleSheet.create({
  loginButton: {
    marginHorizontal: TOKENS.spacing.md,
  },
  loginContent: {
    minHeight: TOKENS.control.compactHeight,
  },
  anchor: {
    borderRadius: TOKENS.radius.pill,
    minHeight: TOKENS.touchTarget.min,
  },
  collapsedAnchor: {
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: TOKENS.touchTarget.min,
  },
  expandedAnchor: {
    marginHorizontal: TOKENS.spacing.md,
    paddingHorizontal: TOKENS.spacing.sm,
    justifyContent: 'center',
  },
  anchorContent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.sm,
  },
  accountText: {
    flex: 1,
    minWidth: 0,
  },
  menuHeader: {
    padding: TOKENS.spacing.lg,
    alignItems: 'center',
  },
  menuAvatar: {
    marginBottom: TOKENS.spacing.sm,
  },
  menuName: {
    fontWeight: TOKENS.typography.weight.bold,
  },
  menuEmail: {
    marginBottom: TOKENS.spacing.sm,
  },
});

