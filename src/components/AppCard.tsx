import React, { useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet } from 'react-native';
import type { ViewStyle } from 'react-native';
import { Card, useTheme } from 'react-native-paper';
import type { ComponentProps } from 'react';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import { TOKENS } from '../theme/tokens';

type PaperCardProps = ComponentProps<typeof Card>;

function AppCardBase({ style, ...props }: PaperCardProps) {
  const [hovered, setHovered] = useState(false);
  const theme = useTheme();
  const scale = useSharedValue(1);

  const isHoverEnabled = Platform.OS === 'web';
  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const handleHoverIn = () => {
    if (!isHoverEnabled) return;
    setHovered(true);
    scale.value = withTiming(TOKENS.surface.hoverScale, {
      duration: TOKENS.motion.duration.short,
    });
  };

  const handleHoverOut = () => {
    if (!isHoverEnabled) return;
    setHovered(false);
    scale.value = withTiming(1, { duration: TOKENS.motion.duration.short });
  };

  const layoutStyle = useMemo(() => {
    const flatStyle = StyleSheet.flatten(style) as ViewStyle | undefined;
    const nextStyle: ViewStyle = {
      alignSelf: flatStyle?.alignSelf,
      flex: flatStyle?.flex,
      flexBasis: flatStyle?.flexBasis,
      flexGrow: flatStyle?.flexGrow,
      flexShrink: flatStyle?.flexShrink,
      margin: flatStyle?.margin,
      marginBottom: flatStyle?.marginBottom,
      marginEnd: flatStyle?.marginEnd,
      marginHorizontal: flatStyle?.marginHorizontal,
      marginLeft: flatStyle?.marginLeft,
      marginRight: flatStyle?.marginRight,
      marginStart: flatStyle?.marginStart,
      marginTop: flatStyle?.marginTop,
      marginVertical: flatStyle?.marginVertical,
      maxWidth: flatStyle?.maxWidth,
      minWidth: flatStyle?.minWidth,
      width: flatStyle?.width ?? '100%',
    };

    if (Platform.OS === 'web') {
      nextStyle.cursor = 'auto';
    }

    return nextStyle;
  }, [style]);

  const hoverStyle = useMemo<ViewStyle | undefined>(() => {
    if (!hovered || Platform.OS !== 'web') return undefined;
    return {
      boxShadow: `0 0 0 1px ${theme.colors.outlineVariant}, 0 10px 24px ${theme.colors.shadow}`,
    } as ViewStyle;
  }, [hovered, theme.colors.outlineVariant, theme.colors.shadow]);

  const cardStyle = useMemo(() => [style, styles.card, hoverStyle], [hoverStyle, style]);

  return (
    <Pressable
      onHoverIn={handleHoverIn}
      onHoverOut={handleHoverOut}
      style={layoutStyle}
    >
      <Animated.View style={[styles.hoverLayer, animatedStyle]}>
        <Card {...props} style={cardStyle} />
      </Animated.View>
    </Pressable>
  );
}

export const AppCard = Object.assign(AppCardBase, {
  Actions: Card.Actions,
  Content: Card.Content,
  Cover: Card.Cover,
  Title: Card.Title,
});

const styles = StyleSheet.create({
  card: {
    width: '100%',
  },
  hoverLayer: {
    overflow: 'visible',
  },
});
