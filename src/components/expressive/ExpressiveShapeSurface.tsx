import React from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { useTheme } from 'react-native-paper';
import Svg, { Path } from 'react-native-svg';

import { TOKENS } from '../../theme/tokens';
import { createShapeSurfacePath } from './expressiveGeometry';
import { getExpressiveColors, type ExpressiveColorRole } from './expressiveColors';

interface ExpressiveShapeSurfaceProps {
  children: React.ReactNode;
  variant?: 'soft' | 'outline' | 'blob';
  colorRole?: ExpressiveColorRole;
  style?: StyleProp<ViewStyle>;
}

export function ExpressiveShapeSurface({
  children,
  variant = 'soft',
  colorRole = 'primary',
  style,
}: ExpressiveShapeSurfaceProps) {
  const theme = useTheme();
  const colors = getExpressiveColors(theme, colorRole);
  const path = createShapeSurfacePath(variant);

  return (
    <View style={[styles.root, style]}>
      <Svg
        pointerEvents="none"
        width="100%"
        height="100%"
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        style={StyleSheet.absoluteFill}
      >
        <Path
          d={path}
          fill={variant === 'outline' ? 'transparent' : colors.container}
          stroke={variant === 'outline' ? colors.outline : colors.container}
          strokeWidth={variant === 'outline' ? 2 : 0}
        />
      </Svg>
      <View style={styles.content}>{children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    position: 'relative',
    overflow: 'hidden',
    borderRadius: TOKENS.radius.lg,
  },
  content: {
    flex: 1,
    position: 'relative',
  },
});
