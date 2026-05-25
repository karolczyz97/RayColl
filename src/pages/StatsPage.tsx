import { useMemo } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Grid from '@mui/material/Grid';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Tooltip from '@mui/material/Tooltip';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import SchoolIcon from '@mui/icons-material/School';
import ReplayIcon from '@mui/icons-material/Replay';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import { useTheme } from '@mui/material/styles';
import { useNav } from '../App';
import { SegmentedProgressBar, computeCardStats } from '../components/SegmentedProgressBar';
import { PageHeader } from '../components/PageHeader';
import { useI18n } from '../i18n';

function computeStreak(heatmap: Record<string, number>): number {
  const today = new Date();
  let streak = 0;
  for (let i = 0; i < 365; i++) {
    const d = new Date(today); d.setDate(d.getDate() - i);
    const key = d.toISOString().slice(0, 10);
    if (heatmap[key]) streak++; else break;
  }
  return streak;
}

function HeatmapSvg({ heatmap, t }: { heatmap: Record<string, number>; t: (key: string) => string }) {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';
  
  const cols = 20, rows = 7;
  const cellSize = 14, gap = 3;
  const today = new Date();
  const cells: { x: number; y: number; date: string; count: number }[] = [];

  for (let col = cols - 1; col >= 0; col--) {
    for (let row = 0; row < rows; row++) {
      const daysBack = (cols - 1 - col) * 7 + (6 - row);
      const d = new Date(today); d.setDate(d.getDate() - daysBack);
      const key = d.toISOString().slice(0, 10);
      cells.push({ x: col * (cellSize + gap) + 30, y: row * (cellSize + gap), date: key, count: heatmap[key] || 0 });
    }
  }

  const colorFor = (count: number): string => {
    if (count === 0) return isDark ? '#2d2d3a' : '#eaeaf0';
    if (isDark) {
      if (count <= 2) return '#0e4429';
      if (count <= 5) return '#006d32';
      if (count <= 10) return '#26a641';
      return '#39d353';
    } else {
      if (count <= 2) return '#c6e48b';
      if (count <= 5) return '#7bc96f';
      if (count <= 10) return '#239a3b';
      return '#196127';
    }
  };

  const w = cols * (cellSize + gap) + 30, h = rows * (cellSize + gap);
  const dayLabels = [t('stats.day.mon'), '', t('stats.day.wed'), '', t('stats.day.fri'), '', t('stats.day.sun')];

  return (
    <svg width="100%" viewBox={`0 0 ${w} ${h}`} style={{ maxWidth: 500 }}>
      {dayLabels.map((label, i) => label && (
        <text key={i} x={0} y={i * (cellSize + gap) + cellSize - 2} fontSize={9} fill={isDark ? '#888' : '#666'}>{label}</text>
      ))}
      {cells.map((c, i) => (
        <Tooltip key={i} title={`${c.date}: ${c.count}`} arrow>
          <rect x={c.x} y={c.y} width={cellSize} height={cellSize} rx={3} fill={colorFor(c.count)} style={{ cursor: 'pointer' }} />
        </Tooltip>
      ))}
    </svg>
  );
}

export function StatsPage() {
  const { goBack, store } = useNav();
  const { t } = useI18n();
  const { groups, activityHeatmap, getDueCards } = store;

  const totalCards = groups.reduce((a, g) => a + g.cards.length, 0);
  const totalDue = groups.reduce((a, g) => a + getDueCards(g.id).length, 0);
  const streak = useMemo(() => computeStreak(activityHeatmap), [activityHeatmap]);
  const activeDays = Object.keys(activityHeatmap).length;

  // Global card stats across all groups
  const allCards = useMemo(() => groups.flatMap(g => g.cards), [groups]);
  const globalStats = useMemo(() => computeCardStats(allCards), [allCards]);

  return (
    <Box sx={{ p: 3, pb: 12, maxWidth: 800, mx: 'auto' }}>
      <PageHeader title={t('stats.title')} onBack={goBack} />

      <Grid container spacing={2} sx={{ mb: 4 }}>
        {[
          { icon: <LocalFireDepartmentIcon />, label: t('stats.streak'), value: `${streak} 🔥`, color: 'warning.main' },
          { icon: <SchoolIcon />, label: t('filter.mastered'), value: `${globalStats.mastered}/${totalCards}`, color: 'success.main' },
          { icon: <ReplayIcon />, label: t('stats.due_cards'), value: String(totalDue), color: 'error.main' },
          { icon: <CalendarMonthIcon />, label: t('stats.active_days'), value: String(activeDays), color: 'primary.main' },
        ].map((m, i) => (
          <Grid size={{ xs: 6, sm: 3 }} key={i}>
            <Card>
              <CardContent sx={{ textAlign: 'center', py: 2.5 }}>
                <Box sx={{ color: m.color, mb: 1 }}>{m.icon}</Box>
                <Typography variant="caption" color="text.secondary">{m.label}</Typography>
                <Typography variant="h5" sx={{ fontWeight: 700, color: m.color }}>{m.value}</Typography>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Global progress bar */}
      <Card sx={{ mb: 4 }}>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>{t('stats.progress_title')}</Typography>
          <SegmentedProgressBar stats={globalStats} height={18} showLegend />
        </CardContent>
      </Card>

      <Card sx={{ mb: 4 }}>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>{t('stats.heatmap_title')}</Typography>
          <Box sx={{ display: 'flex', justifyContent: 'center' }}>
            <HeatmapSvg heatmap={activityHeatmap} t={t} />
          </Box>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>{t('stats.deck_progress')}</Typography>
          {groups.map(g => {
            const groupStats = computeCardStats(g.cards);
            return (
              <Box key={g.id} sx={{ mb: 2.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                  <Typography variant="body2">{g.name}</Typography>
                  <Typography variant="body2" color="text.secondary">{t('stats.cards_count', { count: g.cards.length })}</Typography>
                </Box>
                <SegmentedProgressBar stats={groupStats} height={10} />
              </Box>
            );
          })}
        </CardContent>
      </Card>
    </Box>
  );
}
