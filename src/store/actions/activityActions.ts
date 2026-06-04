import { getLocalDateString } from '@/store/selectors/stats';

export function recordActivityAction(heatmap: Record<string, number>): {
  nextHeatmap: Record<string, number>;
  todayKey: string;
} {
  const todayKey = getLocalDateString(new Date());

  const nextHeatmap = {
    ...heatmap,
    [todayKey]: (heatmap[todayKey] || 0) + 1,
  };

  return { nextHeatmap, todayKey };
}
