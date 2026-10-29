/**
 * Formatting utilities for ChandToman / Andis
 */

const FA_DIGITS = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];

export function toFaDigits(input: string | number): string {
  const str = String(input);
  return str.replace(/[0-9]/g, (d) => FA_DIGITS[Number(d)] ?? d);
}

export function toAsciiDigits(str: string): string {
  return str.replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 1776))
            .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 1632));
}

export function formatToman(amount: number, privacyMode = false): string {
  if (privacyMode) {
    return '••••••';
  }
  const rounded = Math.round(amount);
  const formatted = new Intl.NumberFormat('en-US').format(rounded);
  return toFaDigits(formatted);
}

export function formatNumberWithDecimals(val: number, decimals = 2, privacyMode = false): string {
  if (privacyMode) return '•••';
  const str = Number(val).toLocaleString('en-US', {
    minimumFractionDigits: 0,
    maximumFractionDigits: decimals,
  });
  return toFaDigits(str);
}

export function formatPercent(pct: number): string {
  const sign = pct > 0 ? '+' : '';
  const val = pct.toFixed(1);
  return `${sign}${toFaDigits(val)}٪`;
}

export function formatCardPan(raw: string, maskMiddle = false, privacyMode = false): string {
  if (privacyMode) return '••••  ••••  ••••  ••••';
  const clean = toAsciiDigits(raw).replace(/\D/g, '');
  if (maskMiddle && clean.length === 16) {
    const first4 = clean.slice(0, 4);
    const last4 = clean.slice(12, 16);
    return `${toFaDigits(first4)}  ••••  ••••  ${toFaDigits(last4)}`;
  }
  if (clean.length === 16) {
    const parts = [
      clean.slice(0, 4),
      clean.slice(4, 8),
      clean.slice(8, 12),
      clean.slice(12, 16),
    ];
    return parts.map(toFaDigits).join('  ');
  }
  return toFaDigits(raw);
}

export function formatSheba(raw: string): string {
  const clean = toAsciiDigits(raw).replace(/[^a-zA-Z0-9]/g, '').toUpperCase();
  if (clean.startsWith('IR') && clean.length === 26) {
    // Group: IR + 2 digits + 4 digits ...
    const chunks = [
      clean.slice(0, 4),
      clean.slice(4, 8),
      clean.slice(8, 12),
      clean.slice(12, 16),
      clean.slice(16, 20),
      clean.slice(20, 24),
      clean.slice(24, 26),
    ];
    return chunks.join(' - ');
  }
  return clean;
}

// Simple Persian Jalali calendar conversion
export function getJalaliDate(date = new Date()): { year: number; month: number; day: number; formatted: string } {
  // Using Intl with fa-IR-u-ca-persian
  try {
    const formatter = new Intl.DateTimeFormat('fa-IR-u-ca-persian', {
      year: 'numeric',
      month: 'numeric',
      day: 'numeric',
    });
    const parts = formatter.formatToParts(date);
    let year = 1403;
    let month = 1;
    let day = 1;
    for (const part of parts) {
      if (part.type === 'year') year = Number(toAsciiDigits(part.value));
      if (part.type === 'month') month = Number(toAsciiDigits(part.value));
      if (part.type === 'day') day = Number(toAsciiDigits(part.value));
    }
    const monthNames = [
      'فروردین', 'اردیبهشت', 'خرداد', 'تیر', 'مرداد', 'شهریور',
      'مهر', 'آبان', 'آذر', 'دی', 'بهمن', 'اسفند'
    ];
    const formatted = `${toFaDigits(day)} ${monthNames[month - 1]} ${toFaDigits(year)}`;
    return { year, month, day, formatted };
  } catch {
    return { year: 1403, month: 8, day: 7, formatted: '۷ آبان ۱۴۰۳' };
  }
}

export function formatRelativeTime(timestamp: number): string {
  const now = Date.now();
  const diffSec = Math.floor((now - timestamp) / 1000);
  if (diffSec < 60) return 'همین الان';
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin < 60) return `${toFaDigits(diffMin)} دقیقه پیش`;
  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${toFaDigits(diffHour)} ساعت پیش`;
  const diffDay = Math.floor(diffHour / 24);
  if (diffDay === 1) return 'دیروز';
  if (diffDay < 30) return `${toFaDigits(diffDay)} روز پیش`;
  const diffMonth = Math.floor(diffDay / 30);
  return `${toFaDigits(diffMonth)} ماه پیش`;
}
