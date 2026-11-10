import { ParsedTomanCrypto, parsePositiveNumber } from '../types.ts';

/**
 * Pure parser for Nobitex `/v3/orderbook/USDTIRT`.
 * Nobitex quotes IRT in Rial, so `lastTradePrice` is divided by 10 to get Toman.
 */
export function parseNobitexUsdt(data: unknown): ParsedTomanCrypto {
  if (data == null || typeof data !== 'object' || Array.isArray(data)) {
    throw new Error('Invalid Nobitex payload');
  }
  const rec = data as Record<string, unknown>;
  const rial = parsePositiveNumber(rec.lastTradePrice);
  if (rial == null) {
    throw new Error('Nobitex payload missing lastTradePrice');
  }
  const usdtToman = Math.round((rial / 10) * 100) / 100;
  return {
    prices: { usdt: usdtToman },
    namesFa: { usdt: 'تتر' },
    change24h: {},
  };
}

export async function fetchNobitexUsdt(timeoutMs = 10_000): Promise<ParsedTomanCrypto> {
  const res = await fetch('https://apiv2.nobitex.ir/v3/orderbook/USDTIRT', {
    headers: { accept: 'application/json' },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`Nobitex HTTP ${res.status}`);
  return parseNobitexUsdt(await res.json());
}

