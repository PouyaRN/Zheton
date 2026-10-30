import {
  BankAccount,
  BudgetCap,
  Commitment,
  FinancialCycleConfig,
  Holding,
  Transaction,
} from '../types';

const STORAGE_KEY = 'zheton_state_v2';
const LEGACY_STORAGE_KEY = 'chandtoman_state_v1';

export interface AppPersistedState {
  accounts: BankAccount[];
  holdings: Holding[];
  transactions: Transaction[];
  commitments: Commitment[];
  budgetCaps: BudgetCap[];
  cycleConfig: FinancialCycleConfig;
  privacyMode: boolean;
  hasSeenOnboarding: boolean;
}

export const INITIAL_STATE: AppPersistedState = {
  privacyMode: false,
  hasSeenOnboarding: false,
  cycleConfig: {
    anchorDay: 1,
    targetMonthlyBudgetToman: 0,
  },
  accounts: [],
  holdings: [],
  transactions: [],
  commitments: [],
  budgetCaps: [],
};

const now = Date.now();
const HOUR = 3600 * 1000;
const DAY = 24 * HOUR;

export function getDemoState(): AppPersistedState {
  return {
    privacyMode: false,
    hasSeenOnboarding: true,
    cycleConfig: {
      anchorDay: 25,
      targetMonthlyBudgetToman: 32000000,
    },
    accounts: [
      {
        id: 'acc_blu',
        bankKey: 'BLU',
        bankNameFa: 'بلو بانک',
        pan: '6219861945128834',
        sheba: 'IR420560611828005419283701',
        balanceToman: 18450000,
        nickname: 'حساب روزمره و خرج‌کرد',
        theme: 'default',
        updatedAt: now - 2 * HOUR,
      },
      {
        id: 'acc_mellat',
        bankKey: 'MELLAT',
        bankNameFa: 'بانک ملت',
        pan: '6104337891024415',
        sheba: 'IR880120020000008941256312',
        balanceToman: 34200000,
        nickname: 'حساب حقوق و پس‌انداز',
        theme: 'default',
        updatedAt: now - 18 * HOUR,
      },
    ],
    holdings: [
      {
        id: 'h_gold18',
        typeId: 'gold18',
        amount: 24.5,
        label: 'طلای آب‌شده و زینتی',
        excluded: false,
      },
      {
        id: 'h_emami',
        typeId: 'coin_emami',
        amount: 2,
        label: 'سکه امامی بانکی',
        excluded: false,
      },
    ],
    transactions: [
      {
        id: 'tx_1',
        timestamp: now - 1.5 * HOUR,
        amountToman: 285000,
        direction: 'DEBIT',
        categoryFa: 'رستوران و کافه',
        merchant: 'کافه لمیز',
        note: 'قهوه و کروسان صبحگاهی',
        accountId: 'acc_blu',
        necessity: 'WANT',
      },
      {
        id: 'tx_2',
        timestamp: now - 22 * HOUR,
        amountToman: 1890000,
        direction: 'DEBIT',
        categoryFa: 'خواربار',
        merchant: 'هایپراستار',
        note: 'خرید هفتگی منزل',
        accountId: 'acc_mellat',
        necessity: 'NEED',
      },
    ],
    commitments: [
      {
        id: 'cm_snapp',
        title: 'خرید اقساطی اسنپ‌پی',
        provider: 'اسنپ‌پی',
        kind: 'BNPL',
        totalAmountToman: 4000000,
        remainingAmountToman: 2000000,
        installmentCount: 4,
        paidInstallmentCount: 2,
        nextDueTimestamp: now + 5 * DAY,
        note: 'قسط سوم اسنپ‌پی',
        isSettled: false,
      },
    ],
    budgetCaps: [
      { categoryFa: 'خواربار', monthlyLimitToman: 9000000 },
      { categoryFa: 'رستوران و کافه', monthlyLimitToman: 3500000 },
    ],
  };
}

export function loadPersistedState(): AppPersistedState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      return {
        ...INITIAL_STATE,
        ...parsed,
      };
    }
    // Check legacy storage
    const legacy = localStorage.getItem(LEGACY_STORAGE_KEY);
    if (legacy) {
      const parsed = JSON.parse(legacy);
      // If user had existing data in legacy, migrate it and mark onboarding done
      return {
        ...INITIAL_STATE,
        ...parsed,
        hasSeenOnboarding: true,
      };
    }
    return INITIAL_STATE;
  } catch {
    return INITIAL_STATE;
  }
}

export function savePersistedState(state: AppPersistedState): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Ignore storage quota errors
  }
}

export function resetPersistedState(): AppPersistedState {
  try {
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(LEGACY_STORAGE_KEY);
  } catch {
    // Ignore
  }
  return INITIAL_STATE;
}
