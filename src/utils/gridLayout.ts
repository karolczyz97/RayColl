import { TOKENS } from '../theme/tokens';

export function getDeterministicContainerWidth(
  windowWidth: number,
  screenMaxWidth: number,
  hasScrollViewPadding: boolean,
  isWeb: boolean,
): number {
  if (isWeb) {
    // Web: Card has paddingHorizontal: TOKENS.spacing.lg (16px on each side = 32px total).
    // The ScrollView has scrollbarGutter: 'stable both-edges' (8px scrollbar width on each side = 16px total).
    const scrollbarGutterWidth = 16;
    const availableWidth = windowWidth - scrollbarGutterWidth;
    const cardMaxWidth = Math.min(TOKENS.layout.maxWidth, screenMaxWidth);
    const cardWidth = Math.min(cardMaxWidth, availableWidth);
    return Math.max(0, cardWidth - TOKENS.spacing.lg * 2);
  } else {
    // Native: contentRegion has nativePadding (TOKENS.spacing.lg on each side = 32px total).
    const availableWidth = windowWidth - TOKENS.spacing.lg * 2;
    return Math.max(0, Math.min(screenMaxWidth, availableWidth));
  }
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
