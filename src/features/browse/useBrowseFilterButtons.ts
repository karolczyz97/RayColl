import { useMemo } from 'react';
import { useTheme } from 'react-native-paper';
import { useI18n } from '@/i18n';
import type { CardStats } from '@/store/selectors/stats';
import type { SrsCardCategory } from '@/srs/srsEngine';
import type { ExpressiveButtonGroupOption } from '@/components/expressive';
import { getReviewStatusColor } from '@/theme/semanticColors';
import { SRS_CATEGORY_ORDER, SRS_CATEGORIES_TOKENS, CATEGORY_TO_STATS_KEY } from '@/theme/srsTokens';
import { formatSrsCountLabel } from '@/i18n/plural';

export function useBrowseFilterButtons(stats: CardStats, isWide: boolean): ExpressiveButtonGroupOption<SrsCardCategory>[] {
  const theme = useTheme();
  const { t, language } = useI18n();

  return useMemo(
    () =>
      SRS_CATEGORY_ORDER.map((category: SrsCardCategory) => {
        const statsKey = CATEGORY_TO_STATS_KEY[category];
        const count = (stats[statsKey] as number) ?? 0;
        const { fg, bg } = getReviewStatusColor(theme, category);
        const token = SRS_CATEGORIES_TOKENS[category];
        const formattedLabel = formatSrsCountLabel(category, count, t, language, token.labelKey);
        
        return {
          value: category,
          label: isWide ? formattedLabel : `${count}`,
          icon: token.iconName,
          accessibilityLabel: formattedLabel,
          selectedBg: bg,
          selectedContent: fg,
          disabled: count === 0,
        };
      }),
    [stats, theme, t, language, isWide],
  );
}
