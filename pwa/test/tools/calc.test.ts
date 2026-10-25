import { describe, expect, it } from 'vitest';
import {
  coinBubble,
  coinBubblePercent,
  delayDamagesToman,
  habitAnnualCostToman,
  habitEstimate,
  habitTotalCostToman,
  inflationEstimate,
  intrinsicGoldValueToman,
  loanEstimate,
  loanInstallmentToman,
  loanTotalToman,
  mehrValueToman,
  salaryPurchasingPower,
} from '../../src/tools/calc';
import { CPI, cpiIndex, cpiRatio, cpiYears, isEstimatedYear } from '../../src/tools/cpi';
import { fa, faYear, formatPercent, formatToman, parseInteger, parseNumber } from '../../src/tools/number';
import { parseRatesPayload, rateToman } from '../../src/tools/rates';

/**
 * One describe per formula, and every describe carries the four edges that matter: zero, a
 * negative, a number too large to be a number, and an invalid input. The expected values are
 * computed independently (Python, at the time the table was entered) rather than by re-running
 * the implementation.
 */

describe('Persian number input/output', () => {
  it('reads Persian, Arabic-Indic and ASCII digits with the separators people type', () => {
    expect(parseNumber('۱۱۰')).toBe(110);
    expect(parseNumber('۱٬۲۳۴')).toBe(1234);
    expect(parseNumber('۱۲,۳۴۵')).toBe(12345);
    expect(parseNumber('  ۸٫۱۳۳  ')).toBeCloseTo(8.133, 10);
    expect(parseNumber('8.133')).toBeCloseTo(8.133, 10);
    expect(parseNumber('٤٢')).toBe(42);
    expect(parseNumber('-5')).toBe(-5);
  });

  it('refuses instead of repairing a malformed figure', () => {
    expect(parseNumber('')).toBeNull();
    expect(parseNumber('   ')).toBeNull();
    expect(parseNumber('abc')).toBeNull();
    expect(parseNumber('1.2.3')).toBeNull();
    expect(parseNumber('1e5')).toBeNull();
    expect(parseNumber('Infinity')).toBeNull();
    expect(parseNumber('9'.repeat(400))).toBeNull();
    expect(parseInteger('۵٫۵')).toBeNull();
    expect(parseInteger('۵')).toBe(5);
  });

  it('prints Persian digits, grouping and a real minus, never NaN', () => {
    expect(fa(1234.5, 1)).toBe('۱,۲۳۴٫۵');
    expect(fa(-5)).toBe('−۵');
    expect(fa(Number.POSITIVE_INFINITY)).toBe('—');
    expect(formatToman(2_000_000)).toBe('۲,۰۰۰,۰۰۰ تومان');
    expect(fa(1e21)).not.toContain('e');
    expect(formatPercent(12.34)).toBe('۱۲٫۳٪');
    // A year is not an amount: no grouping, or 1405 reads as one thousand four hundred five.
    expect(faYear(1405)).toBe('۱۴۰۵');
    expect(faYear(1380)).toBe('۱۳۸۰');
  });
});

describe('CPI table', () => {
  it('covers 1380–1405 and anchors 1400 at 100', () => {
    expect(CPI).toHaveLength(26);
    expect(cpiYears()[0]).toBe(1380);
    expect(cpiYears().at(-1)).toBe(1405);
    expect(cpiIndex(1400)).toBe(100);
    expect(cpiIndex(1405)).toBeCloseTo(589.06, 2);
  });

  it('marks only the two estimated years', () => {
    expect(isEstimatedYear(1403)).toBe(false);
    expect(isEstimatedYear(1404)).toBe(true);
    expect(isEstimatedYear(1405)).toBe(true);
  });

  it('returns null for a year outside the table or a fractional year', () => {
    expect(cpiIndex(1379)).toBeNull();
    expect(cpiIndex(1406)).toBeNull();
    expect(cpiIndex(1400.5)).toBeNull();
    expect(cpiRatio(1400, 1406)).toBeNull();
  });
});

describe('mehrValueToman', () => {
  it('multiplies coins by the price of one coin', () => {
    expect(mehrValueToman(110, 18_000_000)).toBe(1_980_000_000);
    expect(mehrValueToman(1, 1)).toBe(1);
  });

  it('accepts zero and stays finite for a very large count', () => {
    expect(mehrValueToman(0, 18_000_000)).toBe(0);
    expect(mehrValueToman(1e9, 1e9)).toBe(1e18);
  });

  it('refuses negative, non-finite or overflowing inputs', () => {
    expect(mehrValueToman(-1, 1_000)).toBeNull();
    expect(mehrValueToman(1, -1_000)).toBeNull();
    expect(mehrValueToman(Number.NaN, 1_000)).toBeNull();
    expect(mehrValueToman(1e308, 2)).toBeNull();
  });
});

describe('inflationEstimate / purchasing power', () => {
  it('turns 10m in 1400 into its 1405 purchasing power and nominal equivalent', () => {
    const result = inflationEstimate(10_000_000, 1400, 1405)!;
    expect(result.equivalentToman).toBeCloseTo(58_906_000, 0);
    expect(result.purchasingPowerToman).toBeCloseTo(1_697_619.94, 0);
    expect(result.cumulativeInflationPercent).toBeCloseTo(489.06, 2);
    expect(result.annualizedInflationPercent).toBeCloseTo(42.5712, 3);
  });

  it('handles the same year without inventing an annual rate', () => {
    const result = inflationEstimate(1_000_000, 1400, 1400)!;
    expect(result.equivalentToman).toBe(1_000_000);
    expect(result.purchasingPowerToman).toBe(1_000_000);
    expect(result.cumulativeInflationPercent).toBe(0);
    expect(result.annualizedInflationPercent).toBeNull();
  });

  it('works backwards (deflation in the data is a real result)', () => {
    const result = inflationEstimate(1_000_000, 1405, 1400)!;
    expect(result.cumulativeInflationPercent).toBeCloseTo(-83.0238, 2);
    expect(result.annualizedInflationPercent).toBeCloseTo(-29.8596, 3);
  });

  it('accepts zero but refuses negative/unknown/overflowing input', () => {
    expect(inflationEstimate(0, 1400, 1405)!.purchasingPowerToman).toBe(0);
    expect(inflationEstimate(-1, 1400, 1405)).toBeNull();
    expect(inflationEstimate(1_000_000, 1379, 1405)).toBeNull();
    expect(inflationEstimate(1e308, 1400, 1405)).toBeNull();
  });
});

describe('salaryPurchasingPower', () => {
  it('measures a fixed 1400 salary against 1405 prices', () => {
    const result = salaryPurchasingPower(10_000_000, 1400, 1405)!;
    expect(result.purchasingPowerToman).toBeCloseTo(1_697_619.94, 0);
    expect(result.requiredSalaryToman).toBeCloseTo(58_906_000, 0);
    expect(result.lossPercent).toBeCloseTo(83.0238, 2);
    expect(result.cumulativeInflationPercent).toBeCloseTo(489.06, 2);
  });

  it('has no loss when the years are the same', () => {
    expect(salaryPurchasingPower(10_000_000, 1400, 1400)!.lossPercent).toBe(0);
  });

  it('handles a zero salary without dividing by it', () => {
    const result = salaryPurchasingPower(0, 1400, 1405)!;
    expect(result.purchasingPowerToman).toBe(0);
    expect(result.requiredSalaryToman).toBe(0);
    expect(result.lossPercent).toBe(0);
  });

  it('refuses a negative or invalid salary', () => {
    expect(salaryPurchasingPower(-1, 1400, 1405)).toBeNull();
    expect(salaryPurchasingPower(Number.NaN, 1400, 1405)).toBeNull();
    expect(salaryPurchasingPower(10_000_000, 1400, 1500)).toBeNull();
  });
});

describe('delayDamagesToman', () => {
  it('awards the CPI change on a 1400 debt paid in 1405', () => {
    const result = delayDamagesToman(10_000_000, 1400, 1405)!;
    expect(result.damagesToman).toBeCloseTo(48_906_000, 0);
    expect(result.totalToman).toBeCloseTo(58_906_000, 0);
    expect(result.cpiFrom).toBe(100);
    expect(result.cpiTo).toBeCloseTo(589.06, 2);
  });

  it('returns zero when nothing is late, and never a negative damage', () => {
    expect(delayDamagesToman(10_000_000, 1400, 1400)!.damagesToman).toBe(0);
    expect(delayDamagesToman(10_000_000, 1405, 1400)!.damagesToman).toBe(0);
  });

  it('refuses negative principal, unknown years and overflow', () => {
    expect(delayDamagesToman(-1, 1400, 1405)).toBeNull();
    expect(delayDamagesToman(1_000_000, 1200, 1405)).toBeNull();
    expect(delayDamagesToman(1e308, 1400, 1405)).toBeNull();
  });
});

describe('intrinsicGoldValueToman / coinBubblePercent', () => {
  it('converts fine gold into grams of the quoted purity', () => {
    // 8 g at 900‰, with the price quoted for 750‰: 8 × 1.2 × 1,000,000.
    expect(intrinsicGoldValueToman(8, 900, 1_000_000)).toBe(9_600_000);
    expect(intrinsicGoldValueToman(8, 750, 1_000_000)).toBe(8_000_000);
    expect(intrinsicGoldValueToman(0, 900, 1_000_000)).toBe(0);
  });

  it('computes a positive, negative and zero bubble', () => {
    expect(coinBubblePercent(12_000_000, 9_600_000)).toBe(25);
    expect(coinBubblePercent(9_000_000, 9_600_000)).toBe(-6.25);
    expect(coinBubblePercent(9_600_000, 9_600_000)).toBe(0);
    expect(coinBubblePercent(0, 9_600_000)).toBe(-100);
  });

  it('refuses a zero intrinsic, bad purity or negative price', () => {
    expect(intrinsicGoldValueToman(8, 0, 1_000_000)).toBeNull();
    expect(intrinsicGoldValueToman(8, 1001, 1_000_000)).toBeNull();
    expect(intrinsicGoldValueToman(8, 900, -1)).toBeNull();
    expect(intrinsicGoldValueToman(-1, 900, 1_000_000)).toBeNull();
    expect(coinBubblePercent(-1, 9_600_000)).toBeNull();
    expect(coinBubblePercent(1_000, 0)).toBeNull();
    expect(coinBubblePercent(1_000, -1)).toBeNull();
    expect(intrinsicGoldValueToman(1e308, 1000, 1e308, 1)).toBeNull();
  });

  it('returns all parts together from coinBubble', () => {
    const result = coinBubble(12_000_000, 8, 900, 1_000_000)!;
    expect(result.intrinsicToman).toBe(9_600_000);
    expect(result.bubblePercent).toBe(25);
    expect(result.premiumToman).toBe(2_400_000);
    expect(coinBubble(12_000_000, 0, 900, 1_000_000)).toBeNull();
  });
});

describe('habitAnnualCostToman / habitTotalCostToman', () => {
  it('annualises the daily cost and compounds it forward', () => {
    expect(habitAnnualCostToman(100_000, 365, 0, 0)).toBe(36_500_000);
    expect(habitAnnualCostToman(100_000, 365, 40, 3)).toBeCloseTo(100_156_000, 0);
  });

  it('sums a geometric series, and a simple product when inflation is zero', () => {
    expect(habitTotalCostToman(100_000, 365, 0, 5)).toBe(182_500_000);
    expect(habitTotalCostToman(100_000, 365, 40, 5)).toBeCloseTo(399_514_400, 0);
    expect(habitTotalCostToman(100_000, 365, 40, 10)).toBeCloseTo(2_548_198_726.66, 0);
    expect(habitTotalCostToman(100_000, 365, -10, 5)).toBeCloseTo(149_471_150, 0);
  });

  it('accepts zero cost, but refuses zero days, a fractional year or -100%', () => {
    expect(habitAnnualCostToman(0, 365, 40, 2)).toBe(0);
    expect(habitAnnualCostToman(100_000, 0, 40, 0)).toBeNull();
    expect(habitAnnualCostToman(100_000, 367, 40, 0)).toBeNull();
    expect(habitAnnualCostToman(100_000, 365, 40, -1)).toBeNull();
    expect(habitAnnualCostToman(100_000, 365, 40, 1.5)).toBeNull();
    expect(habitAnnualCostToman(-1, 365, 40, 0)).toBeNull();
    expect(habitAnnualCostToman(100_000, 365, -100, 0)).toBeNull();
    expect(habitAnnualCostToman(100_000, 365, 10_001, 0)).toBeNull();
    expect(habitTotalCostToman(100_000, 365, 40, 0)).toBeNull();
    expect(habitTotalCostToman(100_000, 365, 10_000, 1_000)).toBeNull();
    expect(habitAnnualCostToman(1e308, 365, 0, 1)).toBeNull();
  });

  it('reports the first year, last year and total in one object', () => {
    const result = habitEstimate(100_000, 365, 40, 5)!;
    expect(result.firstYearToman).toBe(36_500_000);
    expect(result.finalYearToman).toBeCloseTo(140_218_400, 0);
    expect(result.totalToman).toBeCloseTo(399_514_400, 0);
    expect(habitEstimate(100_000, 365, 40, 0)).toBeNull();
  });
});

describe('loanInstallmentToman / loanTotalToman / loanEstimate', () => {
  it('computes the standard PMT for 100m at 23% over 60 months', () => {
    expect(loanInstallmentToman(100_000_000, 23, 60)).toBeCloseTo(2_819_047.11, 1);
    expect(loanTotalToman(100_000_000, 23, 60)).toBeCloseTo(169_142_826.54, 0);
  });

  it('falls back to principal/months at a zero rate', () => {
    expect(loanInstallmentToman(100_000_000, 0, 12)).toBeCloseTo(8_333_333.33, 1);
    expect(loanTotalToman(100_000_000, 0, 12)).toBeCloseTo(100_000_000, 0);
    expect(loanEstimate(100_000_000, 0, 12)!.interestToman).toBeCloseTo(0, 0);
  });

  it('handles a zero principal and very large but finite inputs', () => {
    expect(loanInstallmentToman(0, 23, 60)).toBe(0);
    const huge = loanInstallmentToman(1e15, 1000, 1200)!;
    expect(Number.isFinite(huge)).toBe(true);
    expect((loanTotalToman(1e15, 1000, 1200) ?? 0) / 1e18).toBeCloseTo(1, 6);
  });

  it('refuses a negative rate, bad months and overflow', () => {
    expect(loanInstallmentToman(100_000_000, -1, 12)).toBeNull();
    expect(loanInstallmentToman(100_000_000, 23, 0)).toBeNull();
    expect(loanInstallmentToman(100_000_000, 23, 1.5)).toBeNull();
    expect(loanInstallmentToman(100_000_000, 23, 1201)).toBeNull();
    expect(loanInstallmentToman(100_000_000, 1001, 12)).toBeNull();
    expect(loanInstallmentToman(-1, 23, 12)).toBeNull();
    expect(loanTotalToman(1e308, 1000, 1200)).toBeNull();
    expect(loanEstimate(1e308, 1000, 1200)).toBeNull();
  });

  it('reports total and interest from the same installment', () => {
    const result = loanEstimate(100_000_000, 23, 60)!;
    expect(result.installmentToman).toBeCloseTo(2_819_047.11, 1);
    expect(result.totalToman).toBeCloseTo(169_142_826.54, 0);
    expect(result.interestToman).toBeCloseTo(69_142_826.54, 0);
    expect(result.monthlyRate).toBeCloseTo(23 / 1200, 12);
  });
});

describe('parseRatesPayload', () => {
  it('keeps only finite positive Toman prices', () => {
    const snapshot = parseRatesPayload({
      updatedAt: 1_700_000_000_000,
      toman: { usd: 100_000, gold18: 18_000_000, zero: 0, bad: 'x', missing: null },
    })!;
    expect(snapshot.updatedAt).toBe(1_700_000_000_000);
    expect(snapshot.toman).toEqual({ usd: 100_000, gold18: 18_000_000 });
    expect(rateToman(snapshot, 'gold18')).toBe(18_000_000);
    expect(rateToman(snapshot, 'zero')).toBeNull();
    expect(rateToman(null, 'gold18')).toBeNull();
  });

  it('refuses a payload with no usable price', () => {
    expect(parseRatesPayload(null)).toBeNull();
    expect(parseRatesPayload([])).toBeNull();
    expect(parseRatesPayload({})).toBeNull();
    expect(parseRatesPayload({ toman: {} })).toBeNull();
    expect(parseRatesPayload({ toman: [] })).toBeNull();
    expect(parseRatesPayload({ toman: { usd: -1 } })).toBeNull();
    expect(parseRatesPayload({ toman: { usd: Number.NaN } })).toBeNull();
  });

  it('treats a missing timestamp as unknown rather than 1970', () => {
    expect(parseRatesPayload({ toman: { usd: 1 } })!.updatedAt).toBe(0);
  });
});
