export interface WalletOption {
  network: string;
  networkFa: string;
  contract?: string;
}

export interface Coin {
  id: string;
  name: string;
  en: string;
  icon: string;
  wallets: WalletOption[];
}

export interface ReleaseInfo {
  name: string;
  url: string;
  apk: string;
  notes: string[];
}

export interface RatesPayload {
  schema: 1;
  kid: string;
  updatedAt: number;
  generatedAt: number;
  toman: Record<string, number>;
  usd: Record<string, number>;
  change24h: Record<string, number>;
  sparklines: Record<string, number[]>;
  coins: Coin[];
  sources: Record<string, string>;
  stale: Record<string, boolean>;
  latest: ReleaseInfo | null;
}

export interface ParsedFiatGold {
  toman: Record<string, number>;
  change24h: Record<string, number>;
}

export interface ParsedTomanCrypto {
  prices: Record<string, number>;
  namesFa: Record<string, string>;
  change24h: Record<string, number>;
}

export interface ParsedCoinGecko {
  usd: Record<string, number>;
  change24h: Record<string, number>;
  sparklinesUsd: Record<string, number[]>;
  coins: Coin[];
}

export const PLAUSIBLE_USD_TOMAN = { min: 20_000, max: 2_000_000 } as const;

export const RESERVED_ASSET_IDS = new Set([
  'toman',
  'bank',
  'usd',
  'eur',
  'gbp',
  'nok',
  'try',
  'aed',
  'cad',
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
]);

export function parsePositiveNumber(v: unknown): number | null {
  if (v == null) return null;
  const cleaned = typeof v === 'number' ? v : Number(String(v).replace(/,/g, '').trim());
  return Number.isFinite(cleaned) && cleaned > 0 ? cleaned : null;
}

export function parseFiniteNumber(v: unknown): number | null {
  if (v == null) return null;
  const cleaned = typeof v === 'number' ? v : Number(String(v).replace(/,/g, '').trim());
  return Number.isFinite(cleaned) ? cleaned : null;
}

