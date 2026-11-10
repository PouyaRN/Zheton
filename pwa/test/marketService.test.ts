import { describe, it, expect, beforeEach, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  evaluateRatesFreshness,
  fetchVerifiedMarketState,
  isRatesReplaySafe,
  mapPayloadToMarketQuotes,
  SignedRatesPayload,
  VERIFIED_RATES_CACHE_KEY,
  verifyRatesSignature,
} from '../src/services/marketService';

const REPO_ROOT = join(__dirname, '..', '..');
const GOLDEN_BYTES = new Uint8Array(
  readFileSync(join(REPO_ROOT, 'rates-pipeline', 'fixtures', 'rates.golden.json')),
);
const GOLDEN_SIG = readFileSync(
  join(REPO_ROOT, 'rates-pipeline', 'fixtures', 'rates.golden.json.sig'),
  'utf8',
).trim();
const GOLDEN_JSON = JSON.parse(
  readFileSync(join(REPO_ROOT, 'rates-pipeline', 'fixtures', 'rates.golden.json'), 'utf8'),
) as SignedRatesPayload;

describe('PWA marketService — ECDSA P-256 Verification, Freshness & Replay Protection', () => {
  beforeEach(() => {
    try {
      if (typeof localStorage !== 'undefined' && typeof localStorage.clear === 'function') {
        localStorage.clear();
      }
    } catch {
      // ignore in node without --localstorage-file
    }
    vi.restoreAllMocks();
  });

  it('verifies golden rates.json signature using WebCrypto ECDSA P-256 (SHA-256)', async () => {
    const ok = await verifyRatesSignature(GOLDEN_BYTES, GOLDEN_SIG, 'k1');
    expect(ok).toBe(true);
  });

  it('rejects tampered payload bytes or unknown key id', async () => {
    const tampered = new Uint8Array(GOLDEN_BYTES);
    tampered[25] ^= 0x01;
    expect(await verifyRatesSignature(tampered, GOLDEN_SIG, 'k1')).toBe(false);
    expect(await verifyRatesSignature(GOLDEN_BYTES, GOLDEN_SIG, 'k99')).toBe(false);
  });

  it('evaluates fresh (<48h), stale (48h..7d), and expired (>7d) thresholds accurately', () => {
    const nowMs = GOLDEN_JSON.updatedAt;
    expect(evaluateRatesFreshness(GOLDEN_JSON.updatedAt, nowMs + 2 * 3600_000)).toBe('fresh');
    expect(evaluateRatesFreshness(GOLDEN_JSON.updatedAt, nowMs + 50 * 3600_000)).toBe('stale');
    expect(evaluateRatesFreshness(GOLDEN_JSON.updatedAt, nowMs + 8 * 24 * 3600_000)).toBe('expired');
  });

  it('maps fresh payload to positive prices and hides expired (>7d) prices', () => {
    const freshNow = GOLDEN_JSON.updatedAt + 60_000;
    const freshMapped = mapPayloadToMarketQuotes(GOLDEN_JSON, freshNow);
    expect(freshMapped.freshness).toBe('fresh');
    const usdQuote = freshMapped.quotes.find((q) => q.id === 'usd');
    expect(usdQuote?.priceToman).toBe(GOLDEN_JSON.toman.usd);

    const expiredNow = GOLDEN_JSON.updatedAt + 9 * 24 * 3600_000;
    const expiredMapped = mapPayloadToMarketQuotes(GOLDEN_JSON, expiredNow);
    expect(expiredMapped.freshness).toBe('expired');
    expect(expiredMapped.quotes.every((q) => q.priceToman === 0)).toBe(true);
  });

  it('enforces replay protection (incoming updatedAt >= cached updatedAt)', () => {
    expect(isRatesReplaySafe(1762800000, 1762700000)).toBe(true);
    expect(isRatesReplaySafe(1762800000, 1762800000)).toBe(true);
    expect(isRatesReplaySafe(1762600000, 1762700000)).toBe(false);
  });

  it('fetchVerifiedMarketState verifies signature and rejects tampered network responses', async () => {
    const nowMs = GOLDEN_JSON.updatedAt + 10_000;

    // 1. Valid signed fetch
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('.sig')) {
        return new Response(GOLDEN_SIG, { status: 200 });
      }
      return new Response(GOLDEN_BYTES, { status: 200 });
    });

    const stateOk = await fetchVerifiedMarketState({ urls: ['/rates.json'], nowMs });
    expect(stateOk.verified).toBe(true);
    expect(stateOk.statusReason).toBe('ok');
    expect(stateOk.quotes.find((q) => q.id === 'gold18')?.priceToman).toBe(GOLDEN_JSON.toman.gold18);

    // 2. Tampered fetch (price altered without re-signing)
    const tamperedJson = JSON.stringify({
      ...GOLDEN_JSON,
      toman: { ...GOLDEN_JSON.toman, usd: 999999 },
    });
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('.sig')) {
        return new Response(GOLDEN_SIG, { status: 200 });
      }
      return new Response(tamperedJson, { status: 200 });
    });

    const stateTampered = await fetchVerifiedMarketState({ urls: ['/rates.json'], nowMs });
    expect(stateTampered.statusReason).toBe('tampered');
    // Retains cached verified price or unpriced if no cache
    const cachedPrice = stateTampered.quotes.find((q) => q.id === 'usd')?.priceToman;
    expect(cachedPrice).not.toBe(999999);
  });
});
