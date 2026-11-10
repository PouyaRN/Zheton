import { ParsedFiatGold, parsePositiveNumber } from '../types.ts';

export const BONBAST_MAP: Record<string, string> = {
  usd: 'usd1',
  eur: 'eur1',
  gbp: 'gbp1',
  nok: 'nok1',
  try: 'try1',
  aed: 'aed1',
  cad: 'cad1',
  gold18: 'gol18',
  gold_mesghal: 'mithqal',
  coin_emami: 'emami1',
  coin_bahar: 'azadi1',
  coin_nim: 'azadi1_2',
  coin_rob: 'azadi1_4',
  coin_gerami: 'azadi1g',
};

export function parseBonbastJson(data: unknown): ParsedFiatGold {
  if (data == null || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error('Invalid Bonbast payload');
  }
  const rec = data as Record<string, unknown>;
  const toman: Record<string, number> = {};
  for (const [assetId, field] of Object.entries(BONBAST_MAP)) {
    const val = parsePositiveNumber(rec[field]);
    if (val != null) toman[assetId] = val;
  }
  if (Object.keys(toman).length === 0) {
    throw new Error('Bonbast payload contained no usable prices');
  }
  return { toman, change24h: {} };
}

export async function fetchBonbast(timeoutMs = 10_000): Promise<ParsedFiatGold> {
  const ua =
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';
  const home = await fetch('https://www.bonbast.com/', {
    headers: { 'user-agent': ua, 'accept-language': 'en-US,en;q=0.9' },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!home.ok) throw new Error(`Bonbast homepage HTTP ${home.status}`);
  const html = await home.text();
  const token = html.match(/param\s*[:=]\s*["']([^"']+)["']/)?.[1];
  if (!token) throw new Error('Bonbast token not found');
  const setCookie = home.headers.get('set-cookie');

  const res = await fetch('https://www.bonbast.com/json', {
    method: 'POST',
    headers: {
      'user-agent': ua,
      'content-type': 'application/x-www-form-urlencoded; charset=UTF-8',
      'x-requested-with': 'XMLHttpRequest',
      referer: 'https://www.bonbast.com/',
      ...(setCookie ? { cookie: setCookie.split(';')[0] } : {}),
    },
    body: new URLSearchParams({ param: token }).toString(),
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`Bonbast JSON HTTP ${res.status}`);
  return parseBonbastJson(await res.json());
}

