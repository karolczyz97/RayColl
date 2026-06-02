import type { PlatformOSType, ViewStyle } from 'react-native';

import { TOKENS } from './tokens';

export type ElevationSpec = (typeof TOKENS.elevation)[keyof typeof TOKENS.elevation];
export type ElevationPlatform = PlatformOSType | 'native';

function clampAlpha(value: number): number {
  return Math.max(0, Math.min(1, value));
}

function withOpacity(color: string, opacity: number): string {
  const alpha = clampAlpha(opacity);
  const normalized = color.trim();

  const hex = normalized.startsWith('#') ? normalized.slice(1) : '';
  const expandedHex =
    hex.length === 3
      ? hex
          .split('')
          .map((part) => `${part}${part}`)
          .join('')
      : hex;

  if (/^[0-9a-fA-F]{6}$/.test(expandedHex)) {
    const red = Number.parseInt(expandedHex.slice(0, 2), 16);
    const green = Number.parseInt(expandedHex.slice(2, 4), 16);
    const blue = Number.parseInt(expandedHex.slice(4, 6), 16);
    return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
  }

  const rgbaMatch = normalized.match(
    /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})(?:\s*,\s*([0-9.]+))?\s*\)$/i,
  );

  if (rgbaMatch) {
    const [, red, green, blue, sourceAlpha] = rgbaMatch;
    const parsedSourceAlpha = sourceAlpha === undefined ? 1 : Number(sourceAlpha);
    const finalAlpha = clampAlpha(parsedSourceAlpha * alpha);
    return `rgba(${red}, ${green}, ${blue}, ${finalAlpha})`;
  }

  return `rgba(0, 0, 0, ${alpha})`;
}

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
          : `${spec.x}px ${spec.y}px ${spec.blur}px ${withOpacity(shadowColor, spec.opacity)}`,
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
