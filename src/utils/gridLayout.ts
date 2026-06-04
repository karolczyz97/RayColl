import { TOKENS } from '@/theme/tokens';

export function getDeterministicContainerWidth(
  windowWidth: number,
  screenMaxWidth: number,
  isWeb: boolean,
  // Width reserved by a persistent navigation rail (0 when absent).
  reservedWidth = 0,
): number {
  const usableWindow = Math.max(0, windowWidth - reservedWidth);
  if (isWeb) {
    // Web: webCard has paddingHorizontal: TOKENS.spacing.lg (16px each side = 32px total).
    // ScrollView has scrollbarGutter: 'stable both-edges' (8px each side = 16px total).
    // ScreenContent applies maxWidth: screenMaxWidth inside the padded card.
    // Grid container width = min(screenMaxWidth, webCardInnerWidth).
    const scrollbarGutterWidth = 16;
    const availableWidth = usableWindow - scrollbarGutterWidth;
    const webCardInnerWidth = Math.min(TOKENS.layout.maxWidth, availableWidth) - TOKENS.spacing.lg * 2;
    return Math.max(0, Math.min(screenMaxWidth, webCardInnerWidth));
  } else {
    // Native: contentRegion has nativePadding (TOKENS.spacing.lg on each side = 32px total).
    const availableWidth = usableWindow - TOKENS.spacing.lg * 2;
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
