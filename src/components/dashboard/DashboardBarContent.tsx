import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, Avatar, IconButton, TouchableRipple, useTheme } from 'react-native-paper';
import { router } from 'expo-router';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { ROUTES } from '@/constants/routes';
import { AppMenuButton } from '@/components/AppMenuButton';
import { UserMenuHeader } from '@/components/navigation/UserMenuHeader';
import { getTopBarColors } from '@/theme/semanticColors';
import { APP_NAME } from '@/constants/app';
import type { FlashcardStoreState } from '@/store/FlashcardStoreTypes';
import { getUserAvatarSource } from '@/utils/userAvatar';

// ─── DashboardBrand ──────────────────────────────────────────────────────────

export function DashboardBrand() {
  const { fg } = getTopBarColors(useTheme());
  return (
    <View style={styles.logoGroup}>
      <Avatar.Icon
        size={40}
        icon="book-open-page-variant"
        style={styles.logoIcon}
        accessibilityLabel={`${APP_NAME} Logo Icon`}
      />
      <Text variant="headlineMedium" style={[styles.logoText, { color: fg }]}>
        {APP_NAME}
      </Text>
    </View>
  );
}

// ─── DashboardActions ─────────────────────────────────────────────────────────

interface DashboardActionsProps {
  user: FlashcardStoreState['user'];
  onLogin: () => void;
  onLogout: () => void;
}

export function DashboardActions({ user, onLogin, onLogout }: DashboardActionsProps) {
  const { t } = useI18n();
  const theme = useTheme();
  const { fg } = getTopBarColors(theme);
  const avatarSource = getUserAvatarSource(user);

  return (
    <View style={styles.actionsGroup}>
      <IconButton
        icon="chart-bar"
        size={TOKENS.iconSize.md}
        iconColor={fg}
        onPress={() => router.navigate(ROUTES.STATS)}
        accessibilityLabel={t('stats.title')}
      />
      <IconButton
        icon="cog"
        size={TOKENS.iconSize.md}
        iconColor={fg}
        onPress={() => router.navigate(ROUTES.APP_SETTINGS)}
        accessibilityLabel={t('app_settings.title')}
      />
      {user ? (
        <AppMenuButton
          align="right"
          menuWidth={TOKENS.menu.minWidth}
          renderAnchor={({ open, visible }) => (
            <TouchableRipple
              onPress={open}
              style={styles.avatarAnchor}
              accessibilityLabel="Open user profile menu"
              accessibilityState={{ expanded: visible }}
            >
              <Avatar.Image
                size={TOKENS.iconSize.lg}
                source={avatarSource}
              />
            </TouchableRipple>
          )}
          header={
            <UserMenuHeader
              source={avatarSource}
              displayName={user.displayName}
              email={user.email}
            />
          }
          items={[
            {
              label: t('btn.logout'),
              leadingIcon: 'logout',
              destructive: true,
              onPress: () => {
                onLogout();
              },
            },
          ]}
        />
      ) : (
        <IconButton
          icon="account-circle"
          size={TOKENS.iconSize.md}
          iconColor={fg}
          onPress={onLogin}
          accessibilityLabel="Log in with Google"
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  logoGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.sm,
  },
  logoIcon: {
    backgroundColor: 'transparent',
  },
  logoText: {
    fontWeight: TOKENS.typography.weight.bold,
  },
  actionsGroup: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  avatarAnchor: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: TOKENS.touchTarget.min,
    minWidth: TOKENS.touchTarget.min,
    borderRadius: TOKENS.radius.pill,
  },
});
