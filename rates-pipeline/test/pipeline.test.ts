import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createVerify } from 'node:crypto';
import { parseTgjuAjax } from '../src/sources/tgju.ts';
import { parseBonbastJson } from '../src/sources/bonbast.ts';
import { parseWallexMarkets } from '../src/sources/wallex.ts';
import { parseNobitexUsdt } from '../src/sources/nobitex.ts';
import { parseCoinGeckoMarkets } from '../src/sources/coingecko.ts';
import { parseBinanceVisionTickers } from '../src/sources/binance.ts';
import { parseFawazForex } from '../src/sources/fawazahmed0.ts';
import {
  crossCheckUsdAgainstUsdt,
  guardStepChanges,
  isPlausibleUsdToman,
  validateRatesPayload,
} from '../src/validate.ts';
import { mergeRatesSources } from '../src/merge.ts';
import {
  generateTestKeyPair,
  p1363ToDer,
  signRatesBytes,
  verifyRatesBytes,
} from '../src/sign.ts';
import { RatesPayload } from '../src/types.ts';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PIPELINE_ROOT = join(__dirname, '..');

describe('Rates Pipeline — Upstream Source Parsers', () => {
  it('parses TGJU Rial response into Toman and signed 24h changes', () => {
    const raw = {
      current: {
        price_dollar_rl: { p: '825,000', dp: 0.42, dt: 'high' },
        price_eur: { p: '892,000', dp: 0.35, dt: 'low' },
        price_gbp: { p: '1,045,000', dp: 0.15, dt: 'high' },
        price_nok: { p: '76,000', dp: 0.1, dt: 'high' },
        price_try: { p: '24,100', dp: 0.5, dt: 'low' },
        price_aed: { p: '225,000', dp: 0.2, dt: 'high' },
        price_cad: { p: '598,000', dp: 0.1, dt: 'high' },
        geram18: { p: '64,200,000', dp: 1.18, dt: 'high' },
        mesghal: { p: '278,100,000', dp: 1.12, dt: 'high' },
        silver_999: { p: '920,000', dp: 0.54, dt: 'low' },
        sekee: { p: '745,000,000', dp: 0.95, dt: 'high' },
        sekeb: { p: '712,000,000', dp: 0.8, dt: 'high' },
        nim: { p: '428,000,000', dp: 0.65, dt: 'high' },
        rob: { p: '224,000,000', dp: 0.4, dt: 'low' },
        gerami: { p: '112,000,000', dp: 0.25, dt: 'high' },
      },
    };
    const parsed = parseTgjuAjax(raw);
    expect(parsed.toman.usd).toBe(82500);
    expect(parsed.toman.eur).toBe(89200);
    expect(parsed.toman.gold18).toBe(6420000);
    expect(parsed.toman.silver_925).toBe(Math.round(92000 * 0.925));
    expect(parsed.change24h.usd).toBeCloseTo(0.42);
    expect(parsed.change24h.eur).toBeCloseTo(-0.35);
    expect(parsed.change24h.coin_rob).toBeCloseTo(-0.4);
  });

  it('rejects malformed TGJU response without usable prices', () => {
    expect(() => parseTgjuAjax({})).toThrow(/no usable prices/);
  });

  it('parses Bonbast JSON into ParsedFiatGold', () => {
    const parsed = parseBonbastJson({
      usd1: '82500',
      eur1: '89200',
      gol18: '6420000',
      emami1: '74500000',
    });
    expect(parsed.toman.usd).toBe(82500);
    expect(parsed.toman.gold18).toBe(6420000);
    expect(parsed.toman.coin_emami).toBe(74500000);
  });

  it('parses Wallex TMN markets and ignores reserved asset IDs', () => {
    const parsed = parseWallexMarkets({
      result: {
        symbols: {
          BTCTMN: {
            faBaseAsset: 'بیت کوین',
            stats: { lastPrice: '7189875000', '24h_ch': 2.34 },
          },
          USDTTMN: {
            faBaseAsset: 'تتر',
            stats: { lastPrice: '82500', '24h_ch': 0.38 },
          },
          USDTMN: {
            faBaseAsset: 'دلار',
            stats: { lastPrice: '82500', '24h_ch': 0.1 },
          },
        },
      },
    });
    expect(parsed.prices.btc).toBe(7189875000);
    expect(parsed.prices.usdt).toBe(82500);
    expect(parsed.prices.usd).toBeUndefined(); // reserved ID filtered
    expect(parsed.namesFa.btc).toBe('بیت کوین');
    expect(parsed.change24h.btc).toBe(2.34);
  });

  it('parses Nobitex USDTIRT orderbook from Rial to Toman', () => {
    const parsed = parseNobitexUsdt({ status: 'ok', lastTradePrice: '825000' });
    expect(parsed.prices.usdt).toBe(82500);
    expect(() => parseNobitexUsdt({ status: 'ok', lastTradePrice: '0' })).toThrow();
  });

  it('parses CoinGecko markets response into USD prices, 7-point sparklines, and Coin list', () => {
    const raw = [
      {
        id: 'bitcoin',
        symbol: 'btc',
        name: 'Bitcoin',
        current_price: 87150,
        price_change_percentage_24h: 2.34,
        image: 'https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png',
        sparkline_in_7d: { price: [84000, 84500, 85000, 85500, 86000, 86500, 87000, 87150] },
      },
      {
        id: 'tether',
        symbol: 'usdt',
        name: 'Tether',
        current_price: 1.0,
        price_change_percentage_24h: 0.01,
        image: 'https://coin-images.coingecko.com/coins/images/325/large/Tether.png',
        sparkline_in_7d: { price: [1, 1, 1, 1, 1, 1, 1] },
      },
    ];
    const parsed = parseCoinGeckoMarkets(raw);
    expect(parsed.usd.btc).toBe(87150);
    expect(parsed.sparklinesUsd.btc).toHaveLength(7);
    expect(parsed.coins[0].id).toBe('btc');
    expect(parsed.coins[0].name).toBe('بیت‌کوین');
  });

  it('parses Binance USDT tickers and Fawazahmed0 USD forex rates', () => {
    const binance = parseBinanceVisionTickers([
      { symbol: 'BTCUSDT', price: '87150.50' },
      { symbol: 'ETHUSDT', price: '2690.00' },
    ]);
    expect(binance.btc).toBe(87150.5);
    expect(binance.eth).toBe(2690);
    expect(binance.usdt).toBe(1);

    const fawaz = parseFawazForex(
      {
        usd: { eur: 0.925, gbp: 0.79, nok: 10.8, try: 34.2, aed: 3.67, cad: 1.38 },
      },
      82500,
    );
    expect(fawaz.eur).toBeCloseTo(82500 / 0.925, 1);
    expect(fawaz.gbp).toBeCloseTo(82500 / 0.79, 1);
  });
});

describe('Rates Pipeline — Validation, Cross-Check & Step-Change Guard', () => {
  it('validates golden fixture without errors', () => {
    const goldenPath = join(PIPELINE_ROOT, 'fixtures', 'rates.golden.json');
    const golden = JSON.parse(readFileSync(goldenPath, 'utf8')) as RatesPayload;
    const res = validateRatesPayload(golden);
    expect(res.valid).toBe(true);
    expect(res.errors).toEqual([]);
  });

  it('enforces plausible USD Toman bounds [20_000, 2_000_000]', () => {
    expect(isPlausibleUsdToman(82500)).toBe(true);
    expect(isPlausibleUsdToman(263000)).toBe(true);
    expect(isPlausibleUsdToman(500)).toBe(false);
    expect(isPlausibleUsdToman(9_000_000)).toBe(false);
  });

  it('cross-checks USD against USDT and falls back to USDT when divergence > 8%', () => {
    const ok = crossCheckUsdAgainstUsdt(260000, 263000);
    expect(ok.diverged).toBe(false);
    expect(ok.resolvedUsd).toBe(260000);

    const diverged = crossCheckUsdAgainstUsdt(180000, 263000);
    expect(diverged.diverged).toBe(true);
    expect(diverged.resolvedUsd).toBe(263000);
  });

  it('blocks corrupt >30% step jumps and marks rate stale', () => {
    const nextToman = { usd: 500000, gold18: 6500000 };
    const prevToman = { usd: 250000, gold18: 6400000 };
    const stale: Record<string, boolean> = {};
    const warnings = guardStepChanges(nextToman, prevToman, stale);
    expect(warnings.length).toBe(1);
    expect(nextToman.usd).toBe(250000);
    expect(stale.usd).toBe(true);
    expect(nextToman.gold18).toBe(6500000);
  });
});

describe('Rates Pipeline — Merge & Cryptographic Signing (ECDSA P-256 IEEE-P1363)', () => {
  it('merges partial sources using previous snapshot fallback and produces a valid payload', () => {
    const goldenPath = join(PIPELINE_ROOT, 'fixtures', 'rates.golden.json');
    const previous = JSON.parse(readFileSync(goldenPath, 'utf8')) as RatesPayload;

    const merged = mergeRatesSources({
      nowSec: 1762800000,
      kid: 'k1',
      tgju: null, // simulate TGJU outage
      bonbast: null,
      fawazForexToman: null,
      wallex: {
        prices: { usdt: 263000, btc: 22900000000 },
        namesFa: { btc: 'بیت‌کوین', usdt: 'تتر' },
        change24h: { usdt: 0.5, btc: 1.8 },
      },
      nobitex: {
        prices: { usdt: 263000 },
        namesFa: { usdt: 'تتر' },
        change24h: {},
      },
      coingecko: null, // simulate CoinGecko outage
      binanceUsd: { btc: 87500, eth: 2700, usdt: 1 },
      previous,
    });

    const validation = validateRatesPayload(merged);
    expect(validation.valid).toBe(true);
    expect(merged.toman.usdt).toBe(263000);
    expect(merged.stale.gold18).toBe(true);
  });

  it('signs and verifies payload bytes with ECDSA P-256 (IEEE-P1363) and rejects tampering or wrong key', () => {
    const kp1 = generateTestKeyPair();
    const kp2 = generateTestKeyPair();
    const payloadBytes = Buffer.from('{"schema":1,"kid":"k1","toman":{"usd":82500}}\n', 'utf8');

    const sigB64 = signRatesBytes(payloadBytes, kp1.privateKeyPem);
    expect(Buffer.from(sigB64, 'base64')).toHaveLength(64);

    // Valid verification
    expect(verifyRatesBytes(payloadBytes, sigB64, kp1.publicKeyPem)).toBe(true);

    // Tampered payload rejected
    const tamperedBytes = Buffer.from('{"schema":1,"kid":"k1","toman":{"usd":1}}\n', 'utf8');
    expect(verifyRatesBytes(tamperedBytes, sigB64, kp1.publicKeyPem)).toBe(false);

    // Wrong key rejected
    expect(verifyRatesBytes(payloadBytes, sigB64, kp2.publicKeyPem)).toBe(false);

    // P1363 -> ASN.1 DER conversion works with standard SHA256withECDSA verifier (mirrors Android)
    const derSig = p1363ToDer(Buffer.from(sigB64, 'base64'));
    const verifier = createVerify('SHA256');
    verifier.update(payloadBytes);
    verifier.end();
    expect(verifier.verify(kp1.publicKeyPem, derSig)).toBe(true);
  });
});
