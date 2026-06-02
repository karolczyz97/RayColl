import { useCallback } from 'react';
import { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import { TOKENS } from '../theme/tokens';

/**
 * Expressive spring press feedback. Scales the element down on press-in and
 * springs back on press-out using the shared `expressive` spring spec. Spread
 * the returned handlers onto a Pressable and `animatedStyle` onto a wrapping
 * Animated.View.
 */
export function usePressAnimation(scaleTo = 0.96) {
  const scale = useSharedValue(1);

  const onPressIn = useCallback(() => {
    scale.value = withSpring(scaleTo, TOKENS.motion.spring.expressive);
  }, [scale, scaleTo]);

  const onPressOut = useCallback(() => {
    scale.value = withSpring(1, TOKENS.motion.spring.expressive);
  }, [scale]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  return { animatedStyle, onPressIn, onPressOut };
}
