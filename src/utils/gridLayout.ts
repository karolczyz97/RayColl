import { TOKENS } from '@/theme/tokens';
import { clamp } from './math';

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
    return clamp(webCardInnerWidth, 0, screenMaxWidth);
  } else {
    // Native: contentRegion has nativePadding (TOKENS.spacing.lg on each side = 32px total).
    const availableWidth = usableWindow - TOKENS.spacing.lg * 2;
    return clamp(availableWidth, 0, screenMaxWidth);
  }
}

export function getGridGap(containerWidth: number): number {
  return clamp(
    Math.floor(containerWidth * TOKENS.layout.gapRatio),
    TOKENS.layout.minGap,
    TOKENS.layout.maxGap,
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
  const boundedCols = clamp(calculatedCols, 1, maxCols);
  return clamp(itemCount, 1, boundedCols);
}

export function getGridItemWidth(
  containerWidth: number,
  columns: number,
  gap: number,
): number {
  if (columns <= 0) return 0;
  const width = (containerWidth - gap * (columns - 1)) / columns;
  return Math.max(0, Math.floor(width * 100) / 100);
}
