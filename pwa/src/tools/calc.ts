/**
 * Every calculator's arithmetic, as pure functions.
 *
 * The rules this file holds itself to:
 * - No DOM, no fetch, no clock, no locale: a formula receives numbers and returns a number or
 *   `null`. That is what makes it testable without a browser.
 * - Invalid input is `null`, never a repaired guess. A negative price, a missing year or an
 *   overflow is refused rather than silently turned into zero.
 * - Money is Toman on both sides. Rial never enters this file.
 * - Results are unrounded. The caller rounds for display, once, so a chain of calculations does
 *   not accumulate the display's rounding.
 */

import { cpiIndex, cpiRatio } from './cpi';

function isNonNegative(value: number): boolean {
  return Number.isFinite(value) && value >= 0;
}

function isPositive(value: number): boolean {
  return Number.isFinite(value) && value > 0;
}

function finiteOrNull(value: number): number | null {
  return Number.isFinite(value) ? value : null;
}

// ───────────────────────────── مهریه ─────────────────────────────

/**
 * Value of a Mehrich: the number of coins times the price of one coin today.
 *
 * Assumptions: the Mehr is denominated in whole gold coins of one type, the price is the
 * current market price of that same coin in Toman, and no allowance is made for collectible
 * premium, coin wear, or the difference between buy and sell sides. This is an estimate of
 * replacement cost, not a court valuation.
 */
export function mehrValueToman(coinCount: number, coinPriceToman: number): number | null {
  if (!isNonNegative(coinCount) || !isNonNegative(coinPriceToman)) return null;
  return finiteOrNull(coinCount * coinPriceToman);
}

// ───────────────────── تورم / قدرت خرید ─────────────────────

export interface InflationEstimate {
  amountToman: number;
  fromYear: number;
  toYear: number;
  cpiFrom: number;
  cpiTo: number;
  /**
   * The nominal amount needed at `toYear` prices to buy what `amountToman` bought at
   * `fromYear` prices: `amount × CPI(to) / CPI(from)`.
   */
  equivalentToman: number;
  /**
   * What `amountToman` is worth in `toYear`'s prices, expressed in `fromYear` money:
   * `amount × CPI(from) / CPI(to)`. This is the "purchasing power" figure.
   */
  purchasingPowerToman: number;
  /** Cumulative inflation over the interval, in percent (489.06 = 489.06%). */
  cumulativeInflationPercent: number;
  /** Geometric mean annual inflation, in percent; null for a same-year interval. */
  annualizedInflationPercent: number | null;
}

/**
 * Purchasing power and inflation over a range of Persian years.
 *
 * Assumptions: both years exist in the built-in annual-average CPI table; the basket behind the
 * index is the one the household buys (it is an average, so a household that spends
 * differently will see a different number); and prices are compared between annual averages,
 * not between two specific days.
 */
export function inflationEstimate(
  amountToman: number,
  fromYear: number,
  toYear: number,
): InflationEstimate | null {
  if (!isNonNegative(amountToman)) return null;
  const ratio = cpiRatio(fromYear, toYear);
  const cpiFrom = cpiIndex(fromYear);
  const cpiTo = cpiIndex(toYear);
  if (ratio == null || cpiFrom == null || cpiTo == null) return null;

  const equivalentToman = amountToman * ratio;
  const purchasingPowerToman = amountToman / ratio;
  if (!Number.isFinite(equivalentToman) || !Number.isFinite(purchasingPowerToman)) return null;

  const years = Math.abs(toYear - fromYear);
  const annualized = years === 0 ? null : (Math.pow(ratio, 1 / years) - 1) * 100;

  return {
    amountToman,
    fromYear,
    toYear,
    cpiFrom,
    cpiTo,
    equivalentToman,
    purchasingPowerToman,
    cumulativeInflationPercent: (ratio - 1) * 100,
    annualizedInflationPercent:
      annualized != null && Number.isFinite(annualized) ? annualized : null,
  };
}

// ───────────────────── قدرت خرید حقوق ─────────────────────

export interface SalaryEstimate {
  salaryToman: number;
  fromYear: number;
  toYear: number;
  /** What that salary buys at `toYear` prices, in `fromYear` Tomans. */
  purchasingPowerToman: number;
  /** The salary `toYear` would need in order to preserve the original purchasing power. */
  requiredSalaryToman: number;
  /** Share of purchasing power lost, in percent. Negative means a real gain. */
  lossPercent: number;
  cumulativeInflationPercent: number;
}

/**
 * A salary's purchasing power between two years.
 *
 * Assumptions: the salary is fixed in nominal Toman over the whole interval (no raises), it is
 * spent on the same CPI basket, and it is compared between annual averages. A raise given
 * inside the interval is not modelled; enter the final salary and compare with the year it was
 * last changed if that is the question.
 */
export function salaryPurchasingPower(
  salaryToman: number,
  fromYear: number,
  toYear: number,
): SalaryEstimate | null {
  if (!isNonNegative(salaryToman)) return null;
  const inflation = inflationEstimate(salaryToman, fromYear, toYear);
  if (inflation == null) return null;
  return {
    salaryToman,
    fromYear,
    toYear,
    purchasingPowerToman: inflation.purchasingPowerToman,
    requiredSalaryToman: inflation.equivalentToman,
    // A zero salary has no percentage to lose; dividing by it would print NaN.
    lossPercent:
      salaryToman === 0 ? 0 : (1 - inflation.purchasingPowerToman / salaryToman) * 100,
    cumulativeInflationPercent: inflation.cumulativeInflationPercent,
  };
}

// ───────────────────── تأخیر تادیه ─────────────────────

export interface DelayEstimate {
  principalToman: number;
  fromYear: number;
  toYear: number;
  cpiFrom: number;
  cpiTo: number;
  /** Inflation compensation, floored at zero. */
  damagesToman: number;
  /** Principal plus compensation. */
  totalToman: number;
  cumulativeInflationPercent: number;
}

/**
 * An estimate of damages for late payment (خسارت تأخیر تأدیه), linked to CPI.
 *
 * Legal assumption, stated in the UI as well: Article 522 of the Iranian Civil Procedure Code
 * (مادهٔ ۵۲۲ قانون آیین دادرسی مدنی) lets the court award compensation for the fall in the
 * value of money, measured by the official inflation index, from the date payment was due to
 * the date it was made. This tool applies that idea with the annual-average CPI table and does
 * not decide anything itself. It excludes contractual penalties, lawyer/court costs, the
 * parties' agreed interest, and any special bank/credit-institution rule. When the index falls
 * (deflation) the compensation is zero, not negative. A court's number can differ.
 *
 * Assumption on dates: each year is the annual average, so a debt due mid-year is approximated
 * to that year's average index rather than to a daily index.
 */
export function delayDamagesToman(
  principalToman: number,
  fromYear: number,
  toYear: number,
): DelayEstimate | null {
  if (!isNonNegative(principalToman)) return null;
  const inflation = inflationEstimate(principalToman, fromYear, toYear);
  if (inflation == null) return null;
  const totalToman = principalToman + Math.max(0, inflation.equivalentToman - principalToman);
  return {
    principalToman,
    fromYear,
    toYear,
    cpiFrom: inflation.cpiFrom,
    cpiTo: inflation.cpiTo,
    damagesToman: Math.max(0, inflation.equivalentToman - principalToman),
    totalToman,
    cumulativeInflationPercent: inflation.cumulativeInflationPercent,
  };
}

// ───────────────────────── حباب سکه ─────────────────────────

export const GOLD_PURITY_PERMILLE_18K = 750;

export interface CoinBubbleEstimate {
  coinPriceToman: number;
  weightGrams: number;
  purityPermille: number;
  goldPurityPermille: number;
  goldPricePerGramToman: number;
  /** Melt value of the gold in the coin, in Toman. */
  intrinsicToman: number;
  /** (coin price − intrinsic) / intrinsic × 100. Negative when the coin trades below melt. */
  bubblePercent: number;
  /** Coin price minus intrinsic value, in Toman. Negative below melt. */
  premiumToman: number;
}

/**
 * Melt value of the gold in a coin.
 *
 * `goldPricePerGramToman` is the price of one gram at `goldPurityPermille` (750 = 18k, the
 * quote the rates Worker publishes as `gold18`). The coin's own purity is a permille figure
 * (900 for the 8.133 g Emami/Bahar family). The formula converts the coin's fine-gold content
 * into grams of the quoted purity, then multiplies by that price:
 *
 *   intrinsic = weight × (coinPurity / quotedPurity) × pricePerGram
 *
 * Assumptions: the quoted price is for the same purity passed in, the weight is the standard
 * coin weight and is not adjusted for wear, and no making charge, tax or collectible premium is
 * included. This is the metal value, not a valuation of the coin as a coin.
 */
export function intrinsicGoldValueToman(
  weightGrams: number,
  purityPermille: number,
  goldPricePerGramToman: number,
  goldPurityPermille: number = GOLD_PURITY_PERMILLE_18K,
): number | null {
  if (!isNonNegative(weightGrams) || !isNonNegative(goldPricePerGramToman)) return null;
  if (!isPositive(purityPermille) || purityPermille > 1000) return null;
  if (!isPositive(goldPurityPermille) || goldPurityPermille > 1000) return null;
  return finiteOrNull(weightGrams * (purityPermille / goldPurityPermille) * goldPricePerGramToman);
}

/**
 * Coin bubble: how far the coin's market price sits above its melt value.
 *
 * Assumptions: both prices are in Toman at the same moment, the intrinsic value is computed
 * with `intrinsicGoldValueToman`, and a negative result is a real discount rather than an
 * error. A zero or negative intrinsic value cannot define a percentage, so it returns null.
 */
export function coinBubblePercent(
  coinPriceToman: number,
  intrinsicToman: number,
): number | null {
  if (!isNonNegative(coinPriceToman) || !isPositive(intrinsicToman)) return null;
  return finiteOrNull(((coinPriceToman - intrinsicToman) / intrinsicToman) * 100);
}

/** Intrinsic value, bubble percentage and premium for one coin, or null if any input is bad. */
export function coinBubble(
  coinPriceToman: number,
  weightGrams: number,
  purityPermille: number,
  goldPricePerGramToman: number,
  goldPurityPermille: number = GOLD_PURITY_PERMILLE_18K,
): CoinBubbleEstimate | null {
  const intrinsicToman = intrinsicGoldValueToman(
    weightGrams,
    purityPermille,
    goldPricePerGramToman,
    goldPurityPermille,
  );
  if (intrinsicToman == null) return null;
  const bubblePercent = coinBubblePercent(coinPriceToman, intrinsicToman);
  if (bubblePercent == null) return null;
  return {
    coinPriceToman,
    weightGrams,
    purityPermille,
    goldPurityPermille,
    goldPricePerGramToman,
    intrinsicToman,
    bubblePercent,
    premiumToman: coinPriceToman - intrinsicToman,
  };
}

// ─────────────────── هزینهٔ قهوه / سیگار ───────────────────

export interface HabitEstimate {
  dailyCostToman: number;
  daysPerYear: number;
  inflationPercent: number;
  years: number;
  /** Cost of the habit over the first year, at today's daily price. */
  firstYearToman: number;
  /** Cost over the last year of the interval, after compounding. */
  finalYearToman: number;
  /** Sum of all yearly costs over the interval. */
  totalToman: number;
}

function validHabitInputs(
  dailyCostToman: number,
  daysPerYear: number,
  inflationPercent: number,
): boolean {
  return (
    isNonNegative(dailyCostToman) &&
    Number.isFinite(daysPerYear) &&
    daysPerYear > 0 &&
    daysPerYear <= 366 &&
    Number.isFinite(inflationPercent) &&
    inflationPercent > -100 &&
    inflationPercent <= 10_000
  );
}

/**
 * The cost of one year of a daily habit, `yearsAhead` years from now.
 *
 * Assumptions: the daily price is today's price, the habit happens every day of the year
 * (`daysPerYear` defaults to 365), and the whole price rises once a year by the selected
 * inflation rate, compounded annually. No substitution, no quitting, no investment return —
 * this is the nominal cash cost, not the opportunity cost of the money.
 */
export function habitAnnualCostToman(
  dailyCostToman: number,
  daysPerYear = 365,
  inflationPercent = 0,
  yearsAhead = 0,
): number | null {
  if (!validHabitInputs(dailyCostToman, daysPerYear, inflationPercent)) return null;
  if (!Number.isInteger(yearsAhead) || yearsAhead < 0) return null;
  const first = dailyCostToman * daysPerYear;
  const cost = first * Math.pow(1 + inflationPercent / 100, yearsAhead);
  return finiteOrNull(cost);
}

/**
 * Total nominal cost of the habit over `years` years, with annual compounding.
 *
 * Assumptions: same as `habitAnnualCostToman`; the first year is priced today and each
 * subsequent year is priced at the previous year's price plus the inflation rate. The sum is
 * the closed-form geometric series, not a yearly loop, so a long horizon stays exact.
 */
export function habitTotalCostToman(
  dailyCostToman: number,
  daysPerYear = 365,
  inflationPercent = 0,
  years = 1,
): number | null {
  if (!validHabitInputs(dailyCostToman, daysPerYear, inflationPercent)) return null;
  if (!Number.isInteger(years) || years < 1 || years > 1000) return null;
  const first = dailyCostToman * daysPerYear;
  const rate = inflationPercent / 100;
  const total =
    Math.abs(rate) < 1e-12
      ? first * years
      : first * (Math.pow(1 + rate, years) - 1) / rate;
  return finiteOrNull(total);
}

/** First-year cost, final-year cost and interval total, or null if any input is bad. */
export function habitEstimate(
  dailyCostToman: number,
  daysPerYear: number,
  inflationPercent: number,
  years: number,
): HabitEstimate | null {
  if (!Number.isInteger(years) || years < 1 || years > 1000) return null;
  const firstYearToman = habitAnnualCostToman(dailyCostToman, daysPerYear, inflationPercent, 0);
  const finalYearToman = habitAnnualCostToman(
    dailyCostToman,
    daysPerYear,
    inflationPercent,
    years - 1,
  );
  const totalToman = habitTotalCostToman(dailyCostToman, daysPerYear, inflationPercent, years);
  if (firstYearToman == null || finalYearToman == null || totalToman == null) return null;
  return {
    dailyCostToman,
    daysPerYear,
    inflationPercent,
    years,
    firstYearToman,
    finalYearToman,
    totalToman,
  };
}

// ───────────────────────── قسط وام ─────────────────────────

export interface LoanEstimate {
  principalToman: number;
  annualRatePercent: number;
  months: number;
  /** Monthly rate as a fraction (23% a year → 0.019166…). */
  monthlyRate: number;
  installmentToman: number;
  totalToman: number;
  interestToman: number;
}

/**
 * Equal monthly installment (PMT) for an amortizing loan.
 *
 * Assumptions: a fixed annual nominal rate compounded monthly, equal payments at the end of
 * each month, no fees, insurance, taxes, grace period or balloon payment. `months` is the
 * number of payments. A zero rate is the simple `principal / months`; a negative rate is
 * refused because it is not a loan this app is modelling. This is the standard PMT formula:
 *
 *   r = annualRate / 1200
 *   PMT = P × r / (1 − (1 + r)^(−n))
 */
export function loanInstallmentToman(
  principalToman: number,
  annualRatePercent: number,
  months: number,
): number | null {
  if (!isNonNegative(principalToman)) return null;
  if (!Number.isFinite(annualRatePercent) || annualRatePercent < 0 || annualRatePercent > 1000) {
    return null;
  }
  if (!Number.isInteger(months) || months < 1 || months > 1200) return null;

  const rate = annualRatePercent / 1200;
  if (rate === 0) return finiteOrNull(principalToman / months);

  const installment = (principalToman * rate) / (1 - Math.pow(1 + rate, -months));
  return finiteOrNull(installment);
}

/**
 * Total of all installments. Assumes the installment returned by `loanInstallmentToman` and no
 * prepayment or rate change over the life of the loan.
 */
export function loanTotalToman(
  principalToman: number,
  annualRatePercent: number,
  months: number,
): number | null {
  const installment = loanInstallmentToman(principalToman, annualRatePercent, months);
  if (installment == null) return null;
  return finiteOrNull(installment * months);
}

/** Installment, total repayment and total interest, or null if any input is bad. */
export function loanEstimate(
  principalToman: number,
  annualRatePercent: number,
  months: number,
): LoanEstimate | null {
  const installmentToman = loanInstallmentToman(principalToman, annualRatePercent, months);
  const totalToman = loanTotalToman(principalToman, annualRatePercent, months);
  if (installmentToman == null || totalToman == null) return null;
  return {
    principalToman,
    annualRatePercent,
    months,
    monthlyRate: annualRatePercent / 1200,
    installmentToman,
    totalToman,
    interestToman: Math.max(0, totalToman - principalToman),
  };
}
