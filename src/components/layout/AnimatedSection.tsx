import React from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { TOKENS, getTokenMotionEnterDelay } from '@/theme/tokens';

interface AnimatedSectionProps {
  order?: number;
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
}

export function AnimatedSection({
  order = 0,
  children,
  style,
}: AnimatedSectionProps) {
  const spring = TOKENS.motion.spring.standard;

  return (
    <Animated.View
      entering={FadeInDown.springify()
        .damping(spring.damping)
        .stiffness(spring.stiffness)
        .mass(spring.mass)
        .delay(getTokenMotionEnterDelay(order))}
      style={style}
    >
      {children}
    </Animated.View>
  );
}
