import { useCallback } from 'react';
import { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import { TOKENS } from '@/theme/tokens';

/**
 * Spring press feedback: scale down on press-in, spring back on press-out.
 * Uses the shared `tap` spring (fast, restrained — no visible overshoot).
 * Spread the returned handlers onto a Pressable/TouchableRipple and apply
 * `animatedStyle` to a wrapping Animated.View.
 */
export function usePressAnimation(scaleTo = 0.97) {
  const scale = useSharedValue(1);

  const onPressIn = useCallback(() => {
    scale.value = withSpring(scaleTo, TOKENS.motion.spring.tap);
  }, [scale, scaleTo]);

  const onPressOut = useCallback(() => {
    scale.value = withSpring(1, TOKENS.motion.spring.tap);
  }, [scale]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  return { animatedStyle, onPressIn, onPressOut };
}
