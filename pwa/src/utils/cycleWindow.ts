import { toAsciiDigits, toFaDigits } from './format';

const DAY_MS = 24 * 3600 * 1000;
const TEHRAN_OFFSET_MS = 3.5 * 3600 * 1000;

export interface CycleWindowInfo {
  startMs: number;
  endMs: number;
  cycleDay: number;
  totalCycleDays: number;
  remainingDays: number;
  labelFa: string;
}

const JALALI_MONTH_NAMES = [
  'فروردین',
  'اردیبهشت',
  'خرداد',
  'تیر',
  'مرداد',
  'شهریور',
  'مهر',
  'آبان',
  'آذر',
  'دی',
  'بهمن',
  'اسفند',
];

function isJalaliLeapYear(jy: number): boolean {
  const mod = jy % 33;
  return mod === 1 || mod === 5 || mod === 9 || mod === 13 || mod === 17 || mod === 22 || mod === 26 || mod === 30;
}

export function jalaliMonthLength(jy: number, jm: number): number {
  if (jm <= 6) return 31;
  if (jm <= 11) return 30;
  return isJalaliLeapYear(jy) ? 30 : 29;
}

function getTehranJalaliParts(epochMs: number): { year: number; month: number; day: number } {
  const formatter = new Intl.DateTimeFormat('en-US-u-ca-persian', {
    timeZone: 'Asia/Tehran',
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
  });
  const parts = formatter.formatToParts(new Date(epochMs));
  let year = 1404;
  let month = 1;
  let day = 1;
  for (const part of parts) {
    if (part.type === 'year') year = Number(toAsciiDigits(part.value));
    if (part.type === 'month') month = Number(toAsciiDigits(part.value));
    if (part.type === 'day') day = Number(toAsciiDigits(part.value));
  }
  return { year, month, day };
}

/**
 * Converts a Jalali year/month/day at Asia/Tehran midnight (`00:00:00+03:30`) to epoch ms.
 */
export function jalaliToEpochMs(jy: number, jm: number, jd: number): number {
  const jY = jy - 979;
  let jDayNo =
    365 * jY +
    Math.floor(jY / 33) * 8 +
    Math.floor(((jY % 33) + 3) / 4);
  for (let i = 0; i < jm - 1; ++i) {
    jDayNo += i < 6 ? 31 : 30;
  }
  jDayNo += jd - 1;
  return (jDayNo - 135061) * DAY_MS - TEHRAN_OFFSET_MS;
}

/**
 * Computes the half-open `[startMs, endMs)` window for the active financial cycle,
 * anchored to `anchorDay` (clamped to real Jalali month lengths, e.g. 30 in Esfand -> 29).
 */
export function getCurrentCycleWindow(nowMs = Date.now(), anchorDay = 1): CycleWindowInfo {
  const safeAnchor = Math.min(31, Math.max(1, Math.round(anchorDay)));
  const jNow = getTehranJalaliParts(nowMs);

  let startYear = jNow.year;
  let startMonth = jNow.month;
  let thisMonthAnchor = Math.min(safeAnchor, jalaliMonthLength(jNow.year, jNow.month));

  if (jNow.day < thisMonthAnchor) {
    startMonth -= 1;
    if (startMonth < 1) {
      startMonth = 12;
      startYear -= 1;
    }
    thisMonthAnchor = Math.min(safeAnchor, jalaliMonthLength(startYear, startMonth));
  }

  let endMonth = startMonth + 1;
  let endYear = startYear;
  if (endMonth > 12) {
    endMonth = 1;
    endYear += 1;
  }
  const endDay = Math.min(safeAnchor, jalaliMonthLength(endYear, endMonth));

  const startMs = jalaliToEpochMs(startYear, startMonth, thisMonthAnchor);
  const endMs = jalaliToEpochMs(endYear, endMonth, endDay);
  const todayMidnightMs = jalaliToEpochMs(jNow.year, jNow.month, jNow.day);

  const totalCycleDays = Math.max(1, Math.round((endMs - startMs) / DAY_MS));
  const cycleDay = Math.min(totalCycleDays, Math.max(1, Math.round((todayMidnightMs - startMs) / DAY_MS) + 1));
  const remainingDays = Math.max(0, totalCycleDays - cycleDay);

  const labelFa =
    safeAnchor <= 1
      ? `${JALALI_MONTH_NAMES[startMonth - 1]} ${toFaDigits(startYear)}`
      : `از ${toFaDigits(thisMonthAnchor)} ${JALALI_MONTH_NAMES[startMonth - 1]}`;

  return {
    startMs,
    endMs,
    cycleDay,
    totalCycleDays,
    remainingDays,
    labelFa,
  };
}
