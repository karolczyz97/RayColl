import React, { useState, useRef } from 'react';
import { View, StyleSheet, ScrollView, Pressable, Animated, useWindowDimensions } from 'react-native';
import { Text, Card, Button, FAB, Avatar, IconButton, Menu, useTheme, ActivityIndicator } from 'react-native-paper';
import { router } from 'expo-router';
import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { useI18n } from '../i18n';
import { SegmentedProgressBar, computeCardStats } from '../components/SegmentedProgressBar';
import type { FlashcardGroup } from '../types/models';
import { MaterialCommunityIcons } from '@expo/vector-icons';

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

  const btnBgColor = dueCount === 0 ? theme.colors.surfaceVariant : theme.colors.primary;
  const btnTextColor = dueCount === 0 ? theme.colors.onSurfaceDisabled : theme.colors.onPrimary;

  return (
    <View style={styles.studyButtonGroup}>
      <View style={[styles.pillContainer, { backgroundColor: btnBgColor }]}>
        {/* Left Action Button */}
        <Pressable
          disabled={dueCount === 0}
          onPress={onStudy}
          style={({ pressed }) => [
            styles.pillLeft,
            pressed && styles.pressed,
          ]}
        >
          <MaterialCommunityIcons name="play" size={16} color={btnTextColor} style={{ marginRight: 6 }} />
          <Text style={[styles.pillText, { color: btnTextColor }]} numberOfLines={1}>
            {modeName}
          </Text>
        </Pressable>

        {/* Divider Line */}
        <View style={[styles.pillDivider, { backgroundColor: btnTextColor }]} />

        {/* Right Dropdown Anchor */}
        <Menu
          visible={visible}
          onDismiss={() => setVisible(false)}
          anchor={
            <Pressable
              onPress={() => setVisible(true)}
              style={({ pressed }) => [
                styles.pillRight,
                pressed && styles.pressed,
              ]}
            >
              <MaterialCommunityIcons name="chevron-down" size={16} color={btnTextColor} />
            </Pressable>
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
    </View>
  );
}

export default function Dashboard() {
  const store = useFlashcardStore();
  const { t } = useI18n();
  const theme = useTheme();
  const { width } = useWindowDimensions();
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
              const numColumns = width < 600 ? 1 : width < 960 ? 2 : 3;
              const padding = 16;
              const gap = 16;
              const maxW = 960;
              const currentWidth = width > maxW ? maxW : width;
              const cardWidth = (currentWidth - padding * 2 - gap * (numColumns - 1)) / numColumns;
              return (
                <View key={group.id} style={[styles.gridItem, { width: cardWidth }]}>
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
      <FAB icon="plus" style={styles.fab} onPress={() => router.push('/import')} />
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
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 16,
    width: '100%',
  },
  gridItem: {
    // Width computed dynamically in render loop
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
  pillContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 100,
    height: 40,
    width: '100%',
    overflow: 'hidden',
  },
  pillLeft: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: '100%',
    paddingLeft: 16,
    paddingRight: 8,
  },
  pillText: {
    fontSize: 13,
    fontWeight: 'bold',
  },
  pillDivider: {
    width: 1,
    height: '60%',
    opacity: 0.25,
  },
  pillRight: {
    width: 44,
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.75,
  },
  fab: {
    position: 'absolute',
    margin: 16,
    right: 8,
    bottom: 8,
    borderRadius: 16,
  },
});
