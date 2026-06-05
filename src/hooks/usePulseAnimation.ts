import { useEffect } from 'react';
import { cancelAnimation, useAnimatedStyle, useSharedValue, withRepeat, withSequence, withSpring } from 'react-native-reanimated';

export function usePulseAnimation(enabled: boolean) {
  const scale = useSharedValue(1);

  useEffect(() => {
    if (enabled) {
      scale.value = withRepeat(
        withSequence(withSpring(1.25, { damping: 3 }), withSpring(1, { damping: 3 })),
        -1,
        true,
      );
    } else {
      cancelAnimation(scale);
      scale.value = 1;
    }

    return () => {
      cancelAnimation(scale);
    };
  }, [enabled, scale]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  return animatedStyle;
}
