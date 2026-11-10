import { PacingDecision, PaceStatusKey } from '../types';

/**
 * Port of SafeSpendEngine.kt
 * Pure financial decision engine tailored for Iranian spending patterns & work-week.
 */

export function getIranianDayOfWeek(date = new Date()): number {
  const jsDay = date.getDay();
  switch (jsDay) {
    case 6: return 1; // Saturday = Shanbeh
    case 0: return 2; // Sunday = Yekshanbeh
    case 1: return 3; // Monday = Doshanbeh
    case 2: return 4; // Tuesday = Seshanbeh
    case 3: return 5; // Wednesday = Chaharshanbeh
    case 4: return 6; // Thursday = Panjshanbeh
    case 5: return 7; // Friday = Jomeh
    default: return 1;
  }
}

export function dayOfWeekWeight(dow1to7: number): number {
  switch (dow1to7) {
    case 1:
    case 2:
    case 3:
    case 4:
      return 1.00;
    case 5:
      return 1.15;
    case 6:
      return 1.40;
    case 7:
      return 1.25;
    default:
      return 1.00;
  }
}

export function computePacing(
  liquidCashToman: number,
  upcomingLiabilitiesToman: number,
  cycleDay: number,
  totalCycleDays: number,
  currentDayOfWeek: number,
  actualSpentSoFarToman: number,
  targetMonthlyBudgetToman: number,
  recentDailyBurnToman: number
): PacingDecision {
  const safeCycleDay = Math.min(Math.max(1, cycleDay), totalCycleDays);
  const remainingDays = Math.max(1, totalCycleDays - safeCycleDay + 1);
  const discretionaryBuffer = Math.max(0, liquidCashToman - upcomingLiabilitiesToman);

  let totalRemainingWeight = 0;
  const todayWeight = dayOfWeekWeight(currentDayOfWeek);

  for (let offset = 0; offset < remainingDays; offset++) {
    const dow = ((currentDayOfWeek - 1 + offset) % 7) + 1;
    totalRemainingWeight += dayOfWeekWeight(dow);
  }

  let safeSpendToday = 0;
  if (totalRemainingWeight > 0 && discretionaryBuffer > 0) {
    safeSpendToday = Math.round((discretionaryBuffer / totalRemainingWeight) * todayWeight);
  }

  const cycleProgress = safeCycleDay / totalCycleDays;
  const expectedSpend = targetMonthlyBudgetToman * cycleProgress;
  const velocityIndex = expectedSpend > 0 ? (actualSpentSoFarToman / expectedSpend) : 1.0;

  const burnRate = Math.max(recentDailyBurnToman, safeSpendToday, 1);
  const daysUntilExhaustion = discretionaryBuffer > 0
    ? Math.floor(discretionaryBuffer / burnRate)
    : 0;
  const exhaustionCycleDay = safeCycleDay + daysUntilExhaustion <= totalCycleDays
    ? safeCycleDay + daysUntilExhaustion
    : null;

  let paceStatusKey: PaceStatusKey = 'ON_TRACK';
  let labelFa = 'در محدوده امن بودجه';
  let isWarning = false;

  if (discretionaryBuffer <= 0 || (daysUntilExhaustion < remainingDays && daysUntilExhaustion <= 5)) {
    paceStatusKey = 'DEFICIT_RISK';
    labelFa = 'خطر کسری قبل از حقوق';
    isWarning = true;
  } else if (velocityIndex > 1.25) {
    paceStatusKey = 'ELEVATED_VELOCITY';
    labelFa = 'شتاب بالای خرج‌کرد';
    isWarning = true;
  } else if (velocityIndex < 0.85) {
    paceStatusKey = 'CONSERVATIVE';
    labelFa = 'کنترل عالی (زیر سقف بودجه)';
    isWarning = false;
  }

  return {
    liquidCashToman,
    upcomingLiabilitiesToman,
    discretionaryBufferToman: discretionaryBuffer,
    safeSpendTodayToman: safeSpendToday,
    velocityIndex: Number(velocityIndex.toFixed(2)),
    paceStatus: {
      key: paceStatusKey,
      labelFa,
      isWarning,
    },
    exhaustionCycleDay,
    daysUntilExhaustion: daysUntilExhaustion < 100 ? daysUntilExhaustion : null,
    cycleDay: safeCycleDay,
    totalCycleDays,
    remainingDays,
  };
}
