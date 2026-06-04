import { useWindowDimensions } from 'react-native';
import { TOKENS } from '@/theme/tokens';
import {
  getNavigationRailWidth,
  getWindowSizeClass,
  isExpandedWindowSize,
  type WindowSizeClass,
} from '@/utils/windowSizeClass';

export interface ResponsiveLayout {
  width: number;
  height: number;
  windowSizeClass: WindowSizeClass;
  contentWidth: number;
  contentSizeClass: WindowSizeClass;
  isCompact: boolean;
  isMedium: boolean;
  isExpanded: boolean;
  isDesktop: boolean;
  contentMaxWidth: number;
  formMaxWidth: number;
  cardMaxWidth: number;
  useTwoColumnLayout: boolean;
  /** Whether the persistent navigation rail is shown (medium+ widths). */
  showNavigationRail: boolean;
  /** Alias for shell code that should use rail wording instead of drawer wording. */
  showPersistentNavigation: boolean;
  /** Width reserved on-screen by the rail (0 on compact). */
  navWidth: number;
}

/**
 * Single source of responsive state, driven by MD3 window size classes:
 * compact < 600, medium 600-839, expanded 840+.
 */
export function useResponsiveLayout(): ResponsiveLayout {
  const { width, height } = useWindowDimensions();

  const windowSizeClass = getWindowSizeClass(width);
  const isCompact = windowSizeClass === 'compact';
  const isMedium = windowSizeClass === 'medium';
  const isExpanded = windowSizeClass === 'expanded';
  const isDesktop = isExpanded;

  const navWidth = getNavigationRailWidth(width);
  const showNavigationRail = navWidth > 0;
  const contentWidth = Math.max(0, width - navWidth);
  const contentSizeClass = getWindowSizeClass(contentWidth);

  return {
    width,
    height,
    windowSizeClass,
    contentWidth,
    contentSizeClass,
    isCompact,
    isMedium,
    isExpanded,
    isDesktop,
    contentMaxWidth: TOKENS.layout.maxWidth,
    formMaxWidth: TOKENS.layout.formMaxWidth,
    cardMaxWidth: TOKENS.layout.cardMaxWidth,
    useTwoColumnLayout: isExpandedWindowSize(contentWidth),
    showNavigationRail,
    showPersistentNavigation: showNavigationRail,
    navWidth,
  };
}
