import React, { useState } from 'react';
import { View, StyleSheet, Pressable } from 'react-native';
import { Text, Avatar, IconButton, Menu, Button, useTheme } from 'react-native-paper';
import { router } from 'expo-router';
import type { User } from 'firebase/auth';
import { useI18n } from '../../i18n';

interface Props {
  user: User | null;
  onLogin: () => void;
  onLogout: () => void;
}

export function DashboardHeader({ user, onLogin, onLogout }: Props) {
  const { t } = useI18n();
  const theme = useTheme();
  const [userMenuVisible, setUserMenuVisible] = useState(false);

  return (
    <View style={styles.topBar}>
      <View style={styles.logoGroup}>
        <Avatar.Icon
          size={40}
          icon="book-open-page-variant"
          style={styles.logoIcon}
          accessibilityLabel="RayColl Logo Icon"
        />
        <Text variant="headlineMedium" style={styles.logoText}>
          RayColl
        </Text>
      </View>
      <View style={styles.actionsGroup}>
        <IconButton
          icon="chart-bar"
          size={24}
          onPress={() => router.push('/stats')}
          accessibilityLabel={t('stats.title')}
        />
        <IconButton
          icon="cog"
          size={24}
          onPress={() => router.push('/app-settings')}
          accessibilityLabel={t('app_settings.title')}
        />
        {user ? (
          <Menu
            visible={userMenuVisible}
            onDismiss={() => setUserMenuVisible(false)}
            anchor={
              <Pressable
                onPress={() => setUserMenuVisible(true)}
                accessibilityLabel="Open user profile menu"
              >
                <Avatar.Image
                  size={36}
                  source={user.photoURL ? { uri: user.photoURL } : require('../../../assets/images/icon.png')}
                />
              </Pressable>
            }
          >
            <View style={styles.userMenuContent}>
              <Avatar.Image
                size={48}
                source={user.photoURL ? { uri: user.photoURL } : require('../../../assets/images/icon.png')}
                style={{ marginBottom: 8 }}
              />
              <Text variant="titleMedium" style={{ fontWeight: 'bold' }}>
                {user.displayName || t('auth.local')}
              </Text>
              <Text variant="bodySmall" style={{ color: theme.colors.onSurfaceVariant, marginBottom: 12 }}>
                {user.email}
              </Text>
              <Button
                mode="outlined"
                textColor={theme.colors.error}
                style={{ borderColor: theme.colors.error, width: '100%' }}
                onPress={() => {
                  onLogout();
                  setUserMenuVisible(false);
                }}
              >
                {t('btn.logout')}
              </Button>
            </View>
          </Menu>
        ) : (
          <IconButton
            icon="account-circle"
            size={24}
            onPress={onLogin}
            accessibilityLabel="Log in with Google"
          />
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  topBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 16,
  },
  logoGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
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
  userMenuContent: {
    padding: 16,
    alignItems: 'center',
    minWidth: 200,
  },
});
