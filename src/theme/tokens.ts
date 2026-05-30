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
  typography: {
    size: {
      xs: 12,
      sm: 14,
      md: 16,
      lg: 20,
    },
    weight: {
      regular: '400' as const,
      medium: '500' as const,
      bold: '700' as const,
    },
  },
  opacity: {
    subtle: 0.12,
    medium: 0.32,
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
  },
  motion: {
    duration: {
      short: 150,
      medium: 300,
      long: 500,
    },
  },
};
