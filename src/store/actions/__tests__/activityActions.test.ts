import { getLocalDateString } from '@/utils/date';
import { recordActivityAction } from '../cardActions';

describe('activityActions', () => {
  afterEach(() => {
    jest.useRealTimers();
  });

  it('records the first review for the current local day', () => {
    jest.useFakeTimers().setSystemTime(new Date('2026-06-05T12:00:00'));

    const result = recordActivityAction({});

    expect(result.todayKey).toBe('2026-06-05');
    expect(result.nextHeatmap['2026-06-05']).toBe(1);
  });

  it('increments an existing review count for the same local day', () => {
    jest.useFakeTimers().setSystemTime(new Date('2026-06-05T12:00:00'));

    const result = recordActivityAction({ '2026-06-05': 1 });

    expect(result.nextHeatmap['2026-06-05']).toBe(2);
  });

  it('formats local dates around midnight using the local calendar day', () => {
    expect(getLocalDateString(new Date(2026, 5, 5, 23, 59))).toBe('2026-06-05');
    expect(getLocalDateString(new Date(2026, 5, 6, 0, 1))).toBe('2026-06-06');
  });
});
