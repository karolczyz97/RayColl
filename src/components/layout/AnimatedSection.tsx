import React from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { getEnterDelay, MOTION } from '../../theme/motion';

interface AnimatedSectionProps {
  index?: number;
  order?: number;
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
}

export function AnimatedSection({
  index = 0,
  order,
  children,
  style,
}: AnimatedSectionProps) {
  const delayOrder = order ?? index;

  return (
    <Animated.View
      entering={FadeInDown.springify()
        .damping(MOTION.spring.damping)
        .stiffness(MOTION.spring.stiffness)
        .mass(MOTION.spring.mass)
        .delay(getEnterDelay(delayOrder))}
      style={style}
    >
      {children}
    </Animated.View>
  );
}
