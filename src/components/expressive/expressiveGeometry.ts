import { clamp } from '@/utils/math';

export const STOP_DOT_SIZE = 4;
export const TRACK_END_GAP = 3;

export interface ProgressGeometryOptions {
  value: number;
  max: number;
}

export function getProgressRatio({ value, max }: ProgressGeometryOptions): number {
  if (!Number.isFinite(value) || !Number.isFinite(max) || max <= 0) {
    return 0;
  }

  return clamp(value / max, 0, 1);
}

export function getProgressAccessibilityValue({ value, max }: ProgressGeometryOptions) {
  const safeMax = Number.isFinite(max) && max > 0 ? max : 0;
  const safeNow = clamp(Number.isFinite(value) ? value : 0, 0, safeMax);

  return {
    min: 0,
    max: safeMax,
    now: safeNow,
  };
}

export function getStopDotOpacity(ratio: number): number {
  return ratio > 0 ? 1 : 0;
}

export function createShapeSurfacePath(variant: 'soft' | 'outline' | 'blob'): string {
  if (variant === 'outline') {
    return 'M8 10 C22 2 78 2 92 14 C101 31 93 75 83 90 C61 101 24 96 9 84 C-1 66 1 25 8 10 Z';
  }

  if (variant === 'blob') {
    return 'M10 18 C23 -2 66 1 85 13 C103 25 98 71 83 88 C63 105 19 93 7 76 C-7 55 -1 33 10 18 Z';
  }

  return 'M7 16 C20 3 76 0 91 17 C103 37 91 82 75 91 C52 101 19 94 8 78 C-2 55 -1 29 7 16 Z';
}
