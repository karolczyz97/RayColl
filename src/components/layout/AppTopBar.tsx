import React from 'react';
import { StyleSheet, View } from 'react-native';
import { IconButton, Text, useTheme } from 'react-native-paper';
import Animated, { FadeIn } from 'react-native-reanimated';
import { TOKENS } from '@/theme/tokens';
import { getTopBarColors } from '@/theme/semanticColors';

interface AppTopBarProps {
  /** Sub-page mode: title shown next to the back arrow. */
  title?: string;
  /** Renders a back arrow on the far left. */
  onBack?: () => void;
  /** Right-side slot: action icon(s) for sub-pages or home action cluster. */
  right?: React.ReactNode;
  /** Home mode: left-side branding (logo + name) instead of a title. */
  brand?: React.ReactNode;
}

/**
 * Full-width top app bar that sits flush with the screen background, so it
 * blends into the system status-bar area above it.
 *
 * Layout is edge-pinned: back arrow / brand sit at the far left, the right
 * slot at the far right. The bar spans the full width of the app shell and
 * stays fixed while the content region below it scrolls.
 */
export function AppTopBar({ title, onBack, right, brand }: AppTopBarProps) {
  const theme = useTheme();
  const { bg, fg } = getTopBarColors(theme);

  return (
    <Animated.View
      entering={FadeIn.duration(TOKENS.motion.enter.fadeDuration)}
      style={[styles.bar, { backgroundColor: bg }]}
    >
      <View style={styles.leftGroup}>
        {onBack ? (
          <IconButton
            icon="arrow-left"
            size={TOKENS.iconSize.md}
            iconColor={fg}
            containerColor={theme.colors.surfaceVariant}
            onPress={onBack}
            style={styles.backBtn}
            accessibilityLabel="Go back"
          />
        ) : null}
        {brand ??
          (title ? (
            <Text variant="titleLarge" style={[styles.title, { color: fg }]} numberOfLines={1}>
              {title}
            </Text>
          ) : null)}
      </View>
      {right ? <View style={styles.rightGroup}>{right}</View> : null}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: TOKENS.layout.topBarHeight,
    paddingHorizontal: TOKENS.spacing.xs,
    gap: TOKENS.spacing.sm,
  },
  leftGroup: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.xl,
    minWidth: 0,
  },
  backBtn: {
    margin: 0,
  },
  title: {
    fontWeight: TOKENS.typography.weight.bold,
    flexShrink: 1,
  },
  rightGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    flexShrink: 0,
  },
});
