/**
 * Port of Zheton Financial Calculators (pwa/src/tools/calc.ts, cpi.ts, coins.ts)
 */

// Annual Consumer Price Index (CPI) table (1380 - 1405)
export const CPI_BY_YEAR: Record<number, number> = {
  1380: 3.8,
  1381: 4.4,
  1382: 5.1,
  1383: 5.9,
  1384: 6.5,
  1385: 7.3,
  1386: 8.6,
  1387: 10.8,
  1388: 12.0,
  1389: 13.5,
  1390: 16.4,
  1391: 21.4,
  1392: 28.8,
  1393: 33.3,
  1394: 37.3,
  1395: 40.6,
  1396: 44.5,
  1397: 58.4,
  1398: 82.5,
  1399: 121.4,
  1400: 177.5,
  1401: 259.9,
  1402: 365.7,
  1403: 493.7,
  1404: 666.5,
  1405: 899.8,
};

export const COIN_SPECS = [
  { id: 'coin_emami', fa: 'سکه امامی', weightGram: 8.133, karat: 21.6 },
  { id: 'coin_bahar', fa: 'سکه بهار آزادی', weightGram: 8.133, karat: 21.6 },
  { id: 'coin_nim', fa: 'نیم‌سکه', weightGram: 4.0665, karat: 21.6 },
  { id: 'coin_rob', fa: 'ربع‌سکه', weightGram: 2.03225, karat: 21.6 },
  { id: 'coin_gerami', fa: 'سکه یک‌گرمی', weightGram: 1.01, karat: 21.6 },
];

/** 1. Gold Coin Bubble Calculator (حباب‌سنج سکه) */
export function calcCoinBubble(
  coinMarketPriceToman: number,
  gold18kPriceToman: number,
  weightGram = 8.133,
  karat = 21.6,
  mintingFeeToman = 50000
) {
  const intrinsicToman = Math.round(weightGram * (karat / 18) * gold18kPriceToman + mintingFeeToman);
  const bubbleToman = coinMarketPriceToman - intrinsicToman;
  const bubblePct = intrinsicToman > 0 ? (bubbleToman / intrinsicToman) * 100 : 0;
  return {
    intrinsicToman,
    bubbleToman,
    bubblePct: Number(bubblePct.toFixed(1)),
    statusFa: bubblePct > 18 ? 'حباب بالا (احتیاط در خرید)' : bubblePct > 10 ? 'حباب متعادل بازار' : 'حباب کم (ارزندگی بالا)',
  };
}

/** 2. Loan Installment PMT Calculator (محاسبه قسط وام) */
export function calcLoanPmt(
  principalToman: number,
  annualRatePct: number,
  months: number
) {
  if (months <= 0 || principalToman <= 0) {
    return { monthlyPayment: 0, totalPayment: 0, totalInterest: 0 };
  }
  const monthlyRate = annualRatePct / 100 / 12;
  let monthlyPayment = 0;
  if (monthlyRate === 0) {
    monthlyPayment = principalToman / months;
  } else {
    const factor = Math.pow(1 + monthlyRate, months);
    monthlyPayment = (principalToman * monthlyRate * factor) / (factor - 1);
  }
  const totalPayment = monthlyPayment * months;
  const totalInterest = totalPayment - principalToman;
  return {
    monthlyPayment: Math.round(monthlyPayment),
    totalPayment: Math.round(totalPayment),
    totalInterest: Math.round(totalInterest),
  };
}

/** 3. Inflation & Toman Purchasing Power (تورم و قدرت خرید) */
export function calcInflation(
  amountToman: number,
  fromYear: number,
  toYear: number
) {
  const fromCpi = CPI_BY_YEAR[fromYear] ?? 100;
  const toCpi = CPI_BY_YEAR[toYear] ?? 100;
  const ratio = toCpi / fromCpi;
  const equivalentToman = Math.round(amountToman * ratio);
  const cumulativeInflationPct = Number(((ratio - 1) * 100).toFixed(1));
  const purchasingPowerRetainedPct = Number(((1 / ratio) * 100).toFixed(1));
  return {
    equivalentToman,
    cumulativeInflationPct,
    purchasingPowerRetainedPct,
    ratio: Number(ratio.toFixed(2)),
  };
}

/** 4. Late Payment Penalty - Article 522 (خسارت تأخیر تادیه) */
export function calcTakhirTadieh(
  debtAmountToman: number,
  dueYear: number,
  payYear: number
) {
  const dueCpi = CPI_BY_YEAR[dueYear] ?? 100;
  const payCpi = CPI_BY_YEAR[payYear] ?? 100;
  const totalClaimToman = Math.round(debtAmountToman * (payCpi / dueCpi));
  const penaltyDamageToman = Math.max(0, totalClaimToman - debtAmountToman);
  return {
    totalClaimToman,
    penaltyDamageToman,
    dueCpi,
    payCpi,
  };
}

/** 5. Mehrieh Valuation (ارزش روز مهریه) */
export function calcMehrieh(
  coinCount: number,
  coinPriceToman: number,
  coinWeightGram = 8.133
) {
  const totalValueToman = Math.round(coinCount * coinPriceToman);
  const totalGoldGrams = Number((coinCount * coinWeightGram).toFixed(1));
  return {
    totalValueToman,
    totalGoldGrams,
  };
}

/** 6. Daily Habit Compound Cost (هزینه سالانه قهوه و سیگار با تورم) */
export function calcDailyHabit(
  dailyCostToman: number,
  years: number,
  annualInflationPct: number
) {
  let cumulativeTotal = 0;
  const yearlyBreakdown: { yearIndex: number; annualCost: number }[] = [];
  for (let y = 1; y <= years; y++) {
    const factor = Math.pow(1 + annualInflationPct / 100, y - 1);
    const annualCost = Math.round(dailyCostToman * 365 * factor);
    cumulativeTotal += annualCost;
    yearlyBreakdown.push({ yearIndex: y, annualCost });
  }
  return {
    firstYearCost: yearlyBreakdown[0]?.annualCost ?? 0,
    cumulativeTotal,
    yearlyBreakdown,
  };
}
