import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Avatar, Button, IconButton, Text, TouchableRipple, useTheme } from 'react-native-paper';

import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import type { FlashcardStoreState } from '@/store/FlashcardStoreTypes';
import { AppMenuButton } from '@/components/AppMenuButton';
import { UserMenuHeader } from '@/components/navigation/UserMenuHeader';
import { getUserAvatarSource } from '@/utils/userAvatar';

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
  const source = getUserAvatarSource(user);

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
      <View style={styles.leading}>
        <IconButton
          icon="account-circle"
          size={TOKENS.iconSize.md}
          onPress={onLogin}
          accessibilityLabel={t('btn.login')}
          style={styles.collapsedLogin}
        />
      </View>
    );
  }

  return (
    <AppMenuButton
      align={expanded ? 'left' : 'right'}
      menuWidth={TOKENS.menu.minWidth}
      renderAnchor={({ open, visible }) => (
        <TouchableRipple
          onPress={open}
          style={styles.anchor}
          accessibilityLabel={t('a11y.profile_menu')}
          accessibilityState={{ expanded: visible }}
        >
          <View style={styles.anchorContent}>
            <View style={styles.leading}>
              <Avatar.Image size={TOKENS.touchTarget.compact} source={source} />
            </View>
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
        <UserMenuHeader source={source} displayName={user.displayName} email={user.email} />
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
    borderRadius: TOKENS.radius.lg,
    minHeight: TOKENS.touchTarget.min,
    justifyContent: 'center',
  },
  // Matches the rail leading column so the avatar sits on the same centre line
  // as the destination icons, collapsed and expanded alike.
  leading: {
    width: TOKENS.layout.railWidth,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  collapsedLogin: {
    margin: 0,
  },
  anchorContent: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingRight: TOKENS.spacing.md,
  },
  accountText: {
    flex: 1,
    minWidth: 0,
  },
});
