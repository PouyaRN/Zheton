/**
 * Live rates, read through the sync Worker's same-origin `/rates` proxy.
 *
 * The PWA is served by `muchtoman-sync` (`sync/src/index.ts`), which forwards `/rates` to the
 * rates Worker. So the page asks its own origin, no CORS, and the rate data never needs a
 * second hostname. The shape is the Worker's:
 *
 *   { updatedAt: number, toman: { gold18: number, coin_emami: number, ... } }
 *
 * All values are Toman. Names, coins and sources are ignored: the calculators only need the
 * handful of keys in `CoinPreset.rateKey` and `gold18`.
 *
 * Offline behaviour: the last successful snapshot is kept in `localStorage`, and every tool
 * also accepts a manual price, so a calculator still works when `/rates` is down or the device
 * is offline. Nothing here is sent anywhere and there is no analytics.
 */

export interface RateSnapshot {
  updatedAt: number;
  toman: Record<string, number>;
}

/** The rate key for one gram of 18-karat gold — what the bubble tool asks for. */
export const GOLD_18_RATE_KEY = 'gold18';

const CACHE_KEY = 'rates:last-good:v1';

/**
 * Validates the Worker payload and keeps only usable prices.
 *
 * Assumptions: a usable price is a finite number greater than zero; strings are refused rather
 * than coerced, because a rate is published as JSON numbers; a payload with no usable price is
 * not a snapshot (`null`), so a stale but real cache is never overwritten by an empty answer.
 */
export function parseRatesPayload(payload: unknown): RateSnapshot | null {
  if (payload == null || typeof payload !== 'object' || Array.isArray(payload)) return null;
  const record = payload as Record<string, unknown>;
  const rawToman = record.toman;
  if (rawToman == null || typeof rawToman !== 'object' || Array.isArray(rawToman)) return null;

  const toman: Record<string, number> = {};
  for (const [key, value] of Object.entries(rawToman)) {
    if (typeof value === 'number' && Number.isFinite(value) && value > 0) toman[key] = value;
  }
  if (Object.keys(toman).length === 0) return null;

  return {
    updatedAt:
      typeof record.updatedAt === 'number' && Number.isFinite(record.updatedAt)
        ? record.updatedAt
        : 0,
    toman,
  };
}

/** One rate in Toman, or null when it is missing or not a usable price. */
export function rateToman(rates: RateSnapshot | null, key: string): number | null {
  const value = rates?.toman[key];
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : null;
}

function storage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage;
  } catch {
    // Access can throw in a locked-down browser; a calculator without a cache still works.
    return null;
  }
}

/** The last successful snapshot, if any. Synchronous so a page can paint rates immediately. */
export function cachedRates(): RateSnapshot | null {
  const store = storage();
  if (store == null) return null;
  try {
    return parseRatesPayload(JSON.parse(store.getItem(CACHE_KEY) ?? 'null'));
  } catch {
    return null;
  }
}

function rememberRates(rates: RateSnapshot): void {
  const store = storage();
  if (store == null) return;
  try {
    store.setItem(CACHE_KEY, JSON.stringify(rates));
  } catch {
    // Quota or private mode: caching is a convenience, not a requirement.
  }
}

/**
 * Fetches a fresh snapshot from the same origin. Returns null on any failure — network, HTTP
 * status, timeout, malformed JSON — and leaves the cache untouched in that case.
 */
export async function fetchRates(): Promise<RateSnapshot | null> {
  try {
    const res = await fetch('/rates', {
      headers: { accept: 'application/json' },
      signal: AbortSignal.timeout(6_000),
    });
    if (!res.ok) return null;
    const snapshot = parseRatesPayload(await res.json());
    if (snapshot) rememberRates(snapshot);
    return snapshot;
  } catch {
    return null;
  }
}

/**
 * A short Persian label for when the snapshot was taken. The date formatter is `fa-IR`, so a
 * Persian calendar date; a missing timestamp says so instead of showing 1970.
 */
export function ratesUpdatedLabel(rates: RateSnapshot): string {
  if (rates.updatedAt <= 0) return 'زمان نامشخص';
  try {
    return new Intl.DateTimeFormat('fa-IR', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(rates.updatedAt));
  } catch {
    return 'زمان نامشخص';
  }
}
