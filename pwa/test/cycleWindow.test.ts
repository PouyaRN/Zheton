import { describe, expect, it } from 'vitest';
import { getCurrentCycleWindow, jalaliMonthLength, jalaliToEpochMs } from '../src/utils/cycleWindow';

describe('Financial Cycle Window (getCurrentCycleWindow)', () => {
  it('uses calendar month boundaries when anchorDay = 1', () => {
    // 15 Aban 1405
    const now = jalaliToEpochMs(1405, 8, 15);
    const win = getCurrentCycleWindow(now, 1);
    expect(win.startMs).toBe(jalaliToEpochMs(1405, 8, 1));
    expect(win.endMs).toBe(jalaliToEpochMs(1405, 9, 1));
    expect(win.cycleDay).toBe(15);
    expect(win.totalCycleDays).toBe(30);
    expect(win.remainingDays).toBe(15);
  });

  it('wraps into previous month when current Jalali day < anchorDay', () => {
    // 10 Azar 1405 with anchorDay = 25 -> cycle started 25 Aban 1405
    const now = jalaliToEpochMs(1405, 9, 10);
    const win = getCurrentCycleWindow(now, 25);
    expect(win.startMs).toBe(jalaliToEpochMs(1405, 8, 25));
    expect(win.endMs).toBe(jalaliToEpochMs(1405, 9, 25));
    expect(win.totalCycleDays).toBe(30);
    expect(win.cycleDay).toBe(16);
  });

  it('clamps anchorDay = 31 to 29 in non-leap Esfand and 30 in 30-day months', () => {
    // 1404 is not a leap year (1404 % 33 = 18 -> Esfand has 29 days)
    expect(jalaliMonthLength(1404, 12)).toBe(29);
    expect(jalaliMonthLength(1404, 7)).toBe(30);
    expect(jalaliMonthLength(1404, 1)).toBe(31);

    const esfand20 = jalaliToEpochMs(1404, 12, 20);
    const win = getCurrentCycleWindow(esfand20, 31);
    // Because 20 < 29 (clamped Esfand anchor), starts 30 Bahman -> ends 29 Esfand
    expect(win.startMs).toBe(jalaliToEpochMs(1404, 11, 30));
    expect(win.endMs).toBe(jalaliToEpochMs(1404, 12, 29));
  });
});
