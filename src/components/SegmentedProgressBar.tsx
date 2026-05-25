import Box from '@mui/material/Box';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useTheme } from '@mui/material/styles';
import type { Flashcard } from '../types/models';
import { getCardCategory } from '../srs/srsEngine';
import { useI18n } from '../i18n';

export interface CardStats {
  total: number;
  newCount: number;
  learning: number;   // state 1 or 3
  review: number;     // state 2, not yet mastered
  mastered: number;   // state 2, repetitions >= 3
}

export function computeCardStats(cards: Flashcard[]): CardStats {
  let newCount = 0, learning = 0, review = 0, mastered = 0;
  for (const c of cards) {
    const category = getCardCategory(c.srsState);
    if (category === 'new') newCount++;
    else if (category === 'learning') learning++;
    else if (category === 'review') review++;
    else if (category === 'mastered') mastered++;
  }
  return { total: cards.length, newCount, learning, review, mastered };
}

interface Props {
  stats: CardStats;
  height?: number;
  showLegend?: boolean;
}

export function SegmentedProgressBar({ stats, height = 12, showLegend = false }: Props) {
  const { total, newCount, learning, review, mastered } = stats;
  const { t } = useI18n();
  const theme = useTheme();

  if (total === 0) return (
    <Box sx={{ height, borderRadius: height / 2, bgcolor: 'action.disabledBackground' }} />
  );

  const segments = [
    { count: mastered, color: theme.palette.success.main, label: t('srs.badge.mastered') },
    { count: review, color: theme.palette.secondary.main, label: t('srs.badge.review') },
    { count: learning, color: theme.palette.warning.main, label: t('srs.badge.learning') },
    { count: newCount, color: theme.palette.info.main, label: t('srs.badge.new') },
  ].filter(s => s.count > 0);

  return (
    <Box>
      <Box sx={{
        display: 'flex', borderRadius: height / 2, overflow: 'hidden',
        height, bgcolor: 'action.disabledBackground',
      }}>
        {segments.map((seg, i) => (
          <Tooltip key={i} title={`${seg.label}: ${seg.count} (${Math.round(seg.count / total * 100)}%)`}>
            <Box sx={{
              width: `${(seg.count / total) * 100}%`, bgcolor: seg.color,
              transition: 'width 0.5s ease',
            }} />
          </Tooltip>
        ))}
      </Box>
      {showLegend && (
        <Box sx={{ display: 'flex', gap: 2, mt: 1, flexWrap: 'wrap', justifyContent: 'center' }}>
          {[
            { color: theme.palette.success.main, label: t('srs.badge.mastered'), count: mastered },
            { color: theme.palette.secondary.main, label: t('srs.badge.review'), count: review },
            { color: theme.palette.warning.main, label: t('srs.badge.learning'), count: learning },
            { color: theme.palette.info.main, label: t('srs.badge.new'), count: newCount },
          ].map((item, i) => (
            <Box key={i} sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: item.color }} />
              <Typography variant="caption" color="text.secondary">
                {item.label} ({item.count})
              </Typography>
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
}
