export const TOKENS = {
  spacing: {
    xxs: 2,
    xs: 4,
    sm: 8,
    md: 12,
    lg: 16,
    xl: 24,
    xxl: 32,
  },
  radius: {
    xs: 4,
    sm: 8,
    md: 12,
    lg: 16,
    xl: 20,
    xxl: 28,
    pill: 9999,
    full: 9999,
  },
  // Icon size scale. Snap icon `size=` props to these instead of ad-hoc numbers.
  iconSize: {
    xs: 16, // inline / dense metadata
    sm: 20, // list items, metric cards
    md: 24, // standard: app bar, icon buttons
    lg: 36, // prominent actions
    xl: 48, // large / avatar-scale
    display: 96, // empty-state hero
  },
  typography: {
    size: {
      xxxs: 9,
      xxs: 11,
      xs: 12,
      sm: 14,
      md: 16,
      lg: 20,
      xl: 24,
    },
    weight: {
      regular: '400' as const,
      medium: '500' as const,
      semibold: '600' as const,
      bold: '700' as const,
    },
  },
  opacity: {
    subtle: 0.12,
    medium: 0.32,
    muted: 0.62,
  },
  surface: {
    hoverScale: 1.015,
  },
  touchTarget: {
    min: 48,
    compact: 40,
  },
  control: {
    height: 48,
    compactHeight: 40,
    barHeight: 8,
    progressHeight: 4,
    borderRadius: 12,
    roundedBorderRadius: 24,
  },
  menu: {
    gap: 4,
    itemHeight: 48,
    minWidth: 200,
  },
  dialog: {
    width: '92%',
    maxWidth: 440,
  },
  splitButton: {
    height: 48,
    trailingWidth: 48,
    borderRadius: 9999,
  },
  // MD3 window size class thresholds (px). Single source for all responsive logic.
  breakpoints: {
    medium: 600, // compact -> medium
    expanded: 840, // medium -> expanded
    large: 1240, // expanded -> large
  },
  layout: {
    maxWidth: 1200,
    formMaxWidth: 800,
    cardMaxWidth: 450,
    webWidth: '100%',
    minCardWidth: 280,
    maxCols: 4,
    minGap: 8,
    maxGap: 32,
    gapRatio: 0.0266,
    topBarHeight: 64,
    bottomBarHeight: 80,
    railWidth: 80, // MD3 navigation rail (medium widths)
    expandedRailWidth: 240, // MD3 expanded navigation rail (expanded widths)
    drawerWidth: 240, // Back-compat alias for existing layout code.
    actionMaxWidth: 320,
    studyCardMaxWidth: 600,
    deckSettingsSingleColumnMaxWidth: 600,
    // Heatmap
    heatmapGridMaxWidth: 337,
    heatmapCellSize: 14,
    heatmapDayLabelWidth: 24,
    heatmapDayLabelOffset: -28,
    heatmapGridHeight: 116,
    // FAB
    fabIconSize: 56,
    fabExtendedMinWidth: 80,
    fabMinHeight: 56,
    // Navigation rail collapsed
    collapsedRailItemWidth: 72,
    collapsedRailItemMinHeight: 64,
    collapsedRailItemLabelWidth: 68,
    collapsedRailItemLabelLineHeight: 14,
    collapsedRailIconContainerWidth: 56,
    collapsedRailIconContainerHeight: 32,
    collapsedRailBrandMinHeight: 56,
    collapsedRailImportActionSize: 56,
    // Form
    formSelectWidth: 160,
    // Banners
    syncBannerMaxWidth: 520,
    // Study controls
    studyControlZoneTall: 180,
    studyControlZoneCompact: 80,
    sttTextWrapperHeight: 80,
    audioIconsRowHeight: 60,
  },
  emptyState: {
    accentWidth: 112,
    accentHeight: 96,
  },
  // MD3 elevation specs. Convert these with getElevationStyle(level, shadowColor, platform)
  // so web can use boxShadow while native keeps the current shadow props.
  elevation: {
    level0: { x: 0, y: 0, blur: 0, opacity: 0, nativeElevation: 0 },
    level1: { x: 0, y: 1, blur: 3, opacity: 0.05, nativeElevation: 1 },
    level2: { x: 0, y: 2, blur: 10, opacity: 0.06, nativeElevation: 2 },
    level3: { x: 0, y: 4, blur: 16, opacity: 0.08, nativeElevation: 4 },
    level4: { x: 0, y: 6, blur: 20, opacity: 0.1, nativeElevation: 8 },
    level5: { x: 0, y: 8, blur: 24, opacity: 0.12, nativeElevation: 12 },
  },
  motion: {
    duration: {
      short: 150,
      medium: 300,
      long: 500,
      snackbar: 6000,
    },
    // Staggered section enter — canonical home for the former theme/motion.ts values.
    enter: {
      delayStep: 70,
      delayMax: 490,
      fadeDuration: 300,
    },
    // Reanimated spring specs. `standard` preserves the prior AnimatedSection feel;
    // `expressive` is springier, for Expressive press/enter polish (Task 2+).
    spring: {
      standard: { damping: 18, stiffness: 140, mass: 0.8 },
      expressive: { damping: 14, stiffness: 170, mass: 0.9 },
    },
  },
};

export function getTokenMotionEnterDelay(order = 0): number {
  return Math.min(
    Math.max(0, order) * TOKENS.motion.enter.delayStep,
    TOKENS.motion.enter.delayMax,
  );
}
