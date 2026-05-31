import React from 'react';
import { StyleSheet, View } from 'react-native';
import { IconButton, Text, useTheme } from 'react-native-paper';
import Animated, { FadeIn } from 'react-native-reanimated';
import { MOTION } from '../../theme/motion';
import { TOKENS } from '../../theme/tokens';
import { getTopBarColors } from '../../theme/semanticColors';

interface AppTopBarProps {
  /** Sub-page mode: title shown next to the back arrow. */
  title?: string;
  /** Renders a back arrow on the far left. */
  onBack?: () => void;
  /** Right-side slot: action icon(s) for sub-pages or home action cluster. */
  right?: React.ReactNode;
  /** Home mode: left-side branding (logo + name) instead of a title. */
  brand?: React.ReactNode;
  backAccessibilityLabel?: string;
}

/**
 * Full-width top app bar with its own strong background color.
 *
 * Layout is edge-pinned: back arrow / brand sit at the far left, the right
 * slot at the far right. The bar spans the full width of the app shell and
 * stays fixed while the content region below it scrolls.
 */
export function AppTopBar({
  title,
  onBack,
  right,
  brand,
  backAccessibilityLabel = 'Go back',
}: AppTopBarProps) {
  const theme = useTheme();
  const { bg, fg } = getTopBarColors(theme);

  return (
    <Animated.View
      entering={FadeIn.duration(MOTION.fadeDuration)}
      style={[styles.bar, { backgroundColor: bg }]}
    >
      <View style={styles.leftGroup}>
        {onBack ? (
          <IconButton
            icon="arrow-left"
            size={24}
            iconColor={fg}
            onPress={onBack}
            style={styles.backBtn}
            accessibilityLabel={backAccessibilityLabel}
          />
        ) : null}
        {brand ?? (title ? (
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
    borderBottomLeftRadius: TOKENS.radius.xl,
    borderBottomRightRadius: TOKENS.radius.xl,
  },
  leftGroup: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.sm,
    minWidth: 0,
  },
  backBtn: {
    margin: 0,
  },
  title: {
    fontWeight: 'bold',
    flexShrink: 1,
  },
  rightGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    flexShrink: 0,
  },
});
