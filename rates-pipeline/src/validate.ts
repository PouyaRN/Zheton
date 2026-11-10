import { PLAUSIBLE_USD_TOMAN, RatesPayload } from './types.ts';

export interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

export const REQUIRED_TOMAN_KEYS = [
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
  'btc',
  'eth',
  'usdt',
] as const;

export const MAX_RELATIVE_STEP_CHANGE = 0.30; // 30% max step change per run without fallback/warning
export const MAX_USD_USDT_DIVERGENCE = 0.08; // 8% max divergence between free-market USD and exchange USDT

export function isPlausibleUsdToman(usd: number): boolean {
  return (
    Number.isFinite(usd) &&
    usd >= PLAUSIBLE_USD_TOMAN.min &&
    usd <= PLAUSIBLE_USD_TOMAN.max
  );
}

/**
 * Cross-checks USD free-market Toman price against exchange USDT Toman price.
 * If they diverge by more than MAX_USD_USDT_DIVERGENCE (8%), returns the safer median/exchange rate.
 */
export function crossCheckUsdAgainstUsdt(
  usdToman: number | undefined,
  usdtToman: number | undefined,
): { resolvedUsd: number | null; diverged: boolean; divergenceRatio: number } {
  const validUsd = usdToman != null && isPlausibleUsdToman(usdToman) ? usdToman : null;
  const validUsdt = usdtToman != null && isPlausibleUsdToman(usdtToman) ? usdtToman : null;

  if (validUsd != null && validUsdt != null) {
    const diff = Math.abs(validUsd - validUsdt) / Math.max(validUsd, validUsdt);
    if (diff > MAX_USD_USDT_DIVERGENCE) {
      return {
        resolvedUsd: validUsdt,
        diverged: true,
        divergenceRatio: diff,
      };
    }
    return {
      resolvedUsd: validUsd,
      diverged: false,
      divergenceRatio: diff,
    };
  }

  return {
    resolvedUsd: validUsd ?? validUsdt,
    diverged: false,
    divergenceRatio: 0,
  };
}

/**
 * Guards against sudden corrupt step changes (>30% jump/drop in a single cycle) when previous payload is available.
 */
export function guardStepChanges(
  nextToman: Record<string, number>,
  previousToman: Record<string, number> | undefined,
  staleFlags: Record<string, boolean>,
): string[] {
  const warnings: string[] = [];
  if (!previousToman) return warnings;

  for (const [key, nextVal] of Object.entries(nextToman)) {
    const prevVal = previousToman[key];
    if (prevVal != null && Number.isFinite(prevVal) && prevVal > 0 && Number.isFinite(nextVal) && nextVal > 0) {
      const ratio = Math.abs(nextVal - prevVal) / prevVal;
      if (ratio > MAX_RELATIVE_STEP_CHANGE) {
        warnings.push(
          `Step change of ${(ratio * 100).toFixed(1)}% detected for '${key}' (${prevVal} -> ${nextVal}); retaining previous value.`,
        );
        nextToman[key] = prevVal;
        staleFlags[key] = true;
      }
    }
  }
  return warnings;
}

/**
 * Validates a complete RatesPayload against schema and plausibility rules.
 */
export function validateRatesPayload(payload: RatesPayload): ValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  if (payload.schema !== 1) {
    errors.push(`Unsupported schema version: ${String(payload.schema)}`);
  }

  if (!payload.kid || !/^k[0-9]+$/.test(payload.kid)) {
    errors.push(`Invalid key identifier (kid): ${String(payload.kid)}`);
  }

  if (!Number.isInteger(payload.updatedAt) || payload.updatedAt < 1700000000) {
    errors.push(`Invalid updatedAt epoch seconds: ${String(payload.updatedAt)}`);
  }

  if (!Number.isInteger(payload.generatedAt) || payload.generatedAt < 1700000000) {
    errors.push(`Invalid generatedAt epoch seconds: ${String(payload.generatedAt)}`);
  }

  if (!payload.toman || typeof payload.toman !== 'object') {
    errors.push('Missing toman map');
  } else {
    for (const reqKey of REQUIRED_TOMAN_KEYS) {
      const val = payload.toman[reqKey];
      if (typeof val !== 'number' || !Number.isFinite(val) || val <= 0) {
        errors.push(`Missing or non-positive required toman rate: '${reqKey}' (${String(val)})`);
      }
    }

    const usd = payload.toman.usd;
    if (typeof usd === 'number' && !isPlausibleUsdToman(usd)) {
      errors.push(
        `USD Toman rate ${usd} is outside plausible bounds [${PLAUSIBLE_USD_TOMAN.min}, ${PLAUSIBLE_USD_TOMAN.max}]`,
      );
    }

    for (const [k, v] of Object.entries(payload.toman)) {
      if (typeof v !== 'number' || !Number.isFinite(v) || v <= 0) {
        errors.push(`Invalid toman value for '${k}': ${String(v)}`);
      }
    }
  }

  if (!payload.usd || typeof payload.usd !== 'object') {
    errors.push('Missing usd map');
  } else {
    for (const reqCrypto of ['btc', 'eth', 'usdt']) {
      const val = payload.usd[reqCrypto];
      if (typeof val !== 'number' || !Number.isFinite(val) || val <= 0) {
        errors.push(`Missing or non-positive required usd rate: '${reqCrypto}' (${String(val)})`);
      }
    }
    for (const [k, v] of Object.entries(payload.usd)) {
      if (typeof v !== 'number' || !Number.isFinite(v) || v <= 0) {
        errors.push(`Invalid usd value for '${k}': ${String(v)}`);
      }
    }
  }

  if (!payload.change24h || typeof payload.change24h !== 'object') {
    errors.push('Missing change24h map');
  } else {
    for (const [k, v] of Object.entries(payload.change24h)) {
      if (typeof v !== 'number' || !Number.isFinite(v)) {
        errors.push(`Invalid change24h value for '${k}': ${String(v)}`);
      }
    }
  }

  if (!payload.sparklines || typeof payload.sparklines !== 'object') {
    errors.push('Missing sparklines map');
  } else {
    for (const [k, arr] of Object.entries(payload.sparklines)) {
      if (!Array.isArray(arr) || arr.length !== 7 || arr.some((n) => typeof n !== 'number' || !Number.isFinite(n) || n <= 0)) {
        errors.push(`Invalid 7-point sparkline for '${k}'`);
      }
    }
  }

  if (!Array.isArray(payload.coins) || payload.coins.length === 0) {
    errors.push('coins list must be a non-empty array');
  } else {
    for (const coin of payload.coins) {
      if (!coin.id || !/^[a-z0-9_]+$/.test(coin.id)) {
        errors.push(`Invalid coin id: ${String(coin.id)}`);
      }
      if (!coin.name || !coin.en) {
        errors.push(`Coin '${coin.id}' is missing name or en symbol`);
      }
      if (coin.icon && !coin.icon.startsWith('https://')) {
        errors.push(`Coin '${coin.id}' icon must use HTTPS or be empty`);
      }
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

