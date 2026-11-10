import {
  Coin,
  ParsedCoinGecko,
  RESERVED_ASSET_IDS,
  WalletOption,
  parseFiniteNumber,
  parsePositiveNumber,
} from '../types.ts';

const WALLET_PLATFORMS: Record<string, Omit<WalletOption, 'contract'>> = {
  ethereum: { network: 'ethereum', networkFa: 'اتریوم' },
  tron: { network: 'tron', networkFa: 'ترون' },
  'binance-smart-chain': { network: 'bsc', networkFa: 'BSC' },
  'arbitrum-one': { network: 'arbitrum', networkFa: 'آربیتروم' },
  'polygon-pos': { network: 'polygon', networkFa: 'پالیگان' },
  'optimistic-ethereum': { network: 'optimism', networkFa: 'آپتیمیسم' },
  avalanche: { network: 'avalanche', networkFa: 'آوالانچ' },
};

const NATIVE_WALLETS: Record<string, WalletOption> = {
  bitcoin: { network: 'bitcoin', networkFa: 'بیت کوین' },
  ethereum: { network: 'ethereum', networkFa: 'اتریوم' },
  solana: { network: 'solana', networkFa: 'سولانا' },
  tron: { network: 'tron', networkFa: 'ترون' },
};

export const DEFAULT_FA_NAMES: Record<string, string> = {
  btc: 'بیت‌کوین',
  eth: 'اتریوم',
  usdt: 'تتر',
  bnb: 'بایننس کوین',
  sol: 'سولانا',
  xrp: 'ریپل',
  usdc: 'یواس‌دی کوین',
  ada: 'کاردانو',
  doge: 'دوج‌کوین',
  trx: 'ترون',
  ton: 'تون‌کوین',
  avax: 'آوالانچ',
  shib: 'شیبا اینو',
  dot: 'پولکادات',
  link: 'چین‌لینک',
};

function downsampleSparkline(prices: unknown, points = 7): number[] {
  if (!Array.isArray(prices)) return [];
  const valid = prices
    .map((p) => parsePositiveNumber(p))
    .filter((p): p is number => p != null);
  if (valid.length < points) return valid;
  const result: number[] = [];
  for (let i = 0; i < points; i++) {
    const idx = Math.min(
      valid.length - 1,
      Math.round((i * (valid.length - 1)) / (points - 1)),
    );
    result.push(valid[idx]);
  }
  return result;
}

export function parseCoinGeckoMarkets(
  marketsData: unknown,
  platformsList: unknown = [],
): ParsedCoinGecko {
  if (!Array.isArray(marketsData) || marketsData.length === 0) {
    throw new Error('CoinGecko markets list is empty or invalid');
  }

  const platformsById = new Map<string, Record<string, unknown>>();
  if (Array.isArray(platformsList)) {
    for (const item of platformsList) {
      if (item && typeof item === 'object') {
        const rec = item as Record<string, unknown>;
        if (typeof rec.id === 'string' && rec.platforms && typeof rec.platforms === 'object') {
          platformsById.set(rec.id, rec.platforms as Record<string, unknown>);
        }
      }
    }
  }

  const usd: Record<string, number> = {};
  const change24h: Record<string, number> = {};
  const sparklinesUsd: Record<string, number[]> = {};
  const coins: Coin[] = [];

  for (const item of marketsData) {
    if (!item || typeof item !== 'object') continue;
    const rec = item as Record<string, unknown>;
    const id = String(rec.symbol ?? '').trim().toLowerCase();
    const geckoId = String(rec.id ?? '').trim();
    const price = parsePositiveNumber(rec.current_price);
    if (!id || !geckoId || price == null || RESERVED_ASSET_IDS.has(id)) continue;
    if (id in usd) continue;

    usd[id] = price;
    const ch = parseFiniteNumber(rec.price_change_percentage_24h);
    if (ch != null) change24h[id] = Math.round(ch * 100) / 100;

    const sparkObj = rec.sparkline_in_7d as Record<string, unknown> | undefined;
    const sampled = downsampleSparkline(sparkObj?.price, 7);
    if (sampled.length > 0) sparklinesUsd[id] = sampled;

    const wallets: WalletOption[] = [];
    if (NATIVE_WALLETS[geckoId]) wallets.push(NATIVE_WALLETS[geckoId]);
    const platMap = platformsById.get(geckoId) ?? {};
    for (const [platKey, contractVal] of Object.entries(platMap)) {
      const known = WALLET_PLATFORMS[platKey];
      const contract = String(contractVal ?? '').trim();
      if (known && contract) wallets.push({ ...known, contract });
    }

    const en = String(rec.name ?? id.toUpperCase()).trim();
    coins.push({
      id,
      name: DEFAULT_FA_NAMES[id] ?? en,
      en,
      icon: '',
      wallets,
    });
  }

  if (Object.keys(usd).length === 0) {
    throw new Error('CoinGecko produced no valid USD prices');
  }

  return { usd, change24h, sparklinesUsd, coins };
}

export async function fetchCoinGeckoMarkets(limit = 100, timeoutMs = 12_000): Promise<ParsedCoinGecko> {
  const res = await fetch(
    `https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=${limit}&page=1&sparkline=true`,
    {
      headers: {
        accept: 'application/json',
        'user-agent': 'Zheton-Rates-Pipeline/1.2 (+https://github.com/PouyaRN/Zheton)',
      },
      signal: AbortSignal.timeout(timeoutMs),
    },
  );
  if (!res.ok) throw new Error(`CoinGecko HTTP ${res.status}`);
  return parseCoinGeckoMarkets(await res.json());
}
