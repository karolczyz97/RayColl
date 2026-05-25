import React, { useState, useRef } from 'react';
import { View, StyleSheet, ScrollView, Pressable, Animated } from 'react-native';
import { Text, Card, Button, FAB, Avatar, IconButton, Menu, useTheme, ActivityIndicator } from 'react-native-paper';
import { router } from 'expo-router';
import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { useI18n } from '../i18n';
import { SegmentedProgressBar, computeCardStats } from '../components/SegmentedProgressBar';
import type { FlashcardGroup } from '../types/models';

function PressableCard({ children, onPress }: { children: React.ReactNode; onPress: () => void }) {
  const scale = useRef(new Animated.Value(1)).current;

  const handlePressIn = () => {
    Animated.spring(scale, {
      toValue: 0.96,
      useNativeDriver: true,
      tension: 100,
      friction: 6,
    }).start();
  };

  const handlePressOut = () => {
    Animated.spring(scale, {
      toValue: 1,
      useNativeDriver: true,
      tension: 100,
      friction: 6,
    }).start();
  };

  return (
    <Animated.View style={{ transform: [{ scale }] }}>
      <Pressable
        onPressIn={handlePressIn}
        onPressOut={handlePressOut}
        onPress={onPress}
      >
        {children}
      </Pressable>
    </Animated.View>
  );
}

function GroupStudyMenuButton({
  group,
  onStudy,
  onModeChange,
}: {
  group: FlashcardGroup;
  onStudy: () => void;
  onModeChange: (modeId: string) => void;
}) {
  const store = useFlashcardStore();
  const { t } = useI18n();
  const [visible, setVisible] = useState(false);
  const theme = useTheme();

  const dueCount = store.getDueCards(group.id).length;
  const activeMode = store.studyModes.find((m) => m.id === group.activeModeId);

  const getModeName = (mId: string, defaultName: string) => {
    const key = `mode.${mId}.name`;
    const translated = t(key);
    return translated === key ? defaultName : translated;
  };

  const modeName = activeMode ? getModeName(activeMode.id, activeMode.name) : t('mode.classic.name');

  return (
    <View style={styles.studyButtonGroup}>
      <Button
        mode="contained"
        disabled={dueCount === 0}
        onPress={onStudy}
        style={styles.studyMainBtn}
        icon="play"
        labelStyle={styles.btnLabel}
      >
        {modeName}
      </Button>
      <Menu
        visible={visible}
        onDismiss={() => setVisible(false)}
        anchor={
          <IconButton
            icon="chevron-down"
            mode="contained"
            containerColor={theme.colors.primary}
            iconColor={theme.colors.onPrimary}
            size={20}
            onPress={() => setVisible(true)}
            style={styles.studyDropdownBtn}
          />
        }
      >
        {store.studyModes.map((m) => (
          <Menu.Item
            key={m.id}
            title={getModeName(m.id, m.name)}
            leadingIcon={m.id === group.activeModeId ? 'check' : undefined}
            onPress={() => {
              onModeChange(m.id);
              setVisible(false);
            }}
          />
        ))}
      </Menu>
    </View>
  );
}

export default function Dashboard() {
  const store = useFlashcardStore();
  const { t } = useI18n();
  const theme = useTheme();
  const [userMenuVisible, setUserMenuVisible] = useState(false);

  const { groups, getDueCards, user, signIn, signOut, isLoading } = store;

  const handleLogin = async () => {
    try {
      await signIn();
    } catch (e) {
      console.error('Login failed', e);
    }
  };

  if (isLoading) {
    return (
      <View style={[styles.loadingContainer, { backgroundColor: theme.colors.background }]}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
      </View>
    );
  }

  return (
    <View style={[styles.root, { backgroundColor: theme.colors.background }]}>
      {/* Top bar */}
      <View style={styles.topBar}>
        <View style={styles.logoGroup}>
          <Avatar.Icon size={40} icon="book-open-page-variant" style={styles.logoIcon} />
          <Text variant="headlineMedium" style={styles.logoText}>
            RayColl
          </Text>
        </View>
        <View style={styles.actionsGroup}>
          <IconButton icon="chart-bar" size={24} onPress={() => router.push('/stats')} />
          <IconButton icon="cog" size={24} onPress={() => router.push('/app-settings')} />
          {user ? (
            <Menu
              visible={userMenuVisible}
              onDismiss={() => setUserMenuVisible(false)}
              anchor={
                <Pressable onPress={() => setUserMenuVisible(true)}>
                  <Avatar.Image
                    size={36}
                    source={user.photoURL ? { uri: user.photoURL } : require('../../assets/images/icon.png')}
                  />
                </Pressable>
              }
            >
              <View style={styles.userMenuContent}>
                <Avatar.Image
                  size={48}
                  source={user.photoURL ? { uri: user.photoURL } : require('../../assets/images/icon.png')}
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
                    signOut();
                    setUserMenuVisible(false);
                  }}
                >
                  {t('btn.logout')}
                </Button>
              </View>
            </Menu>
          ) : (
            <IconButton icon="account-circle" size={24} onPress={handleLogin} />
          )}
        </View>
      </View>

      {/* Decks Grid */}
      <ScrollView contentContainerStyle={styles.scrollContent}>
        {groups.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Text style={{ color: theme.colors.onSurfaceVariant }}>{t('dashboard.no_groups')}</Text>
          </View>
        ) : (
          <View style={styles.grid}>
            {groups.map((group) => {
              const dueCount = getDueCards(group.id).length;
              const cardStats = computeCardStats(group.cards);
              return (
                <View key={group.id} style={styles.gridItem}>
                  <PressableCard onPress={() => router.push(`/study/${group.id}`)}>
                    <Card style={styles.card} mode="elevated">
                      <Card.Content>
                        <Text variant="titleMedium" style={styles.cardTitle}>
                          {group.name}
                        </Text>
                        <Text variant="bodyMedium" style={{ color: theme.colors.onSurfaceVariant, marginBottom: 12 }}>
                          {t('dashboard.cards_count', { count: group.cards.length })}
                        </Text>
                        <SegmentedProgressBar stats={cardStats} />
                        {dueCount > 0 && (
                          <Text variant="bodyMedium" style={[styles.dueText, { color: theme.colors.error }]}>
                            {t('dashboard.due_count', { count: dueCount })}
                          </Text>
                        )}
                      </Card.Content>
                      <Card.Actions style={styles.cardActions}>
                        <View style={styles.cardActionsRow}>
                          <IconButton
                            icon="eye-outline"
                            size={20}
                            iconColor={theme.colors.primary}
                            onPress={() => router.push(`/browse/${group.id}`)}
                          />
                          <IconButton
                            icon="tune"
                            size={20}
                            iconColor={theme.colors.primary}
                            onPress={() => router.push(`/settings/${group.id}`)}
                          />
                          <View style={{ flex: 1 }}>
                            <GroupStudyMenuButton
                              group={group}
                              onStudy={() => router.push(`/study/${group.id}`)}
                              onModeChange={(modeId) => store.updateGroup({ ...group, activeModeId: modeId })}
                            />
                          </View>
                        </View>
                      </Card.Actions>
                    </Card>
                  </PressableCard>
                </View>
              );
            })}
          </View>
        )}
      </ScrollView>

      {/* Import FAB */}
      <FAB icon="plus" style={[styles.fab, { backgroundColor: theme.colors.primary }]} color={theme.colors.onPrimary} onPress={() => router.push('/import')} />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  topBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingTop: 48,
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
  scrollContent: {
    padding: 16,
    paddingBottom: 100,
  },
  emptyContainer: {
    alignItems: 'center',
    paddingVertical: 64,
  },
  grid: {
    gap: 16,
  },
  gridItem: {
    width: '100%',
  },
  card: {
    borderRadius: 16,
  },
  cardTitle: {
    fontWeight: 'bold',
    marginBottom: 4,
  },
  dueText: {
    fontWeight: '500',
    marginTop: 8,
  },
  cardActions: {
    paddingHorizontal: 8,
    paddingBottom: 8,
  },
  cardActionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
    gap: 4,
  },
  studyButtonGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
  },
  studyMainBtn: {
    flex: 1,
    borderTopLeftRadius: 100,
    borderBottomLeftRadius: 100,
    borderTopRightRadius: 0,
    borderBottomRightRadius: 0,
  },
  studyDropdownBtn: {
    borderTopLeftRadius: 0,
    borderBottomLeftRadius: 0,
    borderTopRightRadius: 100,
    borderBottomRightRadius: 100,
    margin: 0,
  },
  btnLabel: {
    fontSize: 12,
  },
  fab: {
    position: 'absolute',
    margin: 16,
    right: 8,
    bottom: 8,
    borderRadius: 16,
  },
});
