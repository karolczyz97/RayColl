import { TOKENS } from '@/theme/tokens';

export interface RailItemLayout {
  y: number;
  height: number;
}

/**
 * Returns the Y position for the active-indicator pill so it is vertically
 * centred within the given item layout. Returns null when there is no layout
 * for the requested index (indicator hidden).
 */
export function getRailIndicatorY(
  activeIndex: number,
  itemLayouts: RailItemLayout[],
): number | null {
  const layout = itemLayouts[activeIndex];
  if (!layout) return null;
  const indicatorHeight = TOKENS.touchTarget.compact;
  return layout.y + (layout.height - indicatorHeight) / 2;
}
