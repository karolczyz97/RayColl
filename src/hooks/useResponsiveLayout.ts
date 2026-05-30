import { useWindowDimensions } from 'react-native';
import { TOKENS } from '../theme/tokens';

export interface ResponsiveLayout {
  width: number;
  height: number;
  isCompact: boolean;
  isMedium: boolean;
  isExpanded: boolean;
  isDesktop: boolean;
  contentMaxWidth: number;
  formMaxWidth: number;
  cardMaxWidth: number;
  gridGap: number;
  sectionGap: number;
  useTwoColumnLayout: boolean;
}

export function useResponsiveLayout(): ResponsiveLayout {
  const { width, height } = useWindowDimensions();

  const isCompact = width < 600;
  const isMedium = width >= 600 && width < 904;
  const isExpanded = width >= 904;
  const isDesktop = isExpanded;

  const contentMaxWidth = TOKENS.layout.maxWidth;
  const formMaxWidth = TOKENS.layout.formMaxWidth;
  const cardMaxWidth = TOKENS.layout.cardMaxWidth;

  const gridGap = isCompact ? 12 : isMedium ? 16 : 24;
  const sectionGap = isCompact ? 16 : isMedium ? 20 : 28;

  // two column layout is active only on expanded (desktop) widths
  const useTwoColumnLayout = isExpanded;

  return {
    width,
    height,
    isCompact,
    isMedium,
    isExpanded,
    isDesktop,
    contentMaxWidth,
    formMaxWidth,
    cardMaxWidth,
    gridGap,
    sectionGap,
    useTwoColumnLayout,
  };
}
