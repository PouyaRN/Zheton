import type {
  Coin,
  ParsedCoinGecko,
  ParsedFiatGold,
  ParsedTomanCrypto,
  RatesPayload,
  ReleaseInfo,
} from './types.ts';
import {
  crossCheckUsdAgainstUsdt,
  guardStepChanges,
  isPlausibleUsdToman,
  REQUIRED_TOMAN_KEYS,
} from './validate.ts';
import { DEFAULT_FA_NAMES } from './sources/coingecko.ts';

export interface MergeInputs {
  nowSec: number;
  kid?: string;
  tgju?: ParsedFiatGold | null;
  bonbast?: ParsedFiatGold | null;
  fawazForexToman?: Record<string, number> | null;
  wallex?: ParsedTomanCrypto | null;
  nobitex?: ParsedTomanCrypto | null;
  coingecko?: ParsedCoinGecko | null;
  binanceUsd?: Record<string, number> | null;
  previous?: RatesPayload | null;
  latestRelease?: ReleaseInfo | null;
}

function buildSparklineFromCurrent(
  currentPrice: number,
  change24hPct: number,
  previousSpark?: number[],
): number[] {
  if (
    Array.isArray(previousSpark) &&
    previousSpark.length === 7 &&
    previousSpark.every((n) => n > 0)
  ) {
    return [...previousSpark.slice(1), Math.round(currentPrice)];
  }
  const safeChange = Number.isFinite(change24hPct)
    ? Math.max(-25, Math.min(25, change24hPct))
    : 0;
  const startFactor = 1 - (safeChange / 100) * 1.4;
  const points: number[] = [];
  for (let i = 0; i < 6; i++) {
    const t = i / 6;
    const factor = startFactor + (1 - startFactor) * t;
    points.push(Math.max(1, Math.round(currentPrice * factor)));
  }
  points.push(Math.max(1, Math.round(currentPrice)));
  return points;
}

export function mergeRatesSources(inputs: MergeInputs): RatesPayload {
  const {
    nowSec,
    kid = 'k1',
    tgju,
    bonbast,
    fawazForexToman,
    wallex,
    nobitex,
    coingecko,
    binanceUsd,
    previous,
    latestRelease = null,
  } = inputs;

  const toman: Record<string, number> = {};
  const usd: Record<string, number> = {};
  const change24h: Record<string, number> = {};
  const sparklines: Record<string, number[]> = {};
  const stale: Record<string, boolean> = {};
  const sources: Record<string, string> = {
    fiat: 'unavailable',
    gold: 'unavailable',
    crypto_toman: 'unavailable',
    crypto_usd: 'unavailable',
  };

  // 1. Exchange USDT (Wallex -> Nobitex -> previous)
  const wallexUsdt = wallex?.prices?.usdt;
  const nobitexUsdt = nobitex?.prices?.usdt;
  let usdtToman: number | undefined;
  if (wallexUsdt && isPlausibleUsdToman(wallexUsdt)) {
    usdtToman = wallexUsdt;
    sources.crypto_toman = 'wallex';
  } else if (nobitexUsdt && isPlausibleUsdToman(nobitexUsdt)) {
    usdtToman = nobitexUsdt;
    sources.crypto_toman = 'nobitex';
  } else if (previous?.toman?.usdt && isPlausibleUsdToman(previous.toman.usdt)) {
    usdtToman = previous.toman.usdt;
    stale.usdt = true;
    sources.crypto_toman = 'cached';
  }

  // 2. Fiat & Gold (TGJU -> Bonbast -> previous)
  const tgjuUsd = tgju?.toman?.usd;
  const bonbastUsd = bonbast?.toman?.usd;
  const candidateFiatUsd =
    tgjuUsd && isPlausibleUsdToman(tgjuUsd)
      ? tgjuUsd
      : bonbastUsd && isPlausibleUsdToman(bonbastUsd)
        ? bonbastUsd
        : undefined;

  const crossCheck = crossCheckUsdAgainstUsdt(candidateFiatUsd, usdtToman);
  let resolvedUsd = crossCheck.resolvedUsd;
  if (!resolvedUsd && previous?.toman?.usd && isPlausibleUsdToman(previous.toman.usd)) {
    resolvedUsd = previous.toman.usd;
    stale.usd = true;
  }

  if (!resolvedUsd) {
    throw new Error(
      'Cannot merge rates: no plausible USD or USDT Toman price available from any source',
    );
  }

  toman.usd = Math.round(resolvedUsd);
  toman.usdt = Math.round(usdtToman ?? resolvedUsd);

  if (tgjuUsd && isPlausibleUsdToman(tgjuUsd) && !crossCheck.diverged) {
    sources.fiat = 'tgju';
  } else if (bonbastUsd && isPlausibleUsdToman(bonbastUsd) && !crossCheck.diverged) {
    sources.fiat = 'bonbast';
  } else if (usdtToman) {
    sources.fiat = `${sources.crypto_toman}_usdt_proxy`;
  } else {
    sources.fiat = 'cached';
  }

  // Populate Fiat currencies (TGJU -> Bonbast -> Fawazahmed0 -> previous)
  for (const fiatKey of ['eur', 'gbp', 'nok', 'try', 'aed', 'cad']) {
    const fromTgju = tgju?.toman?.[fiatKey];
    const fromBonbast = bonbast?.toman?.[fiatKey];
    const fromFawaz = fawazForexToman?.[fiatKey];
    const fromPrev = previous?.toman?.[fiatKey];

    if (fromTgju && fromTgju > 0) {
      toman[fiatKey] = Math.round(fromTgju);
    } else if (fromBonbast && fromBonbast > 0) {
      toman[fiatKey] = Math.round(fromBonbast);
    } else if (fromFawaz && fromFawaz > 0) {
      toman[fiatKey] = Math.round(fromFawaz);
    } else if (fromPrev && fromPrev > 0) {
      toman[fiatKey] = Math.round(fromPrev);
      stale[fiatKey] = true;
    }
  }

  // Populate Gold, Silver, Coins & Parsian (TGJU -> Bonbast -> previous)
  const goldKeys = [
    'gold18',
    'gold_mesghal',
    'silver_999',
    'silver_925',
    'coin_emami',
    'coin_bahar',
    'coin_nim',
    'coin_rob',
    'coin_gerami',
    'parsian',
  ] as const;

  let goldFromTgju = false;
  let goldFromBonbast = false;
  for (const gKey of goldKeys) {
    const fromTgju = tgju?.toman?.[gKey];
    const fromBonbast = bonbast?.toman?.[gKey];
    const fromPrev = previous?.toman?.[gKey];

    if (fromTgju && fromTgju > 0) {
      toman[gKey] = Math.round(fromTgju * 100) / 100;
      goldFromTgju = true;
    } else if (fromBonbast && fromBonbast > 0) {
      toman[gKey] = Math.round(fromBonbast * 100) / 100;
      goldFromBonbast = true;
    } else if (fromPrev && fromPrev > 0) {
      toman[gKey] = Math.round(fromPrev * 100) / 100;
      stale[gKey] = true;
    }
  }

  // Copy parsian_* soot rates from TGJU or previous
  for (const [k, v] of Object.entries(tgju?.toman ?? {})) {
    if (k.startsWith('parsian_') && v > 0) {
      toman[k] = Math.round(v);
    }
  }

  // Fallback estimations if silver_925 or gold_mesghal missing
  if (!toman.gold_mesghal && toman.gold18) {
    toman.gold_mesghal = Math.round(toman.gold18 * 4.3318);
  }
  if (!toman.silver_925 && toman.silver_999) {
    toman.silver_925 = Math.round(toman.silver_999 * 0.925);
  }

  // Derived Gold ETFs (for PWA market instruments compatibility)
  if (toman.gold18) {
    toman.etf_ayar =
      previous?.toman?.etf_ayar && stale.gold18
        ? previous.toman.etf_ayar
        : Math.round(toman.gold18 * 0.0085);
    toman.etf_kahroba =
      previous?.toman?.etf_kahroba && stale.gold18
        ? previous.toman.etf_kahroba
        : Math.round(toman.gold18 * 0.0081);
    toman.etf_afra =
      previous?.toman?.etf_afra && stale.gold18
        ? previous.toman.etf_afra
        : Math.round(toman.gold18 * 0.0078);
  }

  sources.gold = goldFromTgju ? 'tgju' : goldFromBonbast ? 'bonbast' : 'cached';

  // Copy TGJU 24h changes
  if (tgju?.change24h) {
    for (const [k, v] of Object.entries(tgju.change24h)) {
      if (Number.isFinite(v)) {
        change24h[k] = Number(v.toFixed(2));
      }
    }
  }
  if (change24h.gold18 != null) {
    change24h.etf_ayar ??= change24h.gold18;
    change24h.etf_kahroba ??= change24h.gold18;
    change24h.etf_afra ??= change24h.gold18;
  }

  // 3. Crypto USD & Toman (CoinGecko + Binance + Wallex)
  usd.usdt = 1;
  if (coingecko && Object.keys(coingecko.usd).length > 0) {
    sources.crypto_usd = 'coingecko';
    for (const [sym, priceUsd] of Object.entries(coingecko.usd)) {
      if (priceUsd > 0) {
        usd[sym] = priceUsd;
      }
    }
    for (const [sym, ch] of Object.entries(coingecko.change24h)) {
      if (Number.isFinite(ch)) {
        change24h[sym] = Number(ch.toFixed(2));
      }
    }
  } else if (binanceUsd && Object.keys(binanceUsd).length > 0) {
    sources.crypto_usd = 'binance';
    for (const [sym, priceUsd] of Object.entries(binanceUsd)) {
      if (priceUsd > 0) {
        usd[sym] = priceUsd;
      }
    }
  } else if (previous?.usd) {
    sources.crypto_usd = 'cached';
    for (const [sym, priceUsd] of Object.entries(previous.usd)) {
      if (priceUsd > 0) {
        usd[sym] = priceUsd;
        stale[sym] = true;
      }
    }
  }

  // Merge Wallex Toman crypto prices & 24h changes
  if (wallex) {
    for (const [sym, priceTmn] of Object.entries(wallex.prices)) {
      if (priceTmn > 0) {
        toman[sym] = Math.round(priceTmn * 100) / 100;
      }
    }
    for (const [sym, ch] of Object.entries(wallex.change24h)) {
      if (Number.isFinite(ch) && change24h[sym] == null) {
        change24h[sym] = Number(ch.toFixed(2));
      }
    }
  }

  // Ensure every USD crypto has a Toman price (via USDT Toman rate) and vice versa
  const baseTomanPerUsdt = toman.usdt || toman.usd;
  for (const [sym, priceUsd] of Object.entries(usd)) {
    if (!toman[sym] && priceUsd > 0) {
      toman[sym] = Math.max(0.01, Math.round(priceUsd * baseTomanPerUsdt * 100) / 100);
    }
  }
  for (const [sym, priceTmn] of Object.entries(toman)) {
    if (
      !usd[sym] &&
      !REQUIRED_TOMAN_KEYS.includes(sym as any) &&
      !sym.startsWith('parsian') &&
      !sym.startsWith('etf_') &&
      priceTmn > 0 &&
      baseTomanPerUsdt > 0
    ) {
      usd[sym] = Number((priceTmn / baseTomanPerUsdt).toPrecision(6));
    }
  }

  // Fallback any missing required keys from previous
  if (previous) {
    for (const reqKey of REQUIRED_TOMAN_KEYS) {
      if (!toman[reqKey] && previous.toman?.[reqKey] > 0) {
        toman[reqKey] = previous.toman[reqKey];
        stale[reqKey] = true;
      }
    }
    for (const [k, v] of Object.entries(previous.change24h ?? {})) {
      if (change24h[k] == null && Number.isFinite(v)) {
        change24h[k] = v;
      }
    }
  }

  // Ensure every key in toman has a finite change24h entry
  for (const k of Object.keys(toman)) {
    if (change24h[k] == null || !Number.isFinite(change24h[k])) {
      change24h[k] = 0;
    }
  }

  // Guard against corrupt >30% step jumps compared to previous valid snapshot
  guardStepChanges(toman, previous?.toman, stale);

  // Build 7-point Toman sparklines
  const sparkKeys = new Set<string>([
    'usd',
    'eur',
    'gbp',
    'aed',
    'try',
    'gold18',
    'gold_mesghal',
    'silver_999',
    'coin_emami',
    'coin_bahar',
    'coin_nim',
    'coin_rob',
    'etf_ayar',
    'etf_kahroba',
    'etf_afra',
    'btc',
    'eth',
    'usdt',
    'ton',
    'sol',
    'bnb',
    'xrp',
    'trx',
  ]);

  for (const key of sparkKeys) {
    const currentToman = toman[key];
    if (!currentToman || currentToman <= 0) continue;

    const cgSparkUsd = coingecko?.sparklinesUsd?.[key];
    if (cgSparkUsd && cgSparkUsd.length === 7 && cgSparkUsd.every((n) => n > 0)) {
      const scaled = cgSparkUsd.map((u) => Math.max(1, Math.round(u * baseTomanPerUsdt)));
      scaled[6] = Math.round(currentToman);
      sparklines[key] = scaled;
    } else {
      sparklines[key] = buildSparklineFromCurrent(
        currentToman,
        change24h[key] ?? 0,
        previous?.sparklines?.[key],
      );
    }
  }

  // Build Coin catalog
  const coinMap = new Map<string, Coin>();
  if (coingecko?.coins) {
    for (const c of coingecko.coins) {
      const faFromWallex = wallex?.namesFa?.[c.id];
      coinMap.set(c.id, {
        ...c,
        name: faFromWallex || DEFAULT_FA_NAMES[c.id] || c.name,
      });
    }
  }
  if (previous?.coins) {
    for (const c of previous.coins) {
      if (!coinMap.has(c.id)) {
        coinMap.set(c.id, c);
      }
    }
  }
  const fallbackCoins: Coin[] = [
    { id: 'btc', name: 'بیت‌کوین', en: 'Bitcoin', icon: '', wallets: [] },
    { id: 'eth', name: 'اتریوم', en: 'Ethereum', icon: '', wallets: [] },
    { id: 'usdt', name: 'تتر', en: 'Tether', icon: '', wallets: [] },
  ];
  for (const fc of fallbackCoins) {
    if (!coinMap.has(fc.id)) {
      coinMap.set(fc.id, fc);
    }
  }

  const allFresh = Object.values(stale).every((v) => !v);
  const updatedAt = allFresh || !previous?.updatedAt ? nowSec : previous.updatedAt;

  return {
    schema: 1,
    kid,
    updatedAt,
    generatedAt: nowSec,
    toman,
    usd,
    change24h,
    sparklines,
    coins: Array.from(coinMap.values()),
    sources,
    stale,
    latest: latestRelease ?? previous?.latest ?? null,
  };
}
