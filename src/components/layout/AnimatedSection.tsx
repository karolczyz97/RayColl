import React from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { MOTION } from '../../theme/motion';

interface AnimatedSectionProps {
  index?: number;
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
}

export function AnimatedSection({ index = 0, children, style }: AnimatedSectionProps) {
  return (
    <Animated.View
      entering={FadeInDown.springify()
        .damping(MOTION.spring.damping)
        .stiffness(MOTION.spring.stiffness)
        .mass(MOTION.spring.mass)
        .delay(Math.min(index * MOTION.enterDelayStep, MOTION.enterDelayMax))}
      style={style}
    >
      {children}
    </Animated.View>
  );
}
