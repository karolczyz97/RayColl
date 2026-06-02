import React from 'react';
import { StyleSheet, View } from 'react-native';
import {
  Avatar,
  Drawer,
  Icon,
  IconButton,
  Text,
  TouchableRipple,
  useTheme,
} from 'react-native-paper';

import { useI18n } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import type { FlashcardStoreState } from '../../store/FlashcardStoreTypes';
import {
  NAVIGATION_DESTINATIONS,
  type NavigationDestination,
  type TopLevelDestinationKey,
} from './navigationDestinations';
import { NavigationAccountMenu } from './NavigationAccountMenu';
import { AppFloatingActionButton } from '../AppFloatingActionButton';

interface NavigationRailProps {
  expanded: boolean;
  activeDestination: TopLevelDestinationKey | null;
  user: FlashcardStoreState['user'];
  onNavigate: (destination: NavigationDestination) => void;
  onImport: () => void;
  onLogin: () => void;
  onLogout: () => void;
  onToggleExpanded: () => void;
  onHide: () => void;
}

interface CollapsedRailItemProps {
  focusedIcon: string;
  unfocusedIcon: string;
  label: string;
  active: boolean;
  onPress: () => void;
}

function CollapsedRailItem({
  focusedIcon,
  unfocusedIcon,
  label,
  active,
  onPress,
}: CollapsedRailItemProps) {
  const theme = useTheme();
  const icon = active ? focusedIcon : unfocusedIcon;
  const iconColor = active ? theme.colors.onSecondaryContainer : theme.colors.onSurfaceVariant;
  const labelColor = active ? theme.colors.onSurface : theme.colors.onSurfaceVariant;

  return (
    <TouchableRipple
      borderless
      onPress={onPress}
      style={styles.collapsedItemRipple}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      accessibilityLabel={label}
    >
      <View style={styles.collapsedItemContent}>
        <View
          style={[
            styles.collapsedIconContainer,
            {
              backgroundColor: active ? theme.colors.secondaryContainer : 'transparent',
            },
          ]}
        >
          <Icon source={icon} size={TOKENS.iconSize.md} color={iconColor} />
        </View>
        <Text
          variant="labelMedium"
          numberOfLines={2}
          style={[styles.collapsedItemLabel, { color: labelColor }]}
        >
          {label}
        </Text>
      </View>
    </TouchableRipple>
  );
}

export function NavigationRail({
  expanded,
  activeDestination,
  user,
  onNavigate,
  onImport,
  onLogin,
  onLogout,
  onToggleExpanded,
  onHide,
}: NavigationRailProps) {
  const { t } = useI18n();
  const theme = useTheme();

  return (
    <View
      style={[
        styles.root,
        {
          width: expanded ? TOKENS.layout.expandedRailWidth : TOKENS.layout.railWidth,
          backgroundColor: theme.colors.surface,
          borderRightColor: theme.colors.outlineVariant,
        },
      ]}
    >
      <View style={[styles.brand, expanded ? styles.brandExpanded : styles.brandCollapsed]}>
        <Avatar.Icon
          icon="book-open-page-variant"
          size={expanded ? 40 : 44}
          style={{ backgroundColor: theme.colors.secondaryContainer }}
          color={theme.colors.onSecondaryContainer}
          accessibilityLabel="RayColl Logo Icon"
        />
        {expanded ? (
          <>
            <Text variant="titleLarge" style={styles.brandText}>
              RayColl
            </Text>
            <IconButton
              icon="chevron-left"
              size={TOKENS.iconSize.sm}
              onPress={onToggleExpanded}
              accessibilityLabel="Collapse navigation"
              style={styles.headerButton}
            />
          </>
        ) : (
          <IconButton
            icon="chevron-right"
            size={TOKENS.iconSize.sm}
            onPress={onToggleExpanded}
            accessibilityLabel="Expand navigation"
            style={styles.collapsedExpandButton}
          />
        )}
      </View>

      <View style={styles.destinations}>
        {NAVIGATION_DESTINATIONS.map((destination) => {
          const label = t(destination.labelKey);
          const active = activeDestination === destination.key;

          if (expanded) {
            return (
              <Drawer.Item
                key={destination.key}
                icon={active ? destination.focusedIcon : destination.unfocusedIcon}
                label={label}
                active={active}
                onPress={() => onNavigate(destination)}
                accessibilityLabel={label}
                style={styles.expandedItem}
              />
            );
          }

          return (
            <CollapsedRailItem
              key={destination.key}
              focusedIcon={destination.focusedIcon}
              unfocusedIcon={destination.unfocusedIcon}
              label={label}
              active={active}
              onPress={() => onNavigate(destination)}
            />
          );
        })}
      </View>

      <View style={styles.footer}>
        <IconButton
          icon="chevron-left"
          size={TOKENS.iconSize.sm}
          onPress={onHide}
          accessibilityLabel="Hide navigation"
          style={expanded ? styles.hideButtonExpanded : styles.hideButtonCollapsed}
        />
        <AppFloatingActionButton
          icon="import"
          label={expanded ? t('import.title') : undefined}
          onPress={onImport}
          accessibilityLabel={t('import.title')}
          style={[
            styles.importAction,
            expanded ? styles.importActionExpanded : styles.importActionCollapsed,
          ]}
        />
        <NavigationAccountMenu
          user={user}
          expanded={expanded}
          onLogin={onLogin}
          onLogout={onLogout}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flexShrink: 0,
    borderRightWidth: StyleSheet.hairlineWidth,
    paddingVertical: TOKENS.spacing.md,
  },
  brand: {
    minHeight: 56,
    marginBottom: TOKENS.spacing.lg,
  },
  brandCollapsed: {
    alignItems: 'center',
    justifyContent: 'center',
    gap: TOKENS.spacing.xs,
  },
  brandExpanded: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.sm,
    paddingHorizontal: TOKENS.spacing.lg,
  },
  brandText: {
    fontWeight: TOKENS.typography.weight.bold,
    flex: 1,
  },
  headerButton: {
    margin: 0,
  },
  collapsedExpandButton: {
    margin: 0,
  },
  destinations: {
    flex: 1,
    gap: TOKENS.spacing.xs,
  },
  collapsedItemRipple: {
    alignSelf: 'center',
    width: 72,
    minHeight: 64,
    borderRadius: TOKENS.radius.lg,
    overflow: 'hidden',
  },
  collapsedItemContent: {
    minHeight: 64,
    alignItems: 'center',
    justifyContent: 'center',
    gap: TOKENS.spacing.xs,
    paddingVertical: TOKENS.spacing.xs,
  },
  collapsedIconContainer: {
    width: 56,
    height: 32,
    borderRadius: TOKENS.radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  collapsedItemLabel: {
    width: 68,
    textAlign: 'center',
    lineHeight: 14,
  },
  expandedItem: {
    marginHorizontal: TOKENS.spacing.sm,
  },
  footer: {
    gap: TOKENS.spacing.md,
    alignItems: 'stretch',
  },
  hideButtonCollapsed: {
    alignSelf: 'center',
    margin: 0,
  },
  hideButtonExpanded: {
    alignSelf: 'flex-end',
    marginHorizontal: TOKENS.spacing.md,
    marginVertical: 0,
  },
  importAction: {
    alignSelf: 'center',
    borderRadius: TOKENS.radius.lg,
    overflow: 'hidden',
  },
  importActionCollapsed: {
    width: 56,
    height: 56,
    marginBottom: TOKENS.spacing.xs,
  },
  importActionExpanded: {
    alignSelf: 'stretch',
    marginHorizontal: TOKENS.spacing.md,
    minHeight: 56,
  },
});
