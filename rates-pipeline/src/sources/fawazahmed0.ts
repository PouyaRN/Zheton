import { parsePositiveNumber } from '../types.ts';

const FOREX_KEYS = ['eur', 'gbp', 'aed', 'try', 'cad', 'nok'] as const;

/**
 * Parses Fawazahmed0 Currency API (`usd.json`), which gives `1 USD = X foreign currency`.
 * Given `usdToman` (Toman per 1 USD), computes `1 unit of foreign currency = usdToman / X`.
 */
export function parseFawazForex(data: unknown, usdToman: number): Record<string, number> {
  if (!Number.isFinite(usdToman) || usdToman <= 0) {
    throw new Error('Valid usdToman required for cross-rating');
  }
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error('Invalid Fawazahmed0 payload');
  }
  const usdMap = (data as Record<string, unknown>).usd;
  if (!usdMap || typeof usdMap !== 'object' || Array.isArray(usdMap)) {
    throw new Error('Missing usd rates map');
  }
  const rates = usdMap as Record<string, unknown>;
  const out: Record<string, number> = {};
  for (const key of FOREX_KEYS) {
    const perUsd = parsePositiveNumber(rates[key]);
    if (perUsd != null && perUsd > 0) {
      out[key] = Math.round((usdToman / perUsd) * 100) / 100;
    }
  }
  if (Object.keys(out).length === 0) throw new Error('No forex cross-rates parsed');
  return out;
}

export async function fetchFawazForex(
  usdToman: number,
  timeoutMs = 8_000,
): Promise<Record<string, number>> {
  const res = await fetch(
    'https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json',
    {
      headers: { accept: 'application/json' },
      signal: AbortSignal.timeout(timeoutMs),
    },
  );
  if (!res.ok) throw new Error(`Fawazahmed0 HTTP ${res.status}`);
  return parseFawazForex(await res.json(), usdToman);
}

