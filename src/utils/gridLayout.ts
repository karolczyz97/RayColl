import { TOKENS } from '../theme/tokens';

export function getDeterministicContainerWidth(
  windowWidth: number,
  screenMaxWidth: number,
  hasScrollViewPadding: boolean,
  isWeb: boolean,
): number {
  // 1. Outermost container constraint (only on Web)
  const outerWidth =
    isWeb
      ? Math.min(TOKENS.layout.maxWidth, windowWidth)
      : windowWidth;

  // 2. SafeAreaView padding (TOKENS.spacing.sm on each side = 16px total)
  const safeAreaContentWidth = outerWidth - TOKENS.spacing.sm * 2;

  // 3. ScreenContent maxWidth constraint
  const screenContentWidth = Math.min(screenMaxWidth, safeAreaContentWidth);

  // 4. ScreenContent padding (TOKENS.spacing.sm on each side = 16px total)
  let contentWidth = screenContentWidth - TOKENS.spacing.sm * 2;

  // 5. ScrollView padding (TOKENS.spacing.lg on each side = 32px total)
  if (hasScrollViewPadding) {
    contentWidth -= TOKENS.spacing.lg * 2;
  }

  return Math.max(0, contentWidth);
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
