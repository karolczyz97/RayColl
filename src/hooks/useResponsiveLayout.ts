import { useWindowDimensions } from 'react-native';
import { TOKENS } from '@/theme/tokens';
import { getWindowSizeClass, type WindowSizeClass } from '@/utils/windowSizeClass';

export interface ResponsiveLayout {
  width: number;
  height: number;
  windowSizeClass: WindowSizeClass;
  isCompact: boolean;
  isMedium: boolean;
  isExpanded: boolean;
  isDesktop: boolean;
  contentMaxWidth: number;
  formMaxWidth: number;
  cardMaxWidth: number;
  /** Whether the persistent navigation rail is shown (medium+ widths). */
  showNavigationRail: boolean;
  /** Alias for shell code that should use rail wording instead of drawer wording. */
  showPersistentNavigation: boolean;
}

/**
 * Single source of window-level responsive state, driven by MD3 window size
 * classes: compact < 600, medium 600-839, expanded 840+.
 *
 * Rail-dependent width (how much the rail actually occupies right now — which
 * depends on the collapse toggle) is intentionally NOT computed here. That lives
 * in `NavigationShellContext`, the single source of truth for content width.
 * Computing it from window width alone could only ever be a guess that ignores
 * the collapsed/expanded state, so consumers that need rail-aware width must
 * read `useNavigationShell()` instead.
 */
export function useResponsiveLayout(): ResponsiveLayout {
  const { width, height } = useWindowDimensions();

  const windowSizeClass = getWindowSizeClass(width);
  const isCompact = windowSizeClass === 'compact';
  const isMedium = windowSizeClass === 'medium';
  const isExpanded = windowSizeClass === 'expanded';
  const isDesktop = isExpanded;

  // The rail is shown on medium+ widths. Collapsing it changes its width (owned
  // by the shell), not whether it is shown — so this stays a pure size-class check.
  const showNavigationRail = !isCompact;

  return {
    width,
    height,
    windowSizeClass,
    isCompact,
    isMedium,
    isExpanded,
    isDesktop,
    contentMaxWidth: TOKENS.layout.maxWidth,
    formMaxWidth: TOKENS.layout.formMaxWidth,
    cardMaxWidth: TOKENS.layout.cardMaxWidth,
    showNavigationRail,
    showPersistentNavigation: showNavigationRail,
  };
}
