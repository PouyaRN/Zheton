import { normalizeTimestampMs, verifyRatesSignature } from '../services/marketService';

export interface RateSnapshot {
  updatedAt: number;
  toman: Record<string, number>;
}

/** The rate key for one gram of 18-karat gold — what the bubble tool asks for. */
export const GOLD_18_RATE_KEY = 'gold18';

const CACHE_KEY = 'rates:last-good:v1';

/**
 * Validates the payload and keeps only usable prices.
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

  const rawUpdated =
    typeof record.updatedAt === 'number' && Number.isFinite(record.updatedAt)
      ? normalizeTimestampMs(record.updatedAt)
      : 0;

  return {
    updatedAt: rawUpdated,
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
 * Fetches a fresh signed snapshot (`rates.json` + `rates.json.sig`) and verifies its ECDSA P-256 signature.
 */
export async function fetchRates(): Promise<RateSnapshot | null> {
  const urls = ['/Zheton/rates.json', '/rates.json', './rates.json'];
  for (const url of urls) {
    try {
      const [res, sigRes] = await Promise.all([
        fetch(url, {
          headers: { accept: 'application/json' },
          signal: AbortSignal.timeout(6_000),
        }),
        fetch(`${url}.sig`, {
          headers: { accept: 'text/plain' },
          signal: AbortSignal.timeout(6_000),
        }),
      ]);
      if (!res.ok || !sigRes.ok) continue;
      const bytes = new Uint8Array(await res.arrayBuffer());
      const sigB64 = (await sigRes.text()).trim();
      const text = new TextDecoder('utf-8').decode(bytes);
      const json = JSON.parse(text) as Record<string, unknown>;
      const kid = typeof json.kid === 'string' ? json.kid : 'k1';
      const valid = await verifyRatesSignature(bytes, sigB64, kid);
      if (!valid) continue;

      const snapshot = parseRatesPayload(json);
      if (snapshot) {
        rememberRates(snapshot);
        return snapshot;
      }
    } catch {
      continue;
    }
  }
  return null;
}

/**
 * A short Persian label for when the snapshot was taken.
 */
export function ratesUpdatedLabel(rates: RateSnapshot): string {
  if (rates.updatedAt <= 0) return 'زمان نامشخص';
  try {
    return new Intl.DateTimeFormat('fa-IR', {
      dateStyle: 'short',
      timeStyle: 'short',
    }).format(new Date(normalizeTimestampMs(rates.updatedAt)));
  } catch {
    return 'زمان نامشخص';
  }
}
