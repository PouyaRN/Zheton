/**
 * Reading a figure a person typed, and printing one back in Persian.
 *
 * An input field is not a number: it arrives with Persian or Arabic-Indic digits, the thousands
 * separators people actually use (`٬` in Persian, `,` on a phone keyboard), and an Arabic
 * decimal mark. Parsing lives here, once, so no calculator gets its own opinion about what
 * "۸٫۵" means.
 */

export const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';

/**
 * Persian digits, grouped like the rest of the app (`۱۲,۳۴۵`), with an optional fraction.
 * Non-finite input prints an em dash rather than "NaN": a figure is never a word, and nothing
 * downstream should have to test for one. `toLocaleString` rather than `toFixed` so a number
 * past 1e21 prints its digits instead of switching to exponential notation.
 */
export function fa(value: number, fractionDigits = 0): string {
  if (!Number.isFinite(value)) return '—';
  const digits = Math.min(Math.max(Math.trunc(fractionDigits), 0), 10);
  const sign = value < 0 ? '−' : '';
  const grouped = Math.abs(value).toLocaleString('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
    useGrouping: true,
  });
  return (sign + grouped.replace('.', '٫')).replace(/[0-9]/g, (digit) => FA_DIGITS[Number(digit)]);
}

/** Persian digits without grouping — for a year, where "۱,۴۰۵" would read as an amount. */
export function faYear(year: number): string {
  if (!Number.isFinite(year)) return '—';
  return String(Math.trunc(year)).replace(/[0-9]/g, (digit) => FA_DIGITS[Number(digit)]);
}

/** A Toman figure with its unit. The unit is part of the string so it never wraps alone. */
export function formatToman(value: number, fractionDigits = 0): string {
  return `${fa(value, fractionDigits)} تومان`;
}

/** A percentage with the Persian percent sign. */
export function formatPercent(value: number, fractionDigits = 1): string {
  return `${fa(value, fractionDigits)}٪`;
}

/**
 * Parses a human-entered number.
 *
 * Assumptions:
 * - Persian (۰-۹) and Arabic-Indic (٠-٩) digits are the same digits.
 * - `٬`, `,`, `،` and whitespace are grouping, never decimal marks. `٫` and `.` are decimal.
 * - Scientific notation, currency words and a stray second decimal mark are invalid, not
 *   repaired: a calculator that silently turns "1.2.3" into 123 is worse than one that refuses.
 * - The result must be finite. Overflow is `null`, never `Infinity`.
 */
export function parseNumber(input: string): number | null {
  if (typeof input !== 'string') return null;
  let text = input.trim();
  if (text === '') return null;

  text = text
    .replace(/[۰-۹]/g, (digit) => String(digit.codePointAt(0)! - 0x06f0))
    .replace(/[٠-٩]/g, (digit) => String(digit.codePointAt(0)! - 0x0660))
    .replace(/[٬,،\s]/g, '')
    .replace(/٫/g, '.');

  if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(text)) return null;
  const value = Number(text);
  return Number.isFinite(value) ? value : null;
}

/** A whole count (coins, months, years). A fractional input is invalid rather than rounded. */
export function parseInteger(input: string): number | null {
  const value = parseNumber(input);
  return value != null && Number.isInteger(value) ? value : null;
}
