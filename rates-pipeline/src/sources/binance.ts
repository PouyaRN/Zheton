import { RESERVED_ASSET_IDS, parsePositiveNumber } from '../types.ts';

export function parseBinanceVisionTickers(data: unknown): Record<string, number> {
  if (!Array.isArray(data)) throw new Error('Invalid Binance ticker payload');
  const usd: Record<string, number> = { usdt: 1 };
  for (const item of data) {
    if (!item || typeof item !== 'object') continue;
    const rec = item as Record<string, unknown>;
    const symbol = String(rec.symbol ?? '');
    if (!symbol.endsWith('USDT')) continue;
    const id = symbol.slice(0, -4).toLowerCase();
    if (!id || RESERVED_ASSET_IDS.has(id)) continue;
    const price = parsePositiveNumber(rec.price);
    if (price != null) usd[id] = price;
  }
  if (Object.keys(usd).length <= 1) throw new Error('No USDT pairs found in Binance payload');
  return usd;
}

export async function fetchBinanceVisionUsd(timeoutMs = 10_000): Promise<Record<string, number>> {
  const res = await fetch('https://data-api.binance.vision/api/v3/ticker/price', {
    headers: { accept: 'application/json' },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`Binance Vision HTTP ${res.status}`);
  return parseBinanceVisionTickers(await res.json());
}

