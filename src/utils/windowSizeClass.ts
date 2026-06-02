import { TOKENS } from '../theme/tokens';

export type WindowSizeClass = 'compact' | 'medium' | 'expanded';

export function getWindowSizeClass(width: number): WindowSizeClass {
  if (width >= TOKENS.breakpoints.expanded) {
    return 'expanded';
  }

  if (width >= TOKENS.breakpoints.medium) {
    return 'medium';
  }

  return 'compact';
}

export function isExpandedWindowSize(width: number): boolean {
  return getWindowSizeClass(width) === 'expanded';
}

export function getNavigationRailWidth(width: number): number {
  const sizeClass = getWindowSizeClass(width);

  if (sizeClass === 'compact') {
    return 0;
  }

  return TOKENS.layout.railWidth;
}
