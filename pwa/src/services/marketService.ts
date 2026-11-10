import { AssetKind, MarketQuote } from '../types';

export interface CatalogInstrument {
  id: string;
  fa: string;
  en: string;
  kind: AssetKind;
  unitFa: string;
  coinWeightGram?: number;
  karat?: number;
}

export const MARKET_INSTRUMENT_CATALOG: CatalogInstrument[] = [
  { id: 'gold18', fa: 'طلای ۱۸ عیار', en: '18K Gold Gram', kind: 'GOLD', unitFa: 'گرم' },
  { id: 'gold_mesghal', fa: 'مثقال طلا', en: 'Mesghal Gold', kind: 'GOLD', unitFa: 'مثقال' },
  { id: 'silver_999', fa: 'نقره ۹۹۹', en: 'Silver 999 Gram', kind: 'SILVER', unitFa: 'گرم' },
  {
    id: 'coin_emami',
    fa: 'سکه امامی',
    en: 'Emami Gold Coin',
    kind: 'COIN',
    unitFa: 'عدد',
    coinWeightGram: 8.133,
    karat: 21.6,
  },
  {
    id: 'coin_bahar',
    fa: 'سکه بهار آزادی',
    en: 'Bahar Azadi Coin',
    kind: 'COIN',
    unitFa: 'عدد',
    coinWeightGram: 8.133,
    karat: 21.6,
  },
  {
    id: 'coin_nim',
    fa: 'نیم‌سکه',
    en: 'Half Gold Coin',
    kind: 'COIN',
    unitFa: 'عدد',
    coinWeightGram: 4.0665,
    karat: 21.6,
  },
  {
    id: 'coin_rob',
    fa: 'ربع‌سکه',
    en: 'Quarter Gold Coin',
    kind: 'COIN',
    unitFa: 'عدد',
    coinWeightGram: 2.03225,
    karat: 21.6,
  },
  {
    id: 'coin_gerami',
    fa: 'سکه گرمی',
    en: 'Gram Gold Coin',
    kind: 'COIN',
    unitFa: 'عدد',
    coinWeightGram: 1.01,
    karat: 21.6,
  },
  { id: 'usd', fa: 'دلار آمریکا', en: 'US Dollar (USD)', kind: 'FIAT', unitFa: 'دلار' },
  { id: 'eur', fa: 'یورو اروپا', en: 'Euro (EUR)', kind: 'FIAT', unitFa: 'یورو' },
  { id: 'gbp', fa: 'پوند انگلیس', en: 'British Pound (GBP)', kind: 'FIAT', unitFa: 'پوند' },
  { id: 'aed', fa: 'درهم امارات', en: 'UAE Dirham (AED)', kind: 'FIAT', unitFa: 'درهم' },
  { id: 'try', fa: 'لیر ترکیه', en: 'Turkish Lira (TRY)', kind: 'FIAT', unitFa: 'لیر' },
  { id: 'usdt', fa: 'تتر (دلار دیجیتال)', en: 'Tether (USDT)', kind: 'CRYPTO', unitFa: 'USDT' },
  { id: 'btc', fa: 'بیت‌کوین', en: 'Bitcoin (BTC)', kind: 'CRYPTO', unitFa: 'BTC' },
  { id: 'eth', fa: 'اتریوم', en: 'Ethereum (ETH)', kind: 'CRYPTO', unitFa: 'ETH' },
  { id: 'ton', fa: 'تون‌کوین', en: 'Toncoin (TON)', kind: 'CRYPTO', unitFa: 'TON' },
  { id: 'etf_ayar', fa: 'صندوق طلای عیار', en: 'Ayar Gold ETF', kind: 'STOCK', unitFa: 'واحد' },
  { id: 'etf_kahroba', fa: 'صندوق طلای کهربا', en: 'Kahroba Gold ETF', kind: 'STOCK', unitFa: 'واحد' },
  { id: 'etf_afra', fa: 'صندوق درآمد ثابت افرا', en: 'Afra Fixed Income ETF', kind: 'STOCK', unitFa: 'واحد' },
];

export const UNPRICED_MARKET_QUOTES: MarketQuote[] = MARKET_INSTRUMENT_CATALOG.map((item) => ({
  ...item,
  priceToman: 0,
  change24hPct: 0,
  sparkline: [0, 0, 0, 0, 0, 0, 0],
}));

export const RATES_PUBLIC_KEYS: Record<string, string> = {
  k1: `-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETeIQBB0xyE5BXFEOn8lysuSUfW5w
VRbI2TWbIDFYp1G38SS6aYVjhAIX4gmcuYwG+Nx+5LpgwPi2r0hyzxqq0w==
-----END PUBLIC KEY-----`,
};

export const VERIFIED_RATES_CACHE_KEY = 'zheton:verified-rates:v1';

export type RatesFreshness = 'fresh' | 'stale' | 'expired';

export interface SignedRatesPayload {
  schema?: number;
  kid?: string;
  updatedAt: number;
  generatedAt?: number;
  toman: Record<string, number>;
  usd?: Record<string, number>;
  change24h?: Record<string, number>;
  sparklines?: Record<string, number[]>;
  stale?: Record<string, boolean>;
}

export interface VerifiedMarketState {
  quotes: MarketQuote[];
  updatedAtMs: number;
  freshness: RatesFreshness;
  verified: boolean;
  statusReason: 'ok' | 'cached' | 'tampered' | 'replay' | 'unavailable';
}

function base64ToUint8Array(b64: string): Uint8Array {
  const clean = b64.replace(/-----BEGIN PUBLIC KEY-----/g, '')
    .replace(/-----END PUBLIC KEY-----/g, '')
    .replace(/\s+/g, '');
  if (typeof atob === 'function') {
    const bin = atob(clean);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) {
      out[i] = bin.charCodeAt(i);
    }
    return out;
  }
  return new Uint8Array(Buffer.from(clean, 'base64'));
}

export function normalizeTimestampMs(ts: number): number {
  if (!Number.isFinite(ts) || ts <= 0) return 0;
  return ts < 10_000_000_000 ? ts * 1000 : ts;
}

export function evaluateRatesFreshness(updatedAt: number, nowMs = Date.now()): RatesFreshness {
  const updatedMs = normalizeTimestampMs(updatedAt);
  if (updatedMs <= 0) return 'expired';
  const ageMs = nowMs - updatedMs;
  if (ageMs > 7 * 24 * 3600 * 1000) return 'expired';
  if (ageMs > 48 * 3600 * 1000) return 'stale';
  return 'fresh';
}

export function isRatesReplaySafe(incomingUpdatedAt: number, cachedUpdatedAt: number): boolean {
  const incMs = normalizeTimestampMs(incomingUpdatedAt);
  const curMs = normalizeTimestampMs(cachedUpdatedAt);
  if (incMs <= 0) return false;
  if (curMs <= 0) return true;
  return incMs >= curMs;
}

export async function verifyRatesSignature(
  payloadBytes: Uint8Array,
  signatureBase64: string,
  kid = 'k1',
  keyRing: Record<string, string> = RATES_PUBLIC_KEYS,
): Promise<boolean> {
  try {
    const pem = keyRing[kid];
    if (!pem) return false;
    const sigBytes = base64ToUint8Array(signatureBase64.trim());
    if (sigBytes.byteLength !== 64) return false;
    const spkiBytes = base64ToUint8Array(pem);

    const subtle = globalThis.crypto?.subtle;
    if (!subtle) return false;

    const cryptoKey = await subtle.importKey(
      'spki',
      spkiBytes.buffer.slice(spkiBytes.byteOffset, spkiBytes.byteOffset + spkiBytes.byteLength) as ArrayBuffer,
      { name: 'ECDSA', namedCurve: 'P-256' },
      false,
      ['verify'],
    );

    return await subtle.verify(
      { name: 'ECDSA', hash: 'SHA-256' },
      cryptoKey,
      sigBytes.buffer.slice(sigBytes.byteOffset, sigBytes.byteOffset + sigBytes.byteLength) as ArrayBuffer,
      payloadBytes.buffer.slice(payloadBytes.byteOffset, payloadBytes.byteOffset + payloadBytes.byteLength) as ArrayBuffer,
    );
  } catch {
    return false;
  }
}

export function mapPayloadToMarketQuotes(
  payload: SignedRatesPayload,
  nowMs = Date.now(),
): { quotes: MarketQuote[]; freshness: RatesFreshness; updatedAtMs: number } {
  const updatedAtMs = normalizeTimestampMs(payload.updatedAt);
  const freshness = evaluateRatesFreshness(updatedAtMs, nowMs);

  if (freshness === 'expired') {
    return {
      quotes: UNPRICED_MARKET_QUOTES,
      freshness: 'expired',
      updatedAtMs,
    };
  }

  const toman = payload.toman ?? {};
  const change24h = payload.change24h ?? {};
  const sparklines = payload.sparklines ?? {};

  const quotes: MarketQuote[] = MARKET_INSTRUMENT_CATALOG.map((item) => {
    const rawPrice = toman[item.id];
    const priceToman =
      typeof rawPrice === 'number' && Number.isFinite(rawPrice) && rawPrice > 0
        ? Math.round(rawPrice)
        : 0;
    const rawCh = change24h[item.id];
    const change24hPct =
      typeof rawCh === 'number' && Number.isFinite(rawCh) ? Number(rawCh.toFixed(2)) : 0;
    const rawSpark = sparklines[item.id];
    const sparkline =
      Array.isArray(rawSpark) && rawSpark.length === 7 && rawSpark.every((n) => typeof n === 'number' && n > 0)
        ? rawSpark
        : priceToman > 0
          ? [priceToman, priceToman, priceToman, priceToman, priceToman, priceToman, priceToman]
          : [0, 0, 0, 0, 0, 0, 0];

    return {
      ...item,
      priceToman,
      change24hPct,
      sparkline,
    };
  });

  return { quotes, freshness, updatedAtMs };
}

export function loadCachedVerifiedPayload(): SignedRatesPayload | null {
  try {
    if (typeof localStorage === 'undefined' || typeof localStorage.getItem !== 'function') return null;
    const raw = localStorage.getItem(VERIFIED_RATES_CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as SignedRatesPayload;
    if (!parsed || typeof parsed !== 'object' || !parsed.toman || typeof parsed.updatedAt !== 'number') {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function saveCachedVerifiedPayload(payload: SignedRatesPayload): void {
  try {
    if (typeof localStorage === 'undefined' || typeof localStorage.setItem !== 'function') return;
    localStorage.setItem(VERIFIED_RATES_CACHE_KEY, JSON.stringify(payload));
  } catch {
    // ignore storage errors
  }
}

export function getInitialMarketState(nowMs = Date.now()): VerifiedMarketState {
  const cached = loadCachedVerifiedPayload();
  if (cached) {
    const mapped = mapPayloadToMarketQuotes(cached, nowMs);
    return {
      quotes: mapped.quotes,
      updatedAtMs: mapped.updatedAtMs,
      freshness: mapped.freshness,
      verified: true,
      statusReason: 'cached',
    };
  }
  return {
    quotes: UNPRICED_MARKET_QUOTES,
    updatedAtMs: 0,
    freshness: 'expired',
    verified: false,
    statusReason: 'unavailable',
  };
}

export async function fetchVerifiedMarketState(options?: {
  urls?: string[];
  nowMs?: number;
}): Promise<VerifiedMarketState> {
  const nowMs = options?.nowMs ?? Date.now();
  const cached = loadCachedVerifiedPayload();
  const candidateUrls = options?.urls ?? ['./rates.json', '/Zheton/rates.json', '/rates.json'];

  let sawTamper = false;
  let sawReplay = false;

  for (const url of candidateUrls) {
    try {
      const sigUrl = `${url}.sig`;
      const [ratesRes, sigRes] = await Promise.all([
        fetch(url, { headers: { accept: 'application/json' }, cache: 'no-store' }),
        fetch(sigUrl, { headers: { accept: 'text/plain' }, cache: 'no-store' }),
      ]);

      if (!ratesRes.ok || !sigRes.ok) continue;

      const bytes = new Uint8Array(await ratesRes.arrayBuffer());
      const sigB64 = (await sigRes.text()).trim();
      const text = new TextDecoder('utf-8').decode(bytes);
      const parsed = JSON.parse(text) as SignedRatesPayload;

      if (!parsed || typeof parsed !== 'object' || !parsed.toman || typeof parsed.updatedAt !== 'number') {
        sawTamper = true;
        continue;
      }

      const kid = typeof parsed.kid === 'string' ? parsed.kid : 'k1';
      const sigOk = await verifyRatesSignature(bytes, sigB64, kid);
      if (!sigOk) {
        sawTamper = true;
        continue;
      }

      if (cached && !isRatesReplaySafe(parsed.updatedAt, cached.updatedAt)) {
        sawReplay = true;
        continue;
      }

      saveCachedVerifiedPayload(parsed);
      const mapped = mapPayloadToMarketQuotes(parsed, nowMs);
      return {
        quotes: mapped.quotes,
        updatedAtMs: mapped.updatedAtMs,
        freshness: mapped.freshness,
        verified: true,
        statusReason: 'ok',
      };
    } catch {
      continue;
    }
  }

  if (cached) {
    const mapped = mapPayloadToMarketQuotes(cached, nowMs);
    return {
      quotes: mapped.quotes,
      updatedAtMs: mapped.updatedAtMs,
      freshness: mapped.freshness,
      verified: true,
      statusReason: sawTamper ? 'tampered' : sawReplay ? 'replay' : 'cached',
    };
  }

  return {
    quotes: UNPRICED_MARKET_QUOTES,
    updatedAtMs: 0,
    freshness: 'expired',
    verified: false,
    statusReason: sawTamper ? 'tampered' : sawReplay ? 'replay' : 'unavailable',
  };
}
