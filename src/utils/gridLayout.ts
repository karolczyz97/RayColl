import { TOKENS } from '../theme/tokens';

interface GridContainerWidthParams {
  measuredWidth?: number;
  windowWidth: number;
  maxContainerWidth?: number;
  horizontalInset?: number;
  clampMeasuredToFallback?: boolean;
}

export function getGridContainerWidth({
  measuredWidth,
  windowWidth,
  maxContainerWidth = TOKENS.layout.maxWidth - TOKENS.spacing.lg * 2 - TOKENS.spacing.xxs * 2,
  horizontalInset = TOKENS.spacing.lg * 2 + TOKENS.spacing.xxs * 2,
  clampMeasuredToFallback = false,
}: GridContainerWidthParams): number {
  const fallbackWidth = Math.min(maxContainerWidth, Math.max(0, windowWidth - horizontalInset));

  if (measuredWidth === undefined) {
    return fallbackWidth;
  }

  return clampMeasuredToFallback ? Math.min(measuredWidth, fallbackWidth) : measuredWidth;
}

export function getGridGap(containerWidth: number): number {
  return Math.max(
    TOKENS.layout.minGap,
    Math.min(TOKENS.layout.maxGap, Math.floor(containerWidth * TOKENS.layout.gapRatio)),
  );
}

export function getGridColumns(
  containerWidth: number,
  itemCount: number,
  minItemWidth: number,
  maxCols: number,
  gap: number,
): number {
  const calculatedCols = Math.floor((containerWidth + gap) / (minItemWidth + gap));
  const boundedCols = Math.max(1, Math.min(maxCols, calculatedCols));
  return Math.min(boundedCols, Math.max(1, itemCount));
}

export function getGridItemWidth(
  containerWidth: number,
  columns: number,
  gap: number,
): number {
  return Math.floor(((containerWidth - gap * (columns - 1)) / columns) * 100) / 100;
}
