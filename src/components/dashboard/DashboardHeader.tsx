import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, Avatar, IconButton, TouchableRipple, useTheme } from 'react-native-paper';
import { router } from 'expo-router';
import { useI18n } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import { ROUTES } from '../../constants/routes';
import { AppMenuButton } from '../AppMenuButton';
import { getTopBarColors } from '../../theme/semanticColors';
import type { FlashcardStoreState } from '../../store/FlashcardStoreTypes';

// ─── DashboardBrand ──────────────────────────────────────────────────────────

export function DashboardBrand() {
  const { fg } = getTopBarColors(useTheme());
  return (
    <View style={styles.logoGroup}>
      <Avatar.Icon
        size={40}
        icon="book-open-page-variant"
        style={styles.logoIcon}
        accessibilityLabel="RayColl Logo Icon"
      />
      <Text variant="headlineMedium" style={[styles.logoText, { color: fg }]}>
        RayColl
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

  return (
    <View style={styles.actionsGroup}>
      <IconButton
        icon="chart-bar"
        size={24}
        iconColor={fg}
        onPress={() => router.push(ROUTES.STATS)}
        accessibilityLabel={t('stats.title')}
      />
      <IconButton
        icon="cog"
        size={24}
        iconColor={fg}
        onPress={() => router.push(ROUTES.APP_SETTINGS)}
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
                size={36}
                source={
                  user.photoURL
                    ? { uri: user.photoURL }
                    : require('../../../assets/images/icon.png')
                }
              />
            </TouchableRipple>
          )}
          header={
            <View style={styles.userMenuContent}>
              <Avatar.Image
                size={48}
                source={
                  user.photoURL
                    ? { uri: user.photoURL }
                    : require('../../../assets/images/icon.png')
                }
                style={styles.userMenuAvatar}
              />
              <Text variant="titleMedium" style={styles.userName}>
                {user.displayName || t('auth.local')}
              </Text>
              <Text
                variant="bodySmall"
                style={[styles.userEmail, { color: theme.colors.onSurfaceVariant }]}
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
              onPress: () => {
                onLogout();
              },
            },
          ]}
        />
      ) : (
        <IconButton
          icon="account-circle"
          size={24}
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
    fontWeight: 'bold',
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
  userMenuContent: {
    padding: TOKENS.spacing.lg,
    alignItems: 'center',
  },
  userMenuAvatar: {
    marginBottom: TOKENS.spacing.sm,
  },
  userName: {
    fontWeight: 'bold',
  },
  userEmail: {
    marginBottom: TOKENS.spacing.sm,
  },
});
