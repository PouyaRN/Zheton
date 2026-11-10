import { type ParsedFiatGold, parseFiniteNumber, parsePositiveNumber } from '../types.ts';

/**
 * app asset id -> tgju indicator key in `call1.tgju.org/ajax.json` (`current` object).
 * TGJU quotes in Rial, so every price is divided by 10 to convert to Toman.
 */
export const TGJU_AJAX_MAP: Record<string, string> = {
  usd: 'price_dollar_rl',
  eur: 'price_eur',
  gbp: 'price_gbp',
  nok: 'price_nok',
  try: 'price_try',
  aed: 'price_aed',
  cad: 'price_cad',
  gold18: 'geram18',
  gold_mesghal: 'mesghal',
  silver_999: 'silver_999',
  silver_925: 'silver_925',
  coin_emami: 'sekee',
  coin_bahar: 'sekeb',
  coin_nim: 'nim',
  coin_rob: 'rob',
  coin_gerami: 'gerami',
};

const PARSIAN_SOOT = [
  100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1300, 1400, 1500,
];

/**
 * Pure parser for `call1.tgju.org/ajax.json` payload.
 */
export function parseTgjuAjax(data: unknown): ParsedFiatGold {
  if (data == null || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error('Invalid TGJU payload');
  }
  const root = data as Record<string, unknown>;
  const current = (root.current && typeof root.current === 'object' ? root.current : root) as Record<
    string,
    unknown
  >;

  const toman: Record<string, number> = {};
  const change24h: Record<string, number> = {};

  for (const [assetId, tgjuKey] of Object.entries(TGJU_AJAX_MAP)) {
    const entry = current[tgjuKey];
    if (entry != null && typeof entry === 'object' && !Array.isArray(entry)) {
      const rec = entry as Record<string, unknown>;
      const rial = parsePositiveNumber(rec.p);
      if (rial != null) {
        toman[assetId] = Math.round((rial / 10) * 100) / 100;
      }
      const dp = parseFiniteNumber(rec.dp);
      if (dp != null) {
        const dt = String(rec.dt ?? '').toLowerCase();
        const signed = dt === 'low' && dp > 0 ? -dp : dp;
        change24h[assetId] = Math.round(signed * 100) / 100;
      }
    }
  }

  // Derive silver_925 from silver_999 if silver_925 is missing
  if (toman.silver_999 != null && toman.silver_925 == null) {
    toman.silver_925 = Math.round(toman.silver_999 * 0.925);
  }

  // Derive Parsian gold coin rates (18k gold weight in soot + typical minting premium curve)
  if (toman.gold18 != null) {
    const perSootBase = toman.gold18 / 1000;
    toman.parsian = Math.round(perSootBase * 1.05 * 100) / 100;
    for (const soot of PARSIAN_SOOT) {
      const premium = soot <= 200 ? 1.18 : soot <= 500 ? 1.09 : 1.05;
      toman[`parsian_${soot}`] = Math.round(perSootBase * soot * premium);
    }
  }

  if (Object.keys(toman).length === 0) {
    throw new Error('TGJU payload contained no usable prices');
  }

  return { toman, change24h };
}

export async function fetchTgjuAjax(timeoutMs = 10_000): Promise<ParsedFiatGold> {
  const res = await fetch('https://call1.tgju.org/ajax.json', {
    headers: {
      'user-agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
      accept: 'application/json',
    },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`TGJU HTTP ${res.status}`);
  return parseTgjuAjax(await res.json());
}

