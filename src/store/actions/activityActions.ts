export function recordActivityAction(heatmap: Record<string, number>): {
  nextHeatmap: Record<string, number>;
  todayKey: string;
} {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const todayKey = `${year}-${month}-${day}`;

  const nextHeatmap = {
    ...heatmap,
    [todayKey]: (heatmap[todayKey] || 0) + 1,
  };

  return { nextHeatmap, todayKey };
}
