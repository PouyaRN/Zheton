/**
 * The consumer price index the inflation tools share.
 *
 * Source: Central Bank of Iran (بانک مرکزی جمهوری اسلامی ایران) and the Statistical Centre of
 * Iran (مرکز آمار ایران), annual-average consumer price inflation 1380–1404, rebased onto the
 * 1400 = 100 base the Central Bank adopted in 1403. The published annual rates were chained and
 * rounded to two decimals; treat every figure here as an estimate, not as the legal index a
 * court would read.
 *
 * 1404 is a full-year estimate and 1405 assumes 45% for the year (the app was built during
 * 1405), so both are marked `estimated` and the UI says so. When the official numbers arrive,
 * only this table changes — every formula takes the index as an argument.
 */

export interface CpiPoint {
  readonly year: number;
  readonly index: number;
  /** True when the year is an estimate/projection rather than a settled annual average. */
  readonly estimated?: boolean;
}

export const CPI_BASE_YEAR = 1400;
export const CPI_FIRST_YEAR = 1380;
export const CPI_LAST_YEAR = 1405;

export const CPI: readonly CpiPoint[] = Object.freeze([
  { year: 1380, index: 2.36 },
  { year: 1381, index: 2.72 },
  { year: 1382, index: 3.14 },
  { year: 1383, index: 3.62 },
  { year: 1384, index: 4.1 },
  { year: 1385, index: 4.62 },
  { year: 1386, index: 5.47 },
  { year: 1387, index: 6.86 },
  { year: 1388, index: 7.6 },
  { year: 1389, index: 8.55 },
  { year: 1390, index: 10.38 },
  { year: 1391, index: 13.55 },
  { year: 1392, index: 18.25 },
  { year: 1393, index: 21.1 },
  { year: 1394, index: 23.61 },
  { year: 1395, index: 25.78 },
  { year: 1396, index: 28.34 },
  { year: 1397, index: 37.01 },
  { year: 1398, index: 52.25 },
  { year: 1399, index: 71.33 },
  { year: 1400, index: 100 },
  { year: 1401, index: 147.5 },
  { year: 1402, index: 207.53 },
  { year: 1403, index: 280.17 },
  { year: 1404, index: 406.24, estimated: true },
  { year: 1405, index: 589.06, estimated: true },
]);

const BY_YEAR = new Map<number, CpiPoint>(CPI.map((point) => [point.year, point]));

/** The index for a Persian year, or null when that year is outside the table. */
export function cpiIndex(year: number): number | null {
  if (!Number.isInteger(year)) return null;
  const point = BY_YEAR.get(year);
  return point == null ? null : point.index;
}

/** The table's years, ascending — what the year pickers are built from. */
export function cpiYears(): number[] {
  return CPI.map((point) => point.year);
}

/** Whether the table's value for this year is an estimate/projection. */
export function isEstimatedYear(year: number): boolean {
  return BY_YEAR.get(year)?.estimated === true;
}

/**
 * Cumulative inflation between two years, as a fraction (0.4 = 40%).
 * Assumes both years exist in the table and the index moves monotonically in the table; a
 * missing year returns null rather than an extrapolated guess.
 */
export function cpiRatio(fromYear: number, toYear: number): number | null {
  const from = cpiIndex(fromYear);
  const to = cpiIndex(toYear);
  if (from == null || to == null || from <= 0 || to <= 0) return null;
  return to / from;
}
