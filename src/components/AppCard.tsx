import React, { useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet } from 'react-native';
import type { ViewStyle } from 'react-native';
import { Card } from 'react-native-paper';
import type { ComponentProps } from 'react';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

type PaperCardProps = ComponentProps<typeof Card>;

function AppCardBase({ style, ...props }: PaperCardProps) {
  const [hovered, setHovered] = useState(false);
  const scale = useSharedValue(1);

  const isHoverEnabled = Platform.OS === 'web';
  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const handleHoverIn = () => {
    if (!isHoverEnabled) return;
    setHovered(true);
    scale.value = withTiming(1.015, { duration: 120 });
  };

  const handleHoverOut = () => {
    if (!isHoverEnabled) return;
    setHovered(false);
    scale.value = withTiming(1, { duration: 120 });
  };

  const layoutStyle = useMemo(() => {
    const flatStyle = StyleSheet.flatten(style) as ViewStyle | undefined;
    return {
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
      ...(Platform.OS === 'web' ? { cursor: 'default' } : {}) as any,
    };
  }, [style]);

  const cardStyle = useMemo(() => [style, styles.card, hovered && styles.hovered], [hovered, style]);

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
  hovered: {
    boxShadow: '0 0 0 1px rgba(208, 188, 255, 0.22), 0 10px 24px rgba(0, 0, 0, 0.18)',
  } as ViewStyle,
});
