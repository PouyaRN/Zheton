import {
  BankAccount,
  BudgetCap,
  Commitment,
  FinancialCycleConfig,
  Holding,
  Transaction,
} from '../types';

const STORAGE_KEY = 'chandtoman_state_v1';

export interface AppPersistedState {
  accounts: BankAccount[];
  holdings: Holding[];
  transactions: Transaction[];
  commitments: Commitment[];
  budgetCaps: BudgetCap[];
  cycleConfig: FinancialCycleConfig;
  privacyMode: boolean;
}

const now = Date.now();
const HOUR = 3600 * 1000;
const DAY = 24 * HOUR;

export const INITIAL_STATE: AppPersistedState = {
  privacyMode: false,
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
    {
      id: 'acc_saman',
      bankKey: 'SAMAN',
      bankNameFa: 'بانک سامان',
      pan: '6219861034912207',
      sheba: 'IR190560081180002291400504',
      balanceToman: 7850000,
      nickname: 'کارت اضطراری',
      theme: 'obsidian',
      updatedAt: now - 4 * DAY,
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
    {
      id: 'h_usdt',
      typeId: 'usdt',
      amount: 650,
      label: 'تتر کیف پول شخصی',
      excluded: false,
    },
    {
      id: 'h_ayar',
      typeId: 'etf_ayar',
      amount: 2500,
      label: 'صندوق طلای عیار (کد بورسی)',
      excluded: false,
    },
    {
      id: 'h_usd',
      typeId: 'usd',
      amount: 400,
      label: 'اسکناس دلار خانگی',
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
      merchant: 'کافه لمیز - ولیعصر',
      note: 'قهوه و کروسان صبحگاهی',
      accountId: 'acc_blu',
      necessity: 'WANT',
    },
    {
      id: 'tx_2',
      timestamp: now - 5 * HOUR,
      amountToman: 145000,
      direction: 'DEBIT',
      categoryFa: 'حمل و نقل',
      merchant: 'اسنپ (Snapp)',
      note: 'مسیر شرکت به خانه',
      accountId: 'acc_blu',
      necessity: 'NEED',
    },
    {
      id: 'tx_3',
      timestamp: now - 22 * HOUR,
      amountToman: 1890000,
      direction: 'DEBIT',
      categoryFa: 'خواربار',
      merchant: 'هایپراستار باکری',
      note: 'خرید هفتگی منزل و پروتئین',
      accountId: 'acc_mellat',
      necessity: 'NEED',
    },
    {
      id: 'tx_4',
      timestamp: now - 2 * DAY,
      amountToman: 740000,
      direction: 'DEBIT',
      categoryFa: 'خرید',
      merchant: 'دیجی‌کالا',
      note: 'لوازم جانبی موبایل',
      accountId: 'acc_blu',
      necessity: 'WANT',
    },
    {
      id: 'tx_5',
      timestamp: now - 3 * DAY,
      amountToman: 420000,
      direction: 'DEBIT',
      categoryFa: 'قبض‌ها',
      merchant: 'همراه اول و اینترنت فیبرنوری',
      note: 'بسته ماهانه اینترنت',
      accountId: 'acc_blu',
      necessity: 'NEED',
    },
    {
      id: 'tx_6',
      timestamp: now - 4 * DAY,
      amountToman: 5000000,
      direction: 'DEBIT',
      categoryFa: 'پس‌انداز و سرمایه',
      merchant: 'کارگزاری مفید - خرید صندوق عیار',
      note: 'تبدیل نقدینگی راکد به طلا',
      accountId: 'acc_mellat',
      necessity: 'SAVINGS',
    },
    {
      id: 'tx_7',
      timestamp: now - 6 * DAY,
      amountToman: 42000000,
      direction: 'CREDIT',
      categoryFa: 'درآمد و حقوق',
      merchant: 'واریز حقوق و مزایا',
      note: 'حقوق ماه جاری',
      accountId: 'acc_mellat',
      necessity: 'SAVINGS',
    },
  ],
  commitments: [
    {
      id: 'cm_snapp',
      title: 'خرید اقساطی هدفون و لوازم دیجیتال',
      provider: 'اسنپ‌پی (SnappPay)',
      kind: 'BNPL',
      totalAmountToman: 7200000,
      remainingAmountToman: 3600000,
      installmentCount: 4,
      paidInstallmentCount: 2,
      nextDueTimestamp: now + 3 * DAY,
      note: 'قسط سوم آخر ماه سررسید می‌شود',
      isSettled: false,
    },
    {
      id: 'cm_resalat',
      title: 'وام قرض‌الحسنه رسالت',
      provider: 'بانک رسالت',
      kind: 'LOAN',
      totalAmountToman: 60000000,
      remainingAmountToman: 35000000,
      installmentCount: 12,
      paidInstallmentCount: 5,
      nextDueTimestamp: now + 9 * DAY,
      note: 'برداشت خودکار از حساب',
      isSettled: false,
    },
    {
      id: 'cm_check',
      title: 'چک صیادی اجاره‌بها',
      provider: 'چک صیادی بنفش',
      kind: 'CHECK',
      totalAmountToman: 12000000,
      remainingAmountToman: 12000000,
      installmentCount: 1,
      paidInstallmentCount: 0,
      nextDueTimestamp: now + 14 * DAY,
      note: 'شناسه صیاد ثبت‌شده در سامانه',
      isSettled: false,
    },
  ],
  budgetCaps: [
    { categoryFa: 'خواربار', monthlyLimitToman: 9000000 },
    { categoryFa: 'رستوران و کافه', monthlyLimitToman: 3500000 },
    { categoryFa: 'حمل و نقل', monthlyLimitToman: 2500000 },
    { categoryFa: 'خرید', monthlyLimitToman: 5000000 },
    { categoryFa: 'قبض‌ها', monthlyLimitToman: 1500000 },
  ],
};

export function loadPersistedState(): AppPersistedState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return INITIAL_STATE;
    const parsed = JSON.parse(raw);
    return {
      ...INITIAL_STATE,
      ...parsed,
    };
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
  } catch {
    // Ignore
  }
  return INITIAL_STATE;
}
