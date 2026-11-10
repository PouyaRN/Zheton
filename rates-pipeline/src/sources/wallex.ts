import { ParsedTomanCrypto, RESERVED_ASSET_IDS, parseFiniteNumber, parsePositiveNumber } from '../types.ts';

/**
 * Pure parser for Wallex `/v1/markets` response.
 * Extracts `*TMN` markets (already quoted in Toman), Persian asset names, and 24h changes.
 */
export function parseWallexMarkets(data: unknown): ParsedTomanCrypto {
  if (data == null || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error('Invalid Wallex payload');
  }
  const root = data as Record<string, unknown>;
  const result = root.result as Record<string, unknown> | undefined;
  const symbols = (result?.symbols ?? root.symbols) as Record<string, unknown> | undefined;
  if (!symbols || typeof symbols !== 'object' || Array.isArray(symbols)) {
    throw new Error('Wallex payload missing symbols object');
  }

  const prices: Record<string, number> = {};
  const namesFa: Record<string, string> = {};
  const change24h: Record<string, number> = {};

  for (const [marketKey, rawMarket] of Object.entries(symbols)) {
    if (!marketKey.endsWith('TMN')) continue;
    if (rawMarket == null || typeof rawMarket !== 'object') continue;
    const market = rawMarket as Record<string, unknown>;
    const baseAsset = String(market.baseAsset ?? marketKey.slice(0, -3)).toLowerCase();
    if (!baseAsset || RESERVED_ASSET_IDS.has(baseAsset)) continue;

    const stats = (market.stats ?? {}) as Record<string, unknown>;
    const lastPrice = parsePositiveNumber(stats.lastPrice ?? market.price);
    if (lastPrice != null) {
      prices[baseAsset] = Math.round(lastPrice * 100) / 100;
    }

    const ch24 = parseFiniteNumber(stats['24h_ch']);
    if (ch24 != null) {
      change24h[baseAsset] = Math.round(ch24 * 100) / 100;
    }

    const faBase = String(market.faBaseAsset ?? '').trim();
    if (faBase && /[\u0600-\u06FF]/.test(faBase)) {
      namesFa[baseAsset] = faBase;
    }
  }

  if (Object.keys(prices).length === 0) {
    throw new Error('No Wallex TMN prices parsed');
  }

  return { prices, namesFa, change24h };
}

export async function fetchWallexMarkets(timeoutMs = 10_000): Promise<ParsedTomanCrypto> {
  const res = await fetch('https://api.wallex.ir/v1/markets', {
    headers: { accept: 'application/json' },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`Wallex HTTP ${res.status}`);
  return parseWallexMarkets(await res.json());
}
