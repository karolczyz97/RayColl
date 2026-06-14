import React from 'react';
import { StyleSheet, View } from 'react-native';
import type { StyleProp, ViewStyle } from 'react-native';
import { Text, TouchableRipple, useTheme } from 'react-native-paper';
import { AppIcon } from '@/components/AppIcon';
import { TOKENS } from '@/theme/tokens';

/** Position of a tile within its group — drives the grouped corner rounding. */
type TilePosition = 'single' | 'first' | 'middle' | 'last';

// Outer (group edge) corners are strongly rounded; inner (between-tile) corners
// are only minimally rounded, so a section reads as one cohesive group.
const OUTER_RADIUS = TOKENS.radius.xl;
const INNER_RADIUS = TOKENS.radius.xs;

function getTilePosition(index: number, count: number): TilePosition {
  if (count <= 1) return 'single';
  if (index === 0) return 'first';
  if (index === count - 1) return 'last';
  return 'middle';
}

function getCornerStyle(position: TilePosition): ViewStyle {
  switch (position) {
    case 'single':
      return { borderRadius: OUTER_RADIUS };
    case 'first':
      return {
        borderTopLeftRadius: OUTER_RADIUS,
        borderTopRightRadius: OUTER_RADIUS,
        borderBottomLeftRadius: INNER_RADIUS,
        borderBottomRightRadius: INNER_RADIUS,
      };
    case 'last':
      return {
        borderTopLeftRadius: INNER_RADIUS,
        borderTopRightRadius: INNER_RADIUS,
        borderBottomLeftRadius: OUTER_RADIUS,
        borderBottomRightRadius: OUTER_RADIUS,
      };
    case 'middle':
    default:
      return { borderRadius: INNER_RADIUS };
  }
}

interface SettingsSectionProps {
  /** Small, colored section header shown above the grouped tiles. */
  title: string;
  children: React.ReactNode;
}

/**
 * Native-Android-style settings group: a small accent-colored header label with
 * a column of {@link SettingsTile}s grouped tightly underneath. The first and
 * last tiles get strongly rounded outer corners and the tiles in between are
 * barely rounded, so the whole section reads as one block with hairline gaps.
 */
export function SettingsSection({ title, children }: SettingsSectionProps) {
  const theme = useTheme();

  const tiles = React.Children.toArray(children).filter(
    React.isValidElement,
  ) as React.ReactElement<SettingsTileProps>[];

  return (
    <View style={styles.section}>
      <Text variant="labelLarge" style={[styles.header, { color: theme.colors.primary }]}>
        {title}
      </Text>
      <View style={styles.tiles}>
        {tiles.map((tile, index) =>
          React.cloneElement(tile, { position: getTilePosition(index, tiles.length) }),
        )}
      </View>
    </View>
  );
}

interface SettingsTileProps {
  /** Primary label of the setting. */
  title: string;
  /** Optional secondary line under the title (muted). */
  description?: string;
  /** Optional leading icon (MaterialCommunityIcons name). */
  icon?: string;
  /** Tints the title (and leading icon) with the accent color, e.g. for actions. */
  accent?: boolean;
  /** Right-aligned control on the header row (e.g. a Switch). */
  trailing?: React.ReactNode;
  /**
   * Makes the whole tile pressable with a ripple. For boolean tiles, pass the
   * toggle here and render the Switch as a display-only `trailing` (the tile
   * press drives the change), matching the native "tap anywhere on the row" feel.
   */
  onPress?: () => void;
  /** Content rendered below the header row, e.g. a segmented control. */
  children?: React.ReactNode;
  danger?: boolean;
  style?: StyleProp<ViewStyle>;
  /** Injected by {@link SettingsSection}; do not set manually. */
  position?: TilePosition;
}

/**
 * A single settings row rendered as a flat tonal surface (no shadow) — the MD3
 * grouped-list style used by native Android settings, where rows are separated by
 * surface color rather than elevation. Title + optional description on the left,
 * an optional trailing control, and optional body content (e.g. a segmented
 * control) below. Corner rounding is set by its `position` within the parent
 * SettingsSection.
 */
export function SettingsTile({
  title,
  description,
  icon,
  accent = false,
  trailing,
  onPress,
  children,
  danger = false,
  style,
  position = 'single',
}: SettingsTileProps) {
  const theme = useTheme();
  const titleColor = danger
    ? theme.colors.error
    : accent
      ? theme.colors.primary
      : theme.colors.onSurface;
  const cornerStyle = getCornerStyle(position);

  const inner = (
    <View style={styles.content}>
      <View style={styles.headerRow}>
        {icon ? (
          <AppIcon
            name={icon}
            size={TOKENS.iconSize.md}
            color={accent ? theme.colors.primary : theme.colors.onSurfaceVariant}
          />
        ) : null}
        <View style={styles.texts}>
          <Text variant="titleMedium" style={[styles.title, { color: titleColor }]}>
            {title}
          </Text>
          {description ? (
            <Text
              variant="bodySmall"
              style={[styles.description, { color: theme.colors.onSurfaceVariant }]}
            >
              {description}
            </Text>
          ) : null}
        </View>
        {trailing ? <View style={styles.trailing}>{trailing}</View> : null}
      </View>
      {children ? <View style={styles.body}>{children}</View> : null}
    </View>
  );

  return (
    <View
      style={[
        styles.tile,
        cornerStyle,
        { backgroundColor: theme.colors.elevation.level2 },
        style,
      ]}
    >
      {onPress ? <TouchableRipple onPress={onPress}>{inner}</TouchableRipple> : inner}
    </View>
  );
}

const styles = StyleSheet.create({
  section: {
    gap: TOKENS.spacing.sm,
  },
  header: {
    marginLeft: TOKENS.spacing.xs,
    fontWeight: TOKENS.typography.weight.semibold,
  },
  tiles: {
    // Hairline gap between grouped tiles (native settings look).
    gap: TOKENS.spacing.xxs,
  },
  tile: {
    // Flat (no shadow) → safe to clip the ripple to the rounded corners.
    overflow: 'hidden',
  },
  content: {
    paddingVertical: TOKENS.spacing.md,
    paddingHorizontal: TOKENS.spacing.lg,
    gap: TOKENS.spacing.md,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.md,
  },
  texts: {
    flex: 1,
    minWidth: 0,
    gap: TOKENS.spacing.xxs,
  },
  title: {
    fontWeight: TOKENS.typography.weight.semibold,
  },
  description: {
    flexShrink: 1,
  },
  trailing: {
    flexShrink: 0,
  },
  body: {
    gap: TOKENS.spacing.md,
  },
});
