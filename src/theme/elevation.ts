import type { PlatformOSType, ViewStyle } from 'react-native';

import { TOKENS } from './tokens';
import { hexToRgba } from './colorUtils';

export type ElevationSpec = (typeof TOKENS.elevation)[keyof typeof TOKENS.elevation];
export type ElevationPlatform = PlatformOSType | 'native';

export function getElevationStyle(
  spec: ElevationSpec,
  shadowColor: string,
  platform: ElevationPlatform,
): ViewStyle {
  if (platform === 'web') {
    return {
      boxShadow:
        spec.opacity <= 0 || spec.blur <= 0
          ? 'none'
          : `${spec.x}px ${spec.y}px ${spec.blur}px ${hexToRgba(shadowColor, spec.opacity)}`,
    };
  }

  return {
    shadowColor,
    shadowOffset: { width: spec.x, height: spec.y },
    shadowOpacity: spec.opacity,
    shadowRadius: spec.blur,
    elevation: spec.nativeElevation,
  };
}
