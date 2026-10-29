export type AssetKind = 'CASH' | 'FIAT' | 'CRYPTO' | 'GOLD' | 'SILVER' | 'COIN' | 'STOCK';

export interface AssetType {
  id: string;
  fa: string;
  kind: AssetKind;
  unitFa: string;
  dec?: number;
  en?: string;
  emoji?: string;
  iconUrl?: string;
}

export interface MarketQuote {
  id: string;
  fa: string;
  priceToman: number;
  change24hPct: number;
  sparkline: number[];
  unitFa: string;
  en: string;
  kind: AssetKind;
  coinWeightGram?: number;
  karat?: number;
}

export interface Holding {
  id: string;
  typeId: string;
  amount: number;
  label?: string;
  excluded?: boolean;
  archived?: boolean;
}

export interface BankAccount {
  id: string;
  bankKey: string;
  bankNameFa: string;
  pan: string; // 16-digit or masked
  sheba?: string; // IR...
  balanceToman: number;
  nickname: string;
  theme: string; // preset name or custom gradient
  updatedAt: number;
}

export type TxnDirection = 'DEBIT' | 'CREDIT';
export type NecessityTag = 'NEED' | 'WANT' | 'SAVINGS';

export interface Transaction {
  id: string;
  timestamp: number;
  amountToman: number;
  direction: TxnDirection;
  categoryFa: string;
  merchant: string;
  note?: string;
  accountId?: string;
  necessity: NecessityTag;
}

export type CommitmentKind = 'BNPL' | 'LOAN' | 'CHECK' | 'INSTALLMENT';

export interface Commitment {
  id: string;
  title: string;
  provider: string; // e.g. اسنپ‌پی، دیجی‌پی، تارا، بانک رسالت، چک صیادی
  kind: CommitmentKind;
  totalAmountToman: number;
  remainingAmountToman: number;
  installmentCount: number;
  paidInstallmentCount: number;
  nextDueTimestamp: number;
  note?: string;
  isSettled?: boolean;
}

export interface BudgetCap {
  categoryFa: string;
  monthlyLimitToman: number;
}

export type PaceStatusKey = 'CONSERVATIVE' | 'ON_TRACK' | 'ELEVATED_VELOCITY' | 'DEFICIT_RISK';

export interface PacingDecision {
  liquidCashToman: number;
  upcomingLiabilitiesToman: number;
  discretionaryBufferToman: number;
  safeSpendTodayToman: number;
  velocityIndex: number;
  paceStatus: {
    key: PaceStatusKey;
    labelFa: string;
    isWarning: boolean;
  };
  exhaustionCycleDay: number | null;
  daysUntilExhaustion: number | null;
  cycleDay: number;
  totalCycleDays: number;
  remainingDays: number;
}

export interface FinancialCycleConfig {
  anchorDay: number; // 1 to 31 (e.g. 25th for salary payday)
  targetMonthlyBudgetToman: number;
}
