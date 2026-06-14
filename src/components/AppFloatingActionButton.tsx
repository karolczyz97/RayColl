import React from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import Animated from 'react-native-reanimated';
import { Icon, Text, TouchableRipple, useTheme } from 'react-native-paper';
import { TOKENS } from '@/theme/tokens';
import { usePressAnimation } from '@/hooks/usePressAnimation';

interface AppFloatingActionButtonProps {
  icon: string;
  onPress: () => void;
  accessibilityLabel: string;
  label?: string;
  style?: StyleProp<ViewStyle>;
  contentStyle?: StyleProp<ViewStyle>;
}

export function AppFloatingActionButton({
  icon,
  label,
  onPress,
  accessibilityLabel,
  style,
  contentStyle,
}: AppFloatingActionButtonProps) {
  const theme = useTheme();
  const extended = !!label;
  const { animatedStyle, onPressIn, onPressOut } = usePressAnimation();

  return (
    <Animated.View style={[style, animatedStyle]}>
      <TouchableRipple
        borderless
        onPress={onPress}
        onPressIn={onPressIn}
        onPressOut={onPressOut}
        style={[
          styles.root,
          extended ? styles.extended : styles.iconOnly,
          { backgroundColor: theme.colors.primaryContainer },
        ]}
        accessibilityRole="button"
        accessibilityLabel={accessibilityLabel}
      >
        <View style={[styles.content, extended && styles.contentExtended, contentStyle]}>
          <Icon source={icon} size={TOKENS.iconSize.md} color={theme.colors.onPrimaryContainer} />
          {extended ? (
            <Text
              variant="labelLarge"
              numberOfLines={1}
              style={[styles.label, { color: theme.colors.onPrimaryContainer }]}
            >
              {label}
            </Text>
          ) : null}
        </View>
      </TouchableRipple>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: {
    overflow: 'hidden',
    borderRadius: TOKENS.radius.lg,
  },
  iconOnly: {
    width: TOKENS.layout.fabIconSize,
    height: TOKENS.layout.fabIconSize,
  },
  extended: {
    minHeight: TOKENS.layout.fabMinHeight,
    minWidth: TOKENS.layout.fabExtendedMinWidth,
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  contentExtended: {
    flexDirection: 'row',
    gap: TOKENS.spacing.sm,
    paddingHorizontal: TOKENS.spacing.lg,
  },
  label: {
    flexShrink: 1,
  },
});
