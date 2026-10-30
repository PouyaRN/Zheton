import React, { useEffect, useMemo, useState } from 'react';
import {
  AssetKind,
  BankAccount,
  Commitment,
  CommitmentKind,
  Holding,
  MarketQuote,
  NecessityTag,
  Transaction,
  TxnDirection,
} from './types';
import {
  formatCardPan,
  formatNumberWithDecimals,
  formatPercent,
  formatSheba,
  formatToman,
  getJalaliDate,
  toFaDigits,
} from './utils/format';
import {
  IRANIAN_BANKS,
  detectBankByPan,
  isValidLuhn,
  isValidSheba,
} from './utils/shetabKit';
import {
  CATEGORIES,
  SAMPLE_BANK_SMS,
  parseBankSms,
} from './utils/smsParser';
import { DEFAULT_MARKET_QUOTES, simulateMarketTick } from './services/marketService';
import {
  getDemoState,
  loadPersistedState,
  savePersistedState,
} from './services/storageService';
import { CalculatorsModal } from './components/CalculatorsModal';
import { OnboardingModal } from './components/OnboardingModal';

type TabKey = 'DASHBOARD' | 'ANALYTICS' | 'COMMITMENTS' | 'GOALS';

interface SavingsGoal {
  id: string;
  title: string;
  currentMillion: number;
  targetMillion: number;
  category: 'short' | 'long' | 'emergency';
  etaLabel: string;
  imageUrl?: string;
}

const INITIAL_SAVINGS_GOALS: SavingsGoal[] = [];

export default function App() {
  const [state, setState] = useState(() => loadPersistedState());
  const [quotes, setQuotes] = useState<MarketQuote[]>(DEFAULT_MARKET_QUOTES);
  const [activeTab, setActiveTab] = useState<TabKey>('DASHBOARD');
  const [dayOffset, setDayOffset] = useState(0);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  // Tab 2: Analytics sub-states
  const [analyticsPeriod, setAnalyticsPeriod] = useState<'daily' | 'weekly' | 'monthly' | 'yearly'>('weekly');
  const [txnFilterModalOpen, setTxnFilterModalOpen] = useState(false);
  const [txnSearchQuery, setTxnSearchQuery] = useState('');

  // Tab 3: Commitments & Cards sub-states
  const [commitmentFilter, setCommitmentFilter] = useState<'all' | 'bank' | 'cheque' | 'cards'>('all');
  const [smsReminder3Days, setSmsReminder3Days] = useState(true);
  const [voiceAlarmDueDay, setVoiceAlarmDueDay] = useState(false);
  const urgentCommitment = useMemo(() => {
    const now = Date.now();
    const sevenDays = 7 * 24 * 3600 * 1000;
    return (
      state.commitments
        .filter(
          (c) =>
            !c.isSettled &&
            c.nextDueTimestamp > 0 &&
            c.nextDueTimestamp - now >= 0 &&
            c.nextDueTimestamp - now <= sevenDays
        )
        .sort((a, b) => a.nextDueTimestamp - b.nextDueTimestamp)[0] ?? null
    );
  }, [state.commitments]);

  const totalCommitmentsToman = useMemo(
    () => state.commitments.reduce((sum, c) => sum + c.totalAmountToman, 0),
    [state.commitments]
  );
  const remainingCommitmentsToman = useMemo(
    () =>
      state.commitments
        .filter((c) => !c.isSettled)
        .reduce((sum, c) => sum + c.remainingAmountToman, 0),
    [state.commitments]
  );
  const paidCommitmentsToman = Math.max(0, totalCommitmentsToman - remainingCommitmentsToman);
  const commitmentsProgressPct =
    totalCommitmentsToman > 0
      ? Math.round((paidCommitmentsToman / totalCommitmentsToman) * 100)
      : 0;

  const monthlyCashflowToman = useMemo(() => {
    const credits = state.transactions
      .filter((t) => t.direction === 'CREDIT')
      .reduce((s, t) => s + t.amountToman, 0);
    const debits = state.transactions
      .filter((t) => t.direction === 'DEBIT')
      .reduce((s, t) => s + t.amountToman, 0);
    return credits - debits;
  }, [state.transactions]);
  const [selectedChequeModal, setSelectedChequeModal] = useState<Commitment | null>(null);

  // Tab 4: Goals & Savings sub-states
  const [roundupActive, setRoundupActive] = useState(true);
  const [goalFilter, setGoalFilter] = useState<'all' | 'short' | 'long' | 'emergency' | 'portfolio'>('all');
  const [savingsGoals, setSavingsGoals] = useState<SavingsGoal[]>(INITIAL_SAVINGS_GOALS);
  const [simTargetMillion, setSimTargetMillion] = useState(80);
  const [simMonthlyMillion, setSimMonthlyMillion] = useState(9);

  // Modals
  const [isCalcOpen, setIsCalcOpen] = useState(false);
  const [isQuickAddOpen, setIsQuickAddOpen] = useState(false);
  const [quickAddTab, setQuickAddTab] = useState<'TXN' | 'SMS' | 'HOLDING' | 'CARD' | 'COMMITMENT' | 'GOAL'>('TXN');
  const [isTransferDrawerOpen, setIsTransferDrawerOpen] = useState(false);
  const [isMarketDrawerOpen, setIsMarketDrawerOpen] = useState(false);
  const [marketFilter, setMarketFilter] = useState<'ALL' | AssetKind>('ALL');

  // Form states inside QuickAddModal
  const [txAmount, setTxAmount] = useState('');
  const [txDirection, setTxDirection] = useState<TxnDirection>('DEBIT');
  const [txMerchant, setTxMerchant] = useState('');
  const [txCategory, setTxCategory] = useState(CATEGORIES[0].nameFa);
  const [txNecessity, setTxNecessity] = useState<NecessityTag>('NEED');
  const [txNote, setTxNote] = useState('');
  const [txAccountId, setTxAccountId] = useState(state.accounts[0]?.id ?? '');

  // SMS Paste state
  const [smsRaw, setSmsRaw] = useState(SAMPLE_BANK_SMS[0].text);

  // Holding form state
  const [holdTypeId, setHoldTypeId] = useState('gold18');
  const [holdAmount, setHoldAmount] = useState('');
  const [holdLabel, setHoldLabel] = useState('');

  // Card form state
  const [cardPan, setCardPan] = useState('');
  const [cardSheba, setCardSheba] = useState('');
  const [cardBalance, setCardBalance] = useState('');
  const [cardNickname, setCardNickname] = useState('');

  // Commitment form state
  const [cmTitle, setCmTitle] = useState('');
  const [cmProvider, setCmProvider] = useState('بانک مسکن');
  const [cmKind, setCmKind] = useState<CommitmentKind>('LOAN');
  const [cmTotal, setCmTotal] = useState('');
  const [cmInstallments, setCmInstallments] = useState('36');
  const [cmDueDays, setCmDueDays] = useState('12');

  // Goal form state
  const [newGoalTitle, setNewGoalTitle] = useState('');
  const [newGoalTargetMillion, setNewGoalTargetMillion] = useState('50');
  const [newGoalCurrentMillion, setNewGoalCurrentMillion] = useState('10');
  const [newGoalCategory, setNewGoalCategory] = useState<'short' | 'long' | 'emergency'>('short');

  useEffect(() => {
    savePersistedState(state);
  }, [state]);

  useEffect(() => {
    const timer = setInterval(() => {
      setQuotes((prev) => simulateMarketTick(prev));
    }, 9000);
    return () => clearInterval(timer);
  }, []);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 2600);
  };

  const displayedDateLabel = useMemo(() => {
    const targetDate = new Date(Date.now() + dayOffset * 24 * 3600 * 1000);
    const j = getJalaliDate(targetDate);
    const parts = j.formatted.split(' ');
    const dayAndMonth = parts.slice(0, 2).join(' ');
    if (dayOffset === 0) return `امروز، ${dayAndMonth}`;
    if (dayOffset === -1) return `دیروز، ${dayAndMonth}`;
    if (dayOffset === 1) return `فردا، ${dayAndMonth}`;
    return j.formatted;
  }, [dayOffset]);

  // Aggregations
  const liquidBalanceToman = useMemo(
    () => state.accounts.reduce((acc, item) => acc + item.balanceToman, 0),
    [state.accounts]
  );

  const portfolioValueToman = useMemo(() => {
    return state.holdings.reduce((sum, h) => {
      if (h.excluded || h.archived) return sum;
      const q = quotes.find((item) => item.id === h.typeId);
      const price = q ? q.priceToman : 0;
      return sum + Math.round(h.amount * price);
    }, 0);
  }, [state.holdings, quotes]);

  const activeLiabilitiesToman = useMemo(() => {
    return state.commitments
      .filter((c) => !c.isSettled)
      .reduce((sum, c) => sum + c.remainingAmountToman, 0);
  }, [state.commitments]);

  const totalCombinedBalanceToman = useMemo(() => {
    return liquidBalanceToman + portfolioValueToman;
  }, [liquidBalanceToman, portfolioValueToman]);

  const totalSavingsMillion = useMemo(() => {
    return savingsGoals.reduce((s, g) => s + g.currentMillion, 0);
  }, [savingsGoals]);

  const monthlyDebitToman = useMemo(() => {
    return state.transactions
      .filter((t) => t.direction === 'DEBIT')
      .reduce((s, t) => s + t.amountToman, 0);
  }, [state.transactions]);

  const monthlyCreditToman = useMemo(() => {
    return state.transactions
      .filter((t) => t.direction === 'CREDIT')
      .reduce((s, t) => s + t.amountToman, 0);
  }, [state.transactions]);

  const togglePrivacy = () => {
    setState((prev) => ({ ...prev, privacyMode: !prev.privacyMode }));
  };

  const openQuickAdd = (tab: 'TXN' | 'SMS' | 'HOLDING' | 'CARD' | 'COMMITMENT' | 'GOAL', dir?: TxnDirection) => {
    if (dir) setTxDirection(dir);
    setQuickAddTab(tab);
    setIsQuickAddOpen(true);
  };

  const handleCopy = (text: string, id: string, label: string) => {
    navigator.clipboard?.writeText(text);
    setCopiedId(id);
    showToast(`${label} در حافظه کپی شد`);
    setTimeout(() => setCopiedId(null), 1800);
  };

  // Mutations
  const handleAddTransaction = (e: React.FormEvent) => {
    e.preventDefault();
    const amount = Number(txAmount);
    if (!amount || amount <= 0) return;
    const newTx: Transaction = {
      id: `tx_${Date.now()}`,
      timestamp: Date.now(),
      amountToman: amount,
      direction: txDirection,
      categoryFa: txCategory,
      merchant: txMerchant.trim() || txCategory,
      note: txNote.trim(),
      accountId: txAccountId,
      necessity: txNecessity,
    };

    setState((prev) => {
      const updatedAccounts = prev.accounts.map((acc) => {
        if (acc.id !== txAccountId) return acc;
        const delta = txDirection === 'DEBIT' ? -amount : amount;
        return {
          ...acc,
          balanceToman: Math.max(0, acc.balanceToman + delta),
          updatedAt: Date.now(),
        };
      });
      return {
        ...prev,
        accounts: updatedAccounts,
        transactions: [newTx, ...prev.transactions],
      };
    });

    setTxAmount('');
    setTxMerchant('');
    setTxNote('');
    setIsQuickAddOpen(false);
    showToast('تراکنش با موفقیت ثبت شد');
  };

  const handleParseAndSaveSms = () => {
    const parsed = parseBankSms(smsRaw);
    if (!parsed) return;
    const matchedAccount =
      state.accounts.find((a) => a.bankNameFa.includes(parsed.bankNameFa)) ??
      state.accounts[0];

    const newTx: Transaction = {
      id: `tx_sms_${Date.now()}`,
      timestamp: Date.now(),
      amountToman: parsed.amountToman,
      direction: parsed.direction,
      categoryFa: parsed.categoryFa,
      merchant: parsed.merchant,
      note: `ثبت خودکار از پیامک ${parsed.bankNameFa}`,
      accountId: matchedAccount?.id,
      necessity: parsed.necessity,
    };

    setState((prev) => {
      const updatedAccounts = prev.accounts.map((acc) => {
        if (acc.id !== matchedAccount?.id) return acc;
        const nextBal =
          parsed.balanceToman !== null
            ? parsed.balanceToman
            : Math.max(
                0,
                acc.balanceToman +
                  (parsed.direction === 'DEBIT' ? -parsed.amountToman : parsed.amountToman)
              );
        return { ...acc, balanceToman: nextBal, updatedAt: Date.now() };
      });
      return {
        ...prev,
        accounts: updatedAccounts,
        transactions: [newTx, ...prev.transactions],
      };
    });

    setIsQuickAddOpen(false);
    showToast(`پیامک ${parsed.bankNameFa} استخراج و ثبت شد`);
  };

  const handleAddHolding = (e: React.FormEvent) => {
    e.preventDefault();
    const amt = Number(holdAmount);
    if (!amt || amt <= 0) return;
    const newHolding: Holding = {
      id: `h_${Date.now()}`,
      typeId: holdTypeId,
      amount: amt,
      label: holdLabel.trim(),
      excluded: false,
    };
    setState((prev) => ({
      ...prev,
      holdings: [newHolding, ...prev.holdings],
    }));
    setHoldAmount('');
    setHoldLabel('');
    setIsQuickAddOpen(false);
    showToast('دارایی جدید به سبد اضافه شد');
  };

  const handleAddCard = (e: React.FormEvent) => {
    e.preventDefault();
    const cleanPan = cardPan.replace(/\D/g, '');
    if (cleanPan.length < 12) return;
    const detected = detectBankByPan(cleanPan) ?? IRANIAN_BANKS[0];
    const newAcc: BankAccount = {
      id: `acc_${Date.now()}`,
      bankKey: detected.key,
      bankNameFa: detected.fa,
      pan: cleanPan.padEnd(16, '0').slice(0, 16),
      sheba: cardSheba.trim() || undefined,
      balanceToman: Number(cardBalance) || 0,
      nickname: cardNickname.trim() || `کارت ${detected.fa}`,
      theme: 'default',
      updatedAt: Date.now(),
    };
    setState((prev) => ({
      ...prev,
      accounts: [...prev.accounts, newAcc],
    }));
    setCardPan('');
    setCardSheba('');
    setCardBalance('');
    setCardNickname('');
    setIsQuickAddOpen(false);
    showToast(`کارت ${detected.fa} اضافه شد`);
  };

  const handleAddCommitment = (e: React.FormEvent) => {
    e.preventDefault();
    const total = Number(cmTotal);
    const count = Math.max(1, Number(cmInstallments) || 1);
    const days = Math.max(1, Number(cmDueDays) || 7);
    if (!total || total <= 0) return;
    const newCm: Commitment = {
      id: `cm_${Date.now()}`,
      title: cmTitle.trim() || `تعهد ${cmProvider}`,
      provider: cmProvider,
      kind: cmKind,
      totalAmountToman: total,
      remainingAmountToman: total,
      installmentCount: count,
      paidInstallmentCount: 0,
      nextDueTimestamp: Date.now() + days * 24 * 3600 * 1000,
      isSettled: false,
    };
    setState((prev) => ({
      ...prev,
      commitments: [newCm, ...prev.commitments],
    }));
    setCmTitle('');
    setCmTotal('');
    setIsQuickAddOpen(false);
    showToast('قسط / تعهد جدید ثبت شد');
  };

  const handleAddGoal = (e: React.FormEvent) => {
    e.preventDefault();
    const targetM = Math.max(1, Number(newGoalTargetMillion) || 50);
    const currentM = Math.min(targetM, Math.max(0, Number(newGoalCurrentMillion) || 0));
    const goal: SavingsGoal = {
      id: `goal_${Date.now()}`,
      title: newGoalTitle.trim() || 'هدف پس‌انداز جدید',
      currentMillion: currentM,
      targetMillion: targetM,
      category: newGoalCategory,
      etaLabel: `${toFaDigits(Math.max(1, Math.ceil((targetM - currentM) / 9)))} ماه تا تکمیل`,
    };
    setSavingsGoals((prev) => [goal, ...prev]);
    setNewGoalTitle('');
    setIsQuickAddOpen(false);
    showToast('هدف پس‌انداز جدید تعریف شد');
  };

  const handleDepositToGoal = (goalId: string, addMillion = 5) => {
    setSavingsGoals((prev) =>
      prev.map((g) => {
        if (g.id !== goalId) return g;
        const nextCur = Math.min(g.targetMillion, g.currentMillion + addMillion);
        return { ...g, currentMillion: nextCur };
      })
    );
    showToast(`${toFaDigits(addMillion)} میلیون تومان به هدف واریز شد`);
  };

  const handlePayInstallment = (commitmentId: string) => {
    setState((prev) => {
      const updated = prev.commitments.map((c) => {
        if (c.id !== commitmentId || c.isSettled) return c;
        const nextPaid = c.paidInstallmentCount + 1;
        const perInst = Math.round(c.totalAmountToman / Math.max(1, c.installmentCount));
        const nextRem = Math.max(0, c.remainingAmountToman - perInst);
        const settled = nextPaid >= c.installmentCount || nextRem === 0;
        return {
          ...c,
          paidInstallmentCount: nextPaid,
          remainingAmountToman: settled ? 0 : nextRem,
          isSettled: settled,
          nextDueTimestamp: c.nextDueTimestamp + 30 * 24 * 3600 * 1000,
        };
      });
      return { ...prev, commitments: updated };
    });
    showToast('پرداخت قسط با موفقیت ثبت شد');
  };

  // Period multiplier for Analytics tab
  const periodFactor =
    analyticsPeriod === 'daily'
      ? 0.15
      : analyticsPeriod === 'weekly'
      ? 1
      : analyticsPeriod === 'monthly'
      ? 4.2
      : 48;

  const periodLabelFa =
    analyticsPeriod === 'daily'
      ? 'امروز ۲۵ آبان'
      : analyticsPeriod === 'weekly'
      ? 'هفته ۲۴ بهار'
      : analyticsPeriod === 'monthly'
      ? 'آبان ۱۴۰۳'
      : 'سال ۱۴۰۳';

  const simMonthsNeeded = Math.max(1, Math.ceil(simTargetMillion / Math.max(1, simMonthlyMillion)));

  return (
    <div className="bg-background text-on-surface flex flex-col min-h-screen selection:bg-primary selection:text-on-primary antialiased">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed top-20 inset-x-0 z-50 flex justify-center px-4 pointer-events-none">
          <div className="bg-primary text-on-primary px-4 py-2.5 rounded-full shadow-[0_8px_24px_rgba(9,13,21,0.22)] flex items-center gap-2 text-[12px] font-bold animate-bounce">
            <span className="material-symbols-outlined text-[16px] text-emerald-400">check_circle</span>
            <span>{toastMessage}</span>
          </div>
        </div>
      )}

      {/* =====================================================================
          TOP APP BAR: Floating Lithe Pill Header
         ===================================================================== */}
      <header className="fixed top-0 inset-x-0 z-40 bg-background/85 backdrop-blur-xl pt-safe px-4 pb-2 transition-all">
        <div className="h-14 flex items-center justify-between gap-3 max-w-md mx-auto">
          {activeTab === 'DASHBOARD' ? (
            /* Date Selector Pill (Gordol "Today" bar from _4/code.html) */
            <div className="flex items-center gap-1.5 bg-white border border-gray-200/80 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] px-3.5 py-1.5 rounded-full">
              <button
                aria-label="روز بعد"
                onClick={() => setDayOffset((d) => d + 1)}
                className="w-6 h-6 flex items-center justify-center rounded-full text-gray-500 hover:text-black hover:bg-gray-100 transition-colors"
                type="button"
              >
                <span className="material-symbols-outlined text-[18px]">chevron_right</span>
              </button>
              <button
                onClick={() => setDayOffset(0)}
                className="text-[13px] font-bold text-gray-900 px-1"
                type="button"
              >
                {displayedDateLabel}
              </button>
              <button
                aria-label="روز قبل"
                onClick={() => setDayOffset((d) => d - 1)}
                className="w-6 h-6 flex items-center justify-center rounded-full text-gray-500 hover:text-black hover:bg-gray-100 transition-colors"
                type="button"
              >
                <span className="material-symbols-outlined text-[18px]">chevron_left</span>
              </button>
            </div>
          ) : (
            /* Page Title Capsule from _1, _2, _3 */
            <div className="flex items-center gap-1.5 bg-surface-container-lowest shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] px-3.5 py-1.5 rounded-full">
              <span className="w-2 h-2 rounded-full bg-secondary-container animate-pulse" />
              <h1 className="text-[13px] font-bold text-on-surface px-1 tracking-tight">
                {activeTab === 'ANALYTICS'
                  ? 'تحلیل مخارج'
                  : activeTab === 'COMMITMENTS'
                  ? 'اقساط و چک'
                  : 'پس‌انداز و اهداف'}
              </h1>
            </div>
          )}

          {/* Action Controls Capsule */}
          <div className="flex items-center gap-2">
            <button
              onClick={() => setIsCalcOpen(true)}
              aria-label="دستیار هوشمند گرداول"
              className="flex items-center gap-1.5 px-3 py-1.5 bg-surface-container-lowest hover:bg-surface-container-low border border-gray-200/60 rounded-full shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] text-on-surface text-[12px] font-semibold transition-all active:scale-95"
              type="button"
            >
              {activeTab === 'DASHBOARD' && (
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              )}
              <span>{activeTab === 'DASHBOARD' ? 'دستیار هوشمند' : 'هوشمند'}</span>
              <span className="material-symbols-outlined text-[16px] text-secondary-container">
                auto_awesome
              </span>
            </button>

            <button
              onClick={() => {
                setActiveTab('COMMITMENTS');
                showToast('سررسید وام ازدواج ۴ روز دیگر است');
              }}
              aria-label="اعلان‌ها"
              className="relative w-9 h-9 rounded-full bg-surface-container-lowest border border-gray-200/60 flex items-center justify-center text-on-surface shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] active:scale-95 transition-all"
              type="button"
            >
              <span className="material-symbols-outlined text-[20px]">notifications</span>
              <span className="absolute top-1.5 left-1.5 w-2 h-2 bg-on-tertiary-container rounded-full ring-2 ring-surface-container-lowest" />
            </button>
          </div>
        </div>
      </header>

      {/* =====================================================================
          MAIN SCROLLABLE CONTENT
         ===================================================================== */}
      <main className="flex flex-col relative w-full pt-20 pb-32 px-4 max-w-md mx-auto min-h-screen">
        {/* ===================================================================
            TAB 1: DASHBOARD (داشبورد مالی — _4/code.html)
           =================================================================== */}
        {activeTab === 'DASHBOARD' && (
          <div className="flex flex-col gap-4">
            {/* 1. HERO BALANCE CARD (Gordol Aesthetic: Clean White 3XL Card) */}
            <section className="relative overflow-hidden rounded-3xl bg-white border border-gray-100 shadow-[0_8px_30px_rgb(0,0,0,0.04)] p-5">
              <div className="absolute -top-12 -left-12 w-36 h-36 bg-blue-50/70 rounded-full blur-2xl pointer-events-none" />
              <div className="relative flex flex-col gap-4">
                {/* Top Row: Label & Monthly Gain Tag */}
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 rounded-full bg-gray-100 flex items-center justify-center text-gray-900 font-bold">
                      <span className="material-symbols-outlined text-[18px]">
                        account_balance_wallet
                      </span>
                    </div>
                    <span className="text-[13px] font-semibold text-gray-500">
                      موجودی کل تجمیعی
                    </span>
                  </div>
                  <div className="flex items-center gap-1 px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-100/80">
                    <span className="material-symbols-outlined text-[14px]">trending_up</span>
                    <span className="text-[12px] font-bold dir-ltr">+۸.۴٪</span>
                    <span className="text-[10px] text-emerald-600 font-medium">ماهانه</span>
                  </div>
                </div>

                {/* Total Balance Big Display */}
                <div className="flex items-baseline justify-between pt-1">
                  <div className="flex items-baseline gap-1.5">
                    <span className="text-[34px] font-black text-gray-950 tracking-tight leading-none num-tabular">
                      {state.privacyMode
                        ? '••••••••'
                        : formatToman(totalCombinedBalanceToman)}
                    </span>
                    <span className="text-[14px] font-bold text-gray-500">تومان</span>
                  </div>
                  <button
                    onClick={togglePrivacy}
                    aria-label="پنهان‌سازی موجودی"
                    className="w-8 h-8 rounded-full bg-gray-50 hover:bg-gray-100 text-gray-400 hover:text-gray-700 transition-colors flex items-center justify-center"
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[18px]">
                      {state.privacyMode ? 'visibility_off' : 'visibility'}
                    </span>
                  </button>
                </div>

                {/* Financial Sub-bar / Monthly Profit Tag */}
                <div className="flex items-center justify-between p-2.5 rounded-2xl bg-gray-50/90 border border-gray-100">
                  <div className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-emerald-500" />
                    <span className="text-[12px] text-gray-500 font-medium">
                      سود محقق‌شده این ماه:
                    </span>
                  </div>
                  <div className="flex items-center gap-1">
                    <span className="text-[13px] font-extrabold text-emerald-600 num-tabular">
                      {state.privacyMode
                        ? '••••••'
                        : `${monthlyCashflowToman >= 0 ? '+' : ''}${formatToman(monthlyCashflowToman)}`}
                    </span>
                    <span className="text-[11px] font-semibold text-gray-500">تومان</span>
                  </div>
                </div>

                {/* Gordol Action Pills: Clean High Contrast Rounded-2XL Buttons */}
                <div className="grid grid-cols-4 gap-2 pt-1">
                  <button
                    onClick={() => openQuickAdd('TXN', 'CREDIT')}
                    className="flex flex-col items-center justify-center gap-1.5 py-2.5 px-1 rounded-2xl bg-gray-900 hover:bg-black text-white active:scale-95 transition-all shadow-xs"
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[20px]">add</span>
                    <span className="text-[11px] font-bold">واریز</span>
                  </button>

                  <button
                    onClick={() => openQuickAdd('TXN', 'DEBIT')}
                    className="flex flex-col items-center justify-center gap-1.5 py-2.5 px-1 rounded-2xl bg-gray-100 hover:bg-gray-200/70 text-gray-900 active:scale-95 transition-all"
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[20px]">edit_note</span>
                    <span className="text-[11px] font-bold">ثبت تراکنش</span>
                  </button>

                  <button
                    onClick={() => setIsTransferDrawerOpen(true)}
                    className="flex flex-col items-center justify-center gap-1.5 py-2.5 px-1 rounded-2xl bg-gray-100 hover:bg-gray-200/70 text-gray-900 active:scale-95 transition-all"
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[20px]">send_money</span>
                    <span className="text-[11px] font-bold">انتقال</span>
                  </button>

                  <button
                    onClick={() => openQuickAdd('SMS')}
                    className="flex flex-col items-center justify-center gap-1.5 py-2.5 px-1 rounded-2xl bg-gray-100 hover:bg-gray-200/70 text-gray-900 active:scale-95 transition-all"
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[20px]">document_scanner</span>
                    <span className="text-[11px] font-bold">اسکن قبض</span>
                  </button>
                </div>
              </div>
            </section>

            {/* 2. TWO-COLUMN SPLIT METRIC CARDS (Like Calorie / Protein Cards in Gordol) */}
            <section className="grid grid-cols-2 gap-3">
              {/* Split Card 1: Budget Status with Circular Progress Ring */}
              <div
                onClick={() => setActiveTab('ANALYTICS')}
                className="cursor-pointer rounded-3xl bg-white border border-gray-100 p-4 shadow-[0_4px_20px_rgb(0,0,0,0.03)] flex flex-col justify-between active:scale-[0.99] transition"
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[12px] font-bold text-gray-500">بودجه ماه جاری</span>
                  <span className="w-6 h-6 rounded-full bg-rose-50 text-rose-600 flex items-center justify-center text-[11px]">
                    <span className="material-symbols-outlined text-[14px]">pie_chart</span>
                  </span>
                </div>
                <div className="flex items-center gap-3 my-1">
                  <div className="relative w-12 h-12 flex-shrink-0 flex items-center justify-center">
                    <svg className="w-12 h-12 -rotate-90" viewBox="0 0 36 36">
                      <path
                        className="text-gray-100"
                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="3.5"
                      />
                      <path
                        className="text-rose-500"
                        d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                        fill="none"
                        stroke="currentColor"
                        strokeDasharray="65, 100"
                        strokeLinecap="round"
                        strokeWidth="3.5"
                      />
                    </svg>
                    <span className="absolute text-[11px] font-black text-gray-900 dir-ltr">
                      ۶۵٪
                    </span>
                  </div>
                  <div>
                    <div className="text-[16px] font-black text-gray-900 leading-tight">
                      {state.privacyMode ? '•••' : '۵.۲ م'}
                    </div>
                    <div className="text-[11px] text-gray-400 font-medium">از ۸ م تومان</div>
                  </div>
                </div>
                <div className="mt-2 pt-2 border-t border-gray-50 text-[11px] font-semibold text-rose-600 flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-rose-500" />
                  <span>۱۰ روز زودتر از موعد</span>
                </div>
              </div>

              {/* Split Card 2: Active Installments & Cheques */}
              <div
                onClick={() => setActiveTab('COMMITMENTS')}
                className="cursor-pointer rounded-3xl bg-white border border-gray-100 p-4 shadow-[0_4px_20px_rgb(0,0,0,0.03)] flex flex-col justify-between active:scale-[0.99] transition"
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[12px] font-bold text-gray-500">اقساط و چک‌ها</span>
                  <span className="w-6 h-6 rounded-full bg-amber-50 text-amber-600 flex items-center justify-center text-[11px]">
                    <span className="material-symbols-outlined text-[14px]">receipt_long</span>
                  </span>
                </div>
                <div className="my-1">
                  <div className="text-[18px] font-black text-gray-900 leading-tight num-tabular">
                    {state.privacyMode ? '••••••' : formatToman(remainingCommitmentsToman)}
                  </div>
                  <div className="text-[11px] text-gray-400 font-medium">
                    {toFaDigits(state.commitments.filter((c) => !c.isSettled).length)} تعهد فعال پیش‌رو
                  </div>
                </div>
                <div className="mt-2 pt-2 border-t border-gray-50">
                  <div className="inline-flex items-center gap-1 bg-amber-50 px-2 py-0.5 rounded-full text-[10px] font-bold text-amber-800">
                    <span className="material-symbols-outlined text-[12px] text-amber-600">
                      alarm
                    </span>
                    <span>
                      {urgentCommitment
                        ? `سررسید: ${urgentCommitment.title}`
                        : 'بدون سررسید فوری'}
                    </span>
                  </div>
                </div>
              </div>
            </section>

            {/* 3. AI FINANCIAL INSIGHT BANNER */}
            <section className="rounded-3xl bg-white border border-gray-100 p-4 shadow-[0_4px_20px_rgb(0,0,0,0.03)] relative overflow-hidden">
              <div className="flex items-start justify-between gap-3 mb-2.5">
                <div className="flex items-center gap-2">
                  <div className="w-7 h-7 rounded-full bg-indigo-50 text-indigo-600 flex items-center justify-center">
                    <span className="material-symbols-outlined text-[16px]">psychology</span>
                  </div>
                  <span className="text-[13px] font-extrabold text-gray-900">
                    پیشنهاد هوشمند سرمایه‌بان
                  </span>
                </div>
                <span className="text-[10px] font-bold bg-indigo-50 text-indigo-700 px-2.5 py-0.5 rounded-full">
                  مخصوص تو
                </span>
              </div>
              <p className="text-[12.5px] leading-relaxed text-gray-600 mb-3">
                با کنترل هزینه کافه و سوپرمارکت در هفته آتی، امکان پس‌انداز اضافی{' '}
                <span className="font-bold text-gray-900">۱٬۵۰۰٬۰۰۰ تومان</span> و خرید صندوق طلا
                فراهم می‌شود.
              </p>
              <button
                onClick={() => {
                  showToast('بودجه هفتگی هوشمند تنظیم و فعال شد');
                  setActiveTab('GOALS');
                }}
                className="w-full py-2.5 px-4 rounded-full bg-gray-900 hover:bg-black text-white text-[12px] font-bold flex items-center justify-center gap-2 transition-all active:scale-[0.98]"
                type="button"
              >
                <span className="material-symbols-outlined text-[16px]">tune</span>
                <span>تنظیم خودکار بودجه هفتگی</span>
              </button>
            </section>

            {/* 4. LIVE MARKET RIBBON (Horizontal scrollable pill tiles) */}
            <section className="flex flex-col gap-2.5">
              <div className="flex items-center justify-between px-1">
                <div className="flex items-center gap-1.5">
                  <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                  <span className="text-[13px] font-extrabold text-gray-900">نبض بازارها</span>
                </div>
                <button
                  onClick={() => setIsMarketDrawerOpen(true)}
                  className="flex items-center gap-1 text-[11px] font-bold text-secondary hover:underline"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[13px]">schedule</span>
                  <span>۱۴:۳۵ • همه نرخ‌ها</span>
                </button>
              </div>

              <div className="flex items-stretch gap-2.5 overflow-x-auto no-scrollbar py-0.5 px-0.5">
                {[
                  {
                    id: 'gold18',
                    title: 'طلا ۱۸ عیار',
                    price: formatToman(
                      quotes.find((q) => q.id === 'gold18')?.priceToman ?? 3450000
                    ),
                    unit: 'تومان / گرم',
                    delta: '+۱.۱٪',
                    up: true,
                    width: 'w-36',
                  },
                  {
                    id: 'coin_emami',
                    title: 'سکه امامی',
                    price: formatToman(
                      quotes.find((q) => q.id === 'coin_emami')?.priceToman ?? 41200000
                    ),
                    unit: 'تومان',
                    delta: '+۰.۸٪',
                    up: true,
                    width: 'w-36',
                  },
                  {
                    id: 'usdt',
                    title: 'تتر (USDT)',
                    price: formatToman(quotes.find((q) => q.id === 'usdt')?.priceToman ?? 59850),
                    unit: 'تومان',
                    delta: '+۰.۲٪',
                    up: true,
                    width: 'w-32',
                  },
                  {
                    id: 'usd',
                    title: 'دلار آزاد',
                    price: formatToman(quotes.find((q) => q.id === 'usd')?.priceToman ?? 59400),
                    unit: 'تومان',
                    delta: '-۰.۴٪',
                    up: false,
                    width: 'w-32',
                  },
                  {
                    id: 'btc',
                    title: 'بیت‌کوین',
                    price: '۶۴٬۲۰۰',
                    unit: 'دلار',
                    delta: '+۲.۵٪',
                    up: true,
                    width: 'w-32',
                  },
                ].map((m) => (
                  <div
                    key={m.id}
                    onClick={() => setIsMarketDrawerOpen(true)}
                    className={`cursor-pointer flex-shrink-0 ${m.width} p-3 rounded-2xl bg-white border border-gray-100 shadow-xs flex flex-col justify-between active:scale-95 transition`}
                  >
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="text-[11px] font-bold text-gray-500">{m.title}</span>
                      <span
                        className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full dir-ltr ${
                          m.up ? 'text-emerald-600 bg-emerald-50' : 'text-rose-600 bg-rose-50'
                        }`}
                      >
                        {m.delta}
                      </span>
                    </div>
                    <div className="text-[15px] font-black text-gray-950 num-tabular">
                      {m.price}
                    </div>
                    <span className="text-[10px] text-gray-400 mt-0.5">{m.unit}</span>
                  </div>
                ))}
              </div>
            </section>

            {/* 5. RECENT TRANSACTIONS (Gordol Clean List Items) */}
            <section className="flex flex-col gap-2.5">
              <div className="flex items-center justify-between px-1">
                <span className="text-[13px] font-extrabold text-gray-900">تراکنش‌های اخیر</span>
                <button
                  onClick={() => setActiveTab('ANALYTICS')}
                  className="text-[12px] font-bold text-gray-500 hover:text-black transition-colors"
                  type="button"
                >
                  مشاهده همه
                </button>
              </div>

              <div className="rounded-3xl bg-white border border-gray-100 p-2 shadow-[0_4px_20px_rgb(0,0,0,0.03)] flex flex-col divide-y divide-gray-50">
                {state.transactions.length === 0 ? (
                  <div className="flex flex-col items-center justify-center p-6 text-center text-gray-400 gap-2">
                    <span className="material-symbols-outlined text-[32px] text-gray-300">receipt_long</span>
                    <span className="text-[13px] font-bold text-gray-700">هنوز تراکنشی ثبت نشده است</span>
                    <span className="text-[11px] text-gray-400">با دریافت پیامک‌های بانکی یا دکمه «واریز» / «ثبت تراکنش» تراکنش جدید ثبت کنید.</span>
                  </div>
                ) : (
                  state.transactions.slice(0, 6).map((tx, idx) => {
                  const isCredit = tx.direction === 'CREDIT';
                  const isGold = tx.categoryFa.includes('سرمایه') || tx.merchant.includes('طلا');
                  const isCafe = tx.categoryFa.includes('کافه');
                  const iconName = isCredit
                    ? 'payments'
                    : isGold
                    ? 'diamond'
                    : isCafe
                    ? 'local_cafe'
                    : 'shopping_bag';
                  const iconStyle = isCredit
                    ? 'bg-emerald-50 text-emerald-600'
                    : isGold
                    ? 'bg-amber-50 text-amber-600'
                    : 'bg-gray-100 text-gray-700';

                  return (
                    <div
                      key={tx.id || idx}
                      className="p-3 flex items-center justify-between hover:bg-gray-50/70 rounded-2xl transition-colors"
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <div
                          className={`w-10 h-10 rounded-full ${iconStyle} flex items-center justify-center flex-shrink-0`}
                        >
                          <span className="material-symbols-outlined text-[20px]">{iconName}</span>
                        </div>
                        <div className="flex flex-col min-w-0">
                          <span className="text-[13px] font-bold text-gray-900 truncate">
                            {tx.merchant}
                          </span>
                          <span className="text-[11px] text-gray-400">
                            ۲۵ آبان • {tx.categoryFa}
                          </span>
                        </div>
                      </div>
                      <div className="flex flex-col items-end flex-shrink-0">
                        <span
                          className={`text-[13px] font-black dir-ltr num-tabular ${
                            isCredit ? 'text-emerald-600' : 'text-gray-900'
                          }`}
                        >
                          {state.privacyMode
                            ? '••••••'
                            : `${isCredit ? '+' : '-'}${formatToman(tx.amountToman)}`}
                        </span>
                        <span className="text-[10px] text-gray-400">تومان</span>
                      </div>
                    </div>
                  );
                })
                )}
              </div>
            </section>
          </div>
        )}

        {/* ===================================================================
            TAB 2: ANALYTICS (تحلیل مخارج — _3/code.html)
           =================================================================== */}
        {activeTab === 'ANALYTICS' && (
          <div className="flex flex-col w-full gap-4">
            {/* کپسول انتخاب بازه زمانی */}
            <section className="flex items-center justify-between gap-1.5 p-1 bg-surface-container-lowest rounded-full shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)]">
              {(
                [
                  { id: 'daily', label: 'روزانه' },
                  { id: 'weekly', label: 'هفتگی' },
                  { id: 'monthly', label: 'ماهانه' },
                  { id: 'yearly', label: 'سالانه' },
                ] as const
              ).map((p) => {
                const active = analyticsPeriod === p.id;
                return (
                  <button
                    key={p.id}
                    onClick={() => setAnalyticsPeriod(p.id)}
                    className={`flex-1 py-2 rounded-full text-label-md transition-all ${
                      active
                        ? 'bg-primary-container text-on-primary shadow-[0_4px_12px_rgba(31,35,43,0.18)] font-semibold'
                        : 'text-on-surface-variant hover:text-on-surface'
                    }`}
                    type="button"
                  >
                    {p.label}
                  </button>
                );
              })}
            </section>

            {/* کارت اصلی بنتو: تراز درآمد و هزینه */}
            <section className="relative bg-surface-container-lowest rounded-[28px] p-5 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04),0_2px_6px_-1px_rgba(31,35,43,0.02)] overflow-hidden">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <span className="w-8 h-8 rounded-full bg-secondary-fixed flex items-center justify-center text-on-secondary-fixed">
                    <span className="material-symbols-outlined text-[18px]">query_stats</span>
                  </span>
                  <div>
                    <span className="block text-label-sm text-on-surface-variant">وضعیت تراز</span>
                    <span className="block text-headline-sm text-on-surface font-bold">
                      {periodLabelFa}
                    </span>
                  </div>
                </div>
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-container-low text-label-sm text-secondary-container font-semibold">
                  <span className="material-symbols-outlined text-[14px]">bolt</span>
                  به‌روز شد
                </span>
              </div>

              {/* گرید مبالغ درآمد و هزینه */}
              <div className="grid grid-cols-2 gap-3 mb-5">
                {/* ستون هزینه */}
                <div className="bg-surface-container-low/60 rounded-[22px] p-3.5 flex flex-col justify-between">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-label-sm text-on-surface-variant">مجموع مخارج</span>
                    <span className="w-6 h-6 rounded-full bg-tertiary-fixed flex items-center justify-center text-on-tertiary-container">
                      <span className="material-symbols-outlined text-[14px]">arrow_outward</span>
                    </span>
                  </div>
                  <div>
                    <div className="flex items-baseline gap-1">
                      <span className="text-[26px] leading-tight text-on-surface font-extrabold tracking-tight num-tabular">
                        {state.privacyMode
                          ? '•••••'
                          : formatToman(state.transactions.filter((t) => t.direction === 'DEBIT').reduce((s, t) => s + t.amountToman, 0))}
                      </span>
                      <span className="text-label-sm text-on-surface-variant">تومان</span>
                    </div>
                    <div className="inline-flex items-center gap-1 mt-1 text-on-tertiary-container text-label-sm">
                      <span className="material-symbols-outlined text-[14px]">trending_down</span>
                      <span>۱۲٪ کمتر از دوره پیش</span>
                    </div>
                  </div>
                </div>

                {/* ستون درآمد */}
                <div className="bg-surface-container-low/60 rounded-[22px] p-3.5 flex flex-col justify-between">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-label-sm text-on-surface-variant">دریافتی ناخالص</span>
                    <span className="w-6 h-6 rounded-full bg-secondary-fixed flex items-center justify-center text-secondary">
                      <span className="material-symbols-outlined text-[14px]">south_west</span>
                    </span>
                  </div>
                  <div>
                    <div className="flex items-baseline gap-1">
                      <span className="text-[26px] leading-tight text-on-surface font-extrabold tracking-tight num-tabular">
                        {state.privacyMode
                          ? '•••••'
                          : formatToman(state.transactions.filter((t) => t.direction === 'CREDIT').reduce((s, t) => s + t.amountToman, 0))}
                      </span>
                      <span className="text-label-sm text-on-surface-variant">تومان</span>
                    </div>
                    <div className="inline-flex items-center gap-1 mt-1 text-secondary text-label-sm">
                      <span className="material-symbols-outlined text-[14px]">verified</span>
                      <span>۲ واریزی موفق</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* نمودار ستونی کپسولی روزهای هفته */}
              <div className="pt-3">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-label-sm text-on-surface-variant font-medium">
                    ریتم جریان نقدینگی روزانه
                  </span>
                  <span className="text-label-sm text-on-surface-variant">میانگین: ۲.۶ م</span>
                </div>
                <div className="grid grid-cols-7 gap-2 items-end h-32 px-1">
                  {[
                    { day: 'ش', h: '38%', peak: false },
                    { day: 'ی', h: '55%', peak: false },
                    { day: 'د', h: '28%', peak: false },
                    { day: 'س', h: '68%', peak: false },
                    { day: 'چ', h: '45%', peak: false },
                    { day: 'پ', h: '95%', peak: true },
                    { day: 'ج', h: '32%', peak: false },
                  ].map((bar) => (
                    <div
                      key={bar.day}
                      className="relative flex flex-col items-center gap-1.5 h-full justify-end group"
                    >
                      {bar.peak && (
                        <span className="absolute -top-6 px-1.5 py-0.5 rounded-full bg-on-tertiary-container text-on-tertiary text-[9px] font-bold shadow-sm whitespace-nowrap animate-bounce">
                          اوج هفته
                        </span>
                      )}
                      <div
                        style={{ height: bar.h }}
                        className={`w-full rounded-full transition-all ${
                          bar.peak
                            ? 'bg-primary-container shadow-[0_4px_12px_rgba(31,35,43,0.2)]'
                            : 'bg-surface-container-high group-hover:bg-secondary-container'
                        }`}
                      />
                      <span
                        className={`text-label-sm ${
                          bar.peak ? 'text-on-surface font-bold' : 'text-on-surface-variant'
                        }`}
                      >
                        {bar.day}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </section>

            {/* کارت بینش هوشمند گردآول و رفتارسنجی مالی */}
            <section className="relative bg-surface-container-lowest rounded-[28px] p-5 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] overflow-hidden">
              <div className="flex items-start justify-between gap-3 mb-3">
                <div className="flex items-center gap-2.5">
                  <div className="w-9 h-9 rounded-2xl bg-secondary-fixed flex items-center justify-center text-secondary">
                    <span className="material-symbols-outlined text-[20px]">psychology</span>
                  </div>
                  <div>
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-secondary-fixed/50 text-secondary text-label-sm font-bold">
                      مخصوص تو
                    </span>
                    <h2 className="text-headline-sm text-on-surface font-bold mt-0.5">
                      فرصت صرفه‌جویی ۷۵۰ هزار تومانی
                    </h2>
                  </div>
                </div>
                <div className="w-12 h-12 rounded-full overflow-hidden shrink-0 bg-surface-container-low flex items-center justify-center">
                  <img
                    className="w-full h-full object-cover"
                    alt="Mascot"
                    src="https://lh3.googleusercontent.com/aida-public/AB6AXuAb_71ZsS4ZUDedOygZkM_099G0-bwS4KVR3Vugrc5XJa1WK_oz5c3oH6Vd0ljdmF4auRvA6iqAWuFS2qSYCzl62KRMlAkZrOu7pKkW_j_2r0lCPHmERkPm7BaR-DaxguefdIXW_Dc8fZzcM8jwF5GGWxs6D4FblpYAS66YQFTFqs6FfPg-4-w1y_BodbiEvFATTQPiop_kBQCOCuokpGPDSoLg77veHSBtvbCrJ_9X"
                  />
                </div>
              </div>
              <p className="text-body-sm text-on-surface-variant leading-relaxed mb-4">
                سفارش‌های کافه‌گردی شما در روزهای پنج‌شنبه ۴۰٪ فراتر از حد تعادل ثبت شده است. با
                فعال‌سازی سقف هوشمند هفتگی، پس‌انداز ماهانه شما به ۳,۱۰۰,۰۰۰ تومان افزایش می‌یابد.
              </p>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => showToast('سقف هوشمند بودجه کافه و تفریح فعال شد')}
                  className="flex-1 py-2.5 px-4 rounded-full bg-primary-container text-on-primary text-label-md font-semibold flex items-center justify-center gap-1.5 shadow-[0_4px_14px_rgba(31,35,43,0.15)] active:scale-95 transition-all"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[16px]">tune</span>
                  <span>فعال‌سازی سقف بودجه</span>
                </button>
                <button
                  onClick={() => setIsCalcOpen(true)}
                  aria-label="تنظیمات بیشتر"
                  className="py-2.5 px-3.5 rounded-full bg-surface-container-low text-on-surface text-label-md flex items-center justify-center hover:bg-surface-container transition-all"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[18px]">more_horiz</span>
                </button>
              </div>
            </section>

            {/* کارت توزیع و سهم مخارج با چارت دونات */}
            <section className="bg-surface-container-lowest rounded-[28px] p-5 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)]">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <span className="text-label-sm text-on-surface-variant block">
                    تقسیم‌بندی سبد مخارج
                  </span>
                  <h3 className="text-headline-sm text-on-surface font-bold">دسته‌بندی‌های فعال</h3>
                </div>
                <button
                  onClick={() => setIsCalcOpen(true)}
                  className="text-label-sm text-secondary-container font-semibold flex items-center gap-0.5"
                  type="button"
                >
                  <span>گزارش کامل</span>
                  <span className="material-symbols-outlined text-[16px]">chevron_left</span>
                </button>
              </div>

              {/* نمودار حلقه‌ای و مرکز داده */}
              <div className="flex items-center justify-center py-2 mb-4">
                <div className="relative w-40 h-40 flex items-center justify-center">
                  <svg className="w-full h-full -rotate-90" viewBox="0 0 120 120">
                    <circle
                      className="text-surface-container-low"
                      cx="60"
                      cy="60"
                      fill="none"
                      r="48"
                      stroke="currentColor"
                      strokeWidth="12"
                    />
                    <circle
                      cx="60"
                      cy="60"
                      fill="none"
                      r="48"
                      stroke="#1f232b"
                      strokeDasharray="105.5 301.6"
                      strokeDashoffset="0"
                      strokeLinecap="round"
                      strokeWidth="12"
                    />
                    <circle
                      cx="60"
                      cy="60"
                      fill="none"
                      r="48"
                      stroke="#ea5c3c"
                      strokeDasharray="72.4 301.6"
                      strokeDashoffset="-108"
                      strokeLinecap="round"
                      strokeWidth="12"
                    />
                    <circle
                      cx="60"
                      cy="60"
                      fill="none"
                      r="48"
                      stroke="#2170e4"
                      strokeDasharray="45.2 301.6"
                      strokeDashoffset="-182"
                      strokeLinecap="round"
                      strokeWidth="12"
                    />
                    <circle
                      cx="60"
                      cy="60"
                      fill="none"
                      r="48"
                      stroke="#f59e0b"
                      strokeDasharray="42.2 301.6"
                      strokeDashoffset="-230"
                      strokeLinecap="round"
                      strokeWidth="12"
                    />
                    <circle
                      cx="60"
                      cy="60"
                      fill="none"
                      r="48"
                      stroke="#14b8a6"
                      strokeDasharray="36.2 301.6"
                      strokeDashoffset="-274"
                      strokeLinecap="round"
                      strokeWidth="12"
                    />
                  </svg>
                  <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
                    <span className="text-label-sm text-on-surface-variant font-medium">
                      سهم اعظم
                    </span>
                    <span className="text-headline-md text-on-surface font-black mt-0.5">۳۵٪</span>
                    <span className="text-label-sm text-on-surface font-semibold">
                      مسکن و قبوض
                    </span>
                  </div>
                </div>
              </div>

              {/* لیست دسته‌ها با کپسول‌های آماری */}
              <div className="space-y-2.5">
                {[
                  {
                    title: 'مسکن، اجاره و قبوض',
                    amount: '۶,۴۱۰,۰۰۰ تومان',
                    pct: '۳۵٪',
                    icon: 'apartment',
                    iconClass: 'bg-primary-container text-on-primary',
                    badgeClass: 'bg-primary-container text-on-primary',
                  },
                  {
                    title: 'خوراک و سوپرمارکت',
                    amount: '۴,۳۹۶,۰۰۰ تومان',
                    pct: '۲۴٪',
                    icon: 'restaurant',
                    iconClass: 'bg-tertiary-fixed text-on-tertiary-container',
                    badgeClass: 'bg-tertiary-fixed text-on-tertiary-container',
                  },
                  {
                    title: 'تعهدات، قسط و بیمه',
                    amount: '۲,۷۴۸,۰۰۰ تومان',
                    pct: '۱۵٪',
                    icon: 'account_balance_wallet',
                    iconClass: 'bg-secondary-fixed text-secondary',
                    badgeClass: 'bg-secondary-fixed text-secondary',
                  },
                  {
                    title: 'حمل‌ونقل و سوخت',
                    amount: '۲,۵۶۴,۰۰۰ تومان',
                    pct: '۱۴٪',
                    icon: 'directions_car',
                    iconClass: 'bg-surface-container-high text-on-surface',
                    badgeClass: 'bg-surface-container text-on-surface',
                  },
                  {
                    title: 'تفریح و سرگرمی',
                    amount: '۲,۱۹۸,۰۰۰ تومان',
                    pct: '۱۲٪',
                    icon: 'attractions',
                    iconClass: 'bg-surface-container-high text-on-surface',
                    badgeClass: 'bg-surface-container text-on-surface',
                  },
                ].map((cat) => (
                  <div
                    key={cat.title}
                    className="flex items-center justify-between p-2.5 rounded-2xl bg-surface-container-low/50"
                  >
                    <div className="flex items-center gap-2.5">
                      <span
                        className={`w-8 h-8 rounded-full ${cat.iconClass} flex items-center justify-center`}
                      >
                        <span className="material-symbols-outlined text-[16px]">{cat.icon}</span>
                      </span>
                      <div>
                        <span className="block text-body-sm text-on-surface font-bold">
                          {cat.title}
                        </span>
                        <span className="block text-label-sm text-on-surface-variant num-tabular">
                          {state.privacyMode ? '•••••• تومان' : cat.amount}
                        </span>
                      </div>
                    </div>
                    <span
                      className={`px-2.5 py-1 rounded-full ${cat.badgeClass} text-label-sm font-bold`}
                    >
                      {cat.pct}
                    </span>
                  </div>
                ))}
              </div>
            </section>

            {/* بنر ویژه رسیدگی سریع به پیامک‌های بانکی */}
            <section
              onClick={() => openQuickAdd('SMS')}
              className="cursor-pointer bg-surface-container-lowest rounded-[28px] p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] flex items-center justify-between gap-3 active:scale-[0.99] transition"
            >
              <div className="w-14 h-14 rounded-2xl overflow-hidden shrink-0 bg-surface-container-low">
                <img
                  className="w-full h-full object-cover"
                  alt="Bank SMS Sync"
                  src="https://lh3.googleusercontent.com/aida-public/AB6AXuApjXCHnnzBe6tqnz9nVCtcT3fiAMrHsg4ejZLkUY4aXXuTMDx-dwr_mNG7MQACbZli_qqAj1wD2qdeWcBmyyLubB8cDljwr3nyUYBODdr_Iv84eag2D1iVo1oVPvwolhLI-BLEXUVH8MJvn1DNYVj5j_za9jsfJ6qxLbdjG9GeG-NzAQAhA97-ucSjjp6b0-IlmQSd1pVZyHM-uJsNUMIOxYBAdt13-kFItBc7mJVY"
                />
              </div>
              <div className="flex-1 min-w-0">
                <span className="block text-body-sm text-on-surface font-bold truncate">
                  ثبت آسان تراکنش اسنپ و فاکتور
                </span>
                <span className="block text-label-sm text-on-surface-variant">
                  همگام‌سازی پیامک‌های بانکی در ثانیه
                </span>
              </div>
              <button
                className="w-10 h-10 rounded-full bg-surface-container-low flex items-center justify-center text-on-surface hover:bg-surface-container active:scale-95 transition-all shrink-0"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">sync</span>
              </button>
            </section>

            {/* لیست تراکنش‌های اخیر پرهزینه */}
            <section className="bg-surface-container-lowest rounded-[28px] p-5 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] mb-2">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <span className="text-label-sm text-on-surface-variant block">
                    ردگیری جریان پول
                  </span>
                  <h3 className="text-headline-sm text-on-surface font-bold">
                    تراکنش‌های عمده این هفته
                  </h3>
                </div>
                <button
                  onClick={() => setTxnFilterModalOpen((v) => !v)}
                  aria-label="فیلتر تراکنش‌ها"
                  className="w-8 h-8 rounded-full bg-surface-container-low flex items-center justify-center text-on-surface"
                  type="button"
                >
                  <span className="material-symbols-outlined text-[18px]">filter_list</span>
                </button>
              </div>

              {txnFilterModalOpen && (
                <div className="mb-3">
                  <input
                    type="text"
                    value={txnSearchQuery}
                    onChange={(e) => setTxnSearchQuery(e.target.value)}
                    placeholder="جستجو در نام فروشگاه یا دسته‌بندی..."
                    className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2 text-xs text-on-surface focus:outline-none"
                  />
                </div>
              )}

              <div className="space-y-3">
                {[
                  {
                    title: 'هایپرمی پالادیوم',
                    sub: 'پنج‌شنبه ۲۴ خرداد • ۱۸:۴۵',
                    amount: '-۱,۴۵۰,۰۰۰',
                    icon: 'local_mall',
                  },
                  {
                    title: 'کافه روبرتو (دورهمی کاری)',
                    sub: 'چهارشنبه ۲۳ خرداد • ۲۰:۱۵',
                    amount: '-۵۸۰,۰۰۰',
                    icon: 'coffee',
                  },
                  {
                    title: 'جایگاه سوخت ولنجک',
                    sub: 'سه‌شنبه ۲۲ خرداد • ۰۹:۳۰',
                    amount: '-۲۲۰,۰۰۰',
                    icon: 'local_gas_station',
                  },
                  ...state.transactions.slice(0, 3).map((t) => ({
                    title: t.merchant,
                    sub: `امروز • ${t.categoryFa}`,
                    amount: `${t.direction === 'CREDIT' ? '+' : '-'}${formatToman(t.amountToman)}`,
                    icon: t.direction === 'CREDIT' ? 'payments' : 'receipt',
                  })),
                ]
                  .filter((item) =>
                    txnSearchQuery.trim() === '' ? true : item.title.includes(txnSearchQuery)
                  )
                  .map((item, i) => (
                    <div
                      key={i}
                      className="flex items-center justify-between p-2 rounded-2xl hover:bg-surface-container-low/40 transition-colors"
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-surface-container-low flex items-center justify-center text-on-surface">
                          <span className="material-symbols-outlined text-[20px]">{item.icon}</span>
                        </div>
                        <div>
                          <span className="block text-body-sm text-on-surface font-bold">
                            {item.title}
                          </span>
                          <span className="block text-label-sm text-on-surface-variant">
                            {item.sub}
                          </span>
                        </div>
                      </div>
                      <div className="text-left">
                        <span className="block text-body-lg text-on-surface font-extrabold tracking-tight num-tabular dir-ltr">
                          {state.privacyMode ? '••••••' : item.amount}
                        </span>
                        <span className="block text-label-sm text-on-surface-variant">تومان</span>
                      </div>
                    </div>
                  ))}
              </div>
            </section>
          </div>
        )}

        {/* ===================================================================
            TAB 3: COMMITMENTS & CHEQUES (اقساط و چک — _2/code.html)
           =================================================================== */}
        {activeTab === 'COMMITMENTS' && (
          <div className="flex flex-col w-full gap-5 pb-10">
            {/* Dynamic Reminder Capsule Banner */}
            {urgentCommitment && (
              <aside
                aria-label="هشدار سررسید نزدیک"
                className="relative overflow-hidden flex items-center justify-between gap-3 p-3.5 bg-error-container/70 rounded-lg shadow-[0_4px_20px_-2px_rgba(186,26,26,0.06)] transition-transform"
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <div className="w-9 h-9 rounded-full bg-surface-container-lowest flex items-center justify-center shrink-0 shadow-sm text-error">
                    <span className="material-symbols-outlined material-symbols-filled text-[20px]">
                      notifications_active
                    </span>
                  </div>
                  <div className="flex flex-col min-w-0">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <span className="text-label-md text-on-error-container">
                        سررسید نزدیک: {urgentCommitment.title}
                      </span>
                    </div>
                    <p className="text-body-sm text-on-error-container/80 truncate">
                      {urgentCommitment.provider} • {formatToman(urgentCommitment.remainingAmountToman)} تومان
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => {
                    setState((prev) => ({
                      ...prev,
                      commitments: prev.commitments.map((c) =>
                        c.id === urgentCommitment.id ? { ...c, isSettled: true } : c
                      ),
                    }));
                    showToast(`پرداخت ${urgentCommitment.title} با موفقیت ثبت شد`);
                  }}
                  className="shrink-0 px-3.5 py-1.5 rounded-full bg-primary text-on-primary text-label-sm shadow-[0_4px_12px_rgba(9,13,21,0.18)] active:scale-95 transition-transform"
                  type="button"
                >
                  ثبت تسویه
                </button>
              </aside>
            )}

            {/* Total Commitments Bento Pod */}
            <section className="bg-surface-container-lowest p-5 rounded-lg shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04),0_2px_6px_-1px_rgba(31,35,43,0.02)] flex flex-col gap-4">
              <div className="flex items-start justify-between gap-2">
                <div className="flex flex-col">
                  <span className="text-label-md text-on-surface-variant">
                    کل بدهی و تعهدات فعال
                  </span>
                  <div className="flex items-baseline gap-1.5 mt-1">
                    <span className="text-display-hero-mobile text-on-surface tracking-tight num-tabular">
                      {state.privacyMode ? '••••••••' : formatToman(remainingCommitmentsToman)}
                    </span>
                    <span className="text-label-md text-outline">تومان</span>
                  </div>
                </div>
                <div className="w-11 h-11 rounded-full bg-surface-container-low flex items-center justify-center text-secondary">
                  <span className="material-symbols-outlined text-[24px]">
                    account_balance_wallet
                  </span>
                </div>
              </div>

              {/* Monthly Progress Track */}
              <div className="p-3.5 bg-surface-container-low rounded-[1.25rem] flex flex-col gap-2">
                <div className="flex items-center justify-between text-label-md">
                  <span className="text-on-surface">وضعیت تسویه کل تعهدات</span>
                  <span className="text-secondary font-bold">
                    {totalCommitmentsToman > 0
                      ? `${toFaDigits(commitmentsProgressPct)}٪ تسویه شده`
                      : 'بدون تعهد فعال'}
                  </span>
                </div>
                <div className="w-full h-3 rounded-full bg-surface-container-highest overflow-hidden p-0.5 flex">
                  <div
                    className="h-full rounded-full bg-secondary-container transition-all duration-500"
                    style={{ width: `${Math.max(2, commitmentsProgressPct)}%` }}
                  />
                </div>
                <div className="flex items-center justify-between text-label-sm text-outline pt-0.5">
                  <span>پرداخت‌شده: {formatToman(paidCommitmentsToman)} ت</span>
                  <span>باقی‌مانده: {formatToman(remainingCommitmentsToman)} ت</span>
                </div>
              </div>

              {/* Micro Stats Pods */}
              <div className="grid grid-cols-3 gap-2">
                <div className="flex flex-col items-center justify-center p-2.5 rounded-DEFAULT bg-surface-container-low/70">
                  <span className="text-headline-sm text-on-surface">
                    {toFaDigits(state.commitments.filter((c) => !c.isSettled).length)}
                  </span>
                  <span className="text-label-sm text-on-surface-variant">تعهد فعال</span>
                </div>
                <div className="flex flex-col items-center justify-center p-2.5 rounded-DEFAULT bg-surface-container-low/70">
                  <span className="text-headline-sm text-on-surface">
                    {toFaDigits(state.commitments.filter((c) => !c.isSettled && c.kind === 'CHECK').length)}
                  </span>
                  <span className="text-label-sm text-on-surface-variant">فقره چک صیاد</span>
                </div>
                <div className="flex flex-col items-center justify-center p-2.5 rounded-DEFAULT bg-surface-container-low/70">
                  <span className="text-headline-sm text-secondary">
                    {state.commitments.length === 0 ? 'A+' : 'A'}
                  </span>
                  <span className="text-label-sm text-on-surface-variant">امتیاز خوش‌حسابی</span>
                </div>
              </div>
            </section>

            {/* Filter Pills Bar */}
            <nav
              aria-label="فیلتر دسته‌بندی تعهدات"
              className="flex items-center gap-2 overflow-x-auto no-scrollbar py-0.5"
            >
              {[
                { id: 'all', label: `همه تعهدات (${toFaDigits(state.commitments.length)})` },
                { id: 'bank', label: `اقساط بانکی (${toFaDigits(state.commitments.filter((c) => c.kind === 'LOAN' || c.kind === 'INSTALLMENT').length)})` },
                { id: 'cheque', label: `چک‌های صیادی (${toFaDigits(state.commitments.filter((c) => c.kind === 'CHECK').length)})` },
                { id: 'cards', label: `کارت‌های بانکی (${toFaDigits(state.accounts.length)})` },
              ].map((f) => {
                const active = commitmentFilter === f.id;
                return (
                  <button
                    key={f.id}
                    onClick={() =>
                      setCommitmentFilter(f.id as 'all' | 'bank' | 'cheque' | 'cards')
                    }
                    className={`px-4 py-2 rounded-full text-label-md shrink-0 transition-all ${
                      active
                        ? 'bg-primary text-on-primary shadow-sm'
                        : 'bg-surface-container-lowest text-on-surface-variant hover:text-on-surface shadow-[0_2px_8px_rgba(0,0,0,0.03)]'
                    }`}
                    type="button"
                  >
                    {f.label}
                  </button>
                );
              })}
            </nav>

            {/* Commitments Stream */}
            {commitmentFilter !== 'cards' ? (
              <section className="flex flex-col gap-3">
                {(() => {
                  const filtered = state.commitments.filter((c) => {
                    if (commitmentFilter === 'bank') return c.kind === 'LOAN' || c.kind === 'INSTALLMENT';
                    if (commitmentFilter === 'cheque') return c.kind === 'CHECK';
                    return true;
                  });

                  if (filtered.length === 0) {
                    return (
                      <div className="flex flex-col items-center justify-center p-8 bg-surface-container-lowest rounded-lg text-center gap-3">
                        <span className="material-symbols-outlined text-[36px] text-outline">description</span>
                        <h3 className="text-body-lg text-on-surface font-bold">هیچ تعهد یا قسطی یافت نشد</h3>
                        <p className="text-body-sm text-outline max-w-xs">
                          تعهدات و اقساط بانکی شما پس از دریافت پیامک‌های بانکی یا با ثبت دستی در این قسمت نمایش می‌یابند.
                        </p>
                        <button
                          onClick={() => openQuickAdd('COMMITMENT')}
                          className="px-4 py-2 rounded-full bg-primary text-on-primary text-label-md font-semibold"
                          type="button"
                        >
                          + ثبت تعهد جدید
                        </button>
                      </div>
                    );
                  }

                  return filtered.map((c) => {
                    const progress = c.totalAmountToman > 0
                      ? Math.min(100, Math.round(((c.totalAmountToman - c.remainingAmountToman) / c.totalAmountToman) * 100))
                      : 0;
                    return (
                      <article
                        key={c.id}
                        className="bg-surface-container-lowest p-4 rounded-lg shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] flex flex-col gap-3"
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="flex items-center gap-3">
                            <div className="w-10 h-10 rounded-full bg-surface-container-low flex items-center justify-center text-primary shrink-0">
                              <span className="material-symbols-outlined text-[22px]">
                                {c.kind === 'CHECK' ? 'check_circle' : 'real_estate_agent'}
                              </span>
                            </div>
                            <div className="flex flex-col">
                              <h2 className="text-body-lg text-on-surface">{c.title}</h2>
                              <span className="text-body-sm text-outline">{c.provider}</span>
                            </div>
                          </div>
                          <div className="text-left flex flex-col items-end">
                            <span className="text-headline-sm text-on-surface num-tabular">
                              {state.privacyMode ? '••••••' : formatToman(c.remainingAmountToman)}
                            </span>
                            <span className="text-label-sm text-outline">تومان باقیمانده</span>
                          </div>
                        </div>

                        <div className="p-3 bg-surface-container-low/60 rounded-DEFAULT flex flex-col gap-2">
                          <div className="flex items-center justify-between text-label-sm text-on-surface-variant">
                            <span>
                              {c.isSettled ? 'تسویه شده' : `پیشرفت تسویه (${toFaDigits(progress)}٪)`}
                            </span>
                            <span className="text-secondary font-semibold">
                              {c.isSettled ? 'تکمیل شد' : 'در جریان'}
                            </span>
                          </div>
                          <div className="w-full h-2 rounded-full bg-surface-container-highest overflow-hidden">
                            <div
                              className="h-full rounded-full bg-secondary-container"
                              style={{ width: `${Math.max(2, progress)}%` }}
                            />
                          </div>
                        </div>

                        <div className="flex items-center justify-between gap-2 pt-1">
                          <button
                            onClick={() => {
                              setState((prev) => ({
                                ...prev,
                                commitments: prev.commitments.map((item) =>
                                  item.id === c.id ? { ...item, isSettled: !item.isSettled } : item
                                ),
                              }));
                              showToast(c.isSettled ? 'به حالت در جریان برگشت' : 'به عنوان تسویه‌شده علامت‌گذاری شد');
                            }}
                            className="flex items-center gap-1.5 px-4 py-1.5 rounded-full bg-primary text-on-primary text-label-md shadow-sm active:scale-95 transition-transform"
                            type="button"
                          >
                            <span>{c.isSettled ? 'بازگردانی به فعال' : 'ثبت تسویه'}</span>
                          </button>
                        </div>
                      </article>
                    );
                  });
                })()}
              </section>
            ) : (
              /* Bank Cards & SHEBA List inside Tab 3 when 'cards' filter is active */
              <section className="flex flex-col gap-3">
                {state.accounts.length === 0 ? (
                  <div className="flex flex-col items-center justify-center p-8 bg-surface-container-lowest rounded-lg text-center gap-3">
                    <span className="material-symbols-outlined text-[36px] text-outline">credit_card</span>
                    <h3 className="text-body-lg text-on-surface font-bold">هیچ کارت بانکی ثبت نشده است</h3>
                    <p className="text-body-sm text-outline max-w-xs">
                      کارت‌های بانکی به صورت خودکار از پیامک‌های واریز/برداشت استخراج شده یا می‌توانید به صورت دستی کارت خود را اضافه کنید.
                    </p>
                  </div>
                ) : (
                  state.accounts.map((acc) => (
                  <article
                    key={acc.id}
                    className="bg-surface-container-lowest p-4 rounded-lg shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] flex flex-col gap-3"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-secondary-fixed flex items-center justify-center text-secondary font-black text-xs">
                          {acc.bankNameFa.slice(0, 2)}
                        </div>
                        <div>
                          <h3 className="text-body-lg text-on-surface">{acc.bankNameFa}</h3>
                          <span className="text-body-sm text-outline">{acc.nickname}</span>
                        </div>
                      </div>
                      <div className="text-left">
                        <span className="text-headline-sm text-on-surface num-tabular">
                          {formatToman(acc.balanceToman, state.privacyMode)}
                        </span>
                        <span className="block text-label-sm text-outline">تومان</span>
                      </div>
                    </div>

                    <div className="p-3 rounded-2xl bg-surface-container-low flex items-center justify-between">
                      <span dir="ltr" className="font-mono text-sm font-bold text-on-surface">
                        {formatCardPan(acc.pan, false, state.privacyMode)}
                      </span>
                      <button
                        onClick={() => handleCopy(acc.pan, `pan_${acc.id}`, 'شماره کارت')}
                        className="px-3 py-1 rounded-full bg-primary text-on-primary text-label-sm"
                        type="button"
                      >
                        {copiedId === `pan_${acc.id}` ? 'کپی شد ✓' : 'کپی کارت'}
                      </button>
                    </div>

                    {acc.sheba && (
                      <div className="px-3 py-2 rounded-xl bg-surface-container-low/60 flex items-center justify-between text-xs">
                        <span dir="ltr" className="font-mono text-[11px] text-on-surface-variant">
                          {state.privacyMode ? 'IR••••••••••••••••••••••••' : formatSheba(acc.sheba)}
                        </span>
                        <button
                          onClick={() => handleCopy(acc.sheba!, `sheba_${acc.id}`, 'شماره شبا')}
                          className="text-secondary font-bold text-label-sm"
                          type="button"
                        >
                          کپی شبا
                        </button>
                      </div>
                    )}
                  </article>
                ))
                )}
                <button
                  onClick={() => openQuickAdd('CARD')}
                  className="w-full py-3 rounded-full bg-surface-container-lowest border border-gray-200 text-on-surface font-bold text-xs"
                  type="button"
                >
                  + افزودن کارت بانکی جدید
                </button>
              </section>
            )}

            {/* Smart Alert Settings Pod */}
            <section className="bg-surface-container-lowest p-5 rounded-lg shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] flex flex-col gap-3.5">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[20px] text-secondary">tune</span>
                <h3 className="text-body-lg text-on-surface">
                  تنظیمات یادآور و هشدارهای هوشمند
                </h3>
              </div>

              {/* Toggle Item 1 */}
              <div className="flex items-center justify-between py-2">
                <div className="flex flex-col">
                  <span className="text-body-md text-on-surface">
                    ارسال پیامک یادآوری ۳ روز قبل
                  </span>
                  <span className="text-body-sm text-outline">
                    به شماره ثبت‌شده در سامانه بانکی
                  </span>
                </div>
                <button
                  onClick={() => setSmsReminder3Days((v) => !v)}
                  aria-checked={smsReminder3Days}
                  className={`w-12 h-7 rounded-full relative transition-colors cursor-pointer shrink-0 focus:outline-none ${
                    smsReminder3Days ? 'bg-primary' : 'bg-surface-container-high'
                  }`}
                  role="switch"
                  type="button"
                >
                  <span
                    className={`absolute top-1 left-1 w-5 h-5 rounded-full bg-surface-container-lowest shadow-sm transition-transform ${
                      smsReminder3Days ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>

              {/* Toggle Item 2 */}
              <div className="flex items-center justify-between py-2">
                <div className="flex flex-col">
                  <span className="text-body-md text-on-surface">
                    هشدار صوتی سررسید در روز موعد
                  </span>
                  <span className="text-body-sm text-outline">ساعت ۹:۰۰ صبح با اولویت بالا</span>
                </div>
                <button
                  onClick={() => setVoiceAlarmDueDay((v) => !v)}
                  aria-checked={voiceAlarmDueDay}
                  className={`w-12 h-7 rounded-full relative transition-colors cursor-pointer shrink-0 focus:outline-none ${
                    voiceAlarmDueDay ? 'bg-primary' : 'bg-surface-container-high'
                  }`}
                  role="switch"
                  type="button"
                >
                  <span
                    className={`absolute top-1 left-1 w-5 h-5 rounded-full bg-surface-container-lowest shadow-sm transition-transform ${
                      voiceAlarmDueDay ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>
            </section>

            {/* Big Floating Action Pod: Register New Commitment */}
            <section className="pt-2">
              <button
                onClick={() => openQuickAdd('COMMITMENT')}
                className="w-full h-14 rounded-full bg-primary text-on-primary flex items-center justify-center gap-2 shadow-[0_8px_24px_-4px_rgba(9,13,21,0.28)] active:scale-[0.98] transition-all"
                type="button"
              >
                <span className="material-symbols-outlined text-[22px]">add_circle</span>
                <span className="text-body-lg">ثبت قسط یا چک جدید</span>
              </button>
            </section>
          </div>
        )}

        {/* ===================================================================
            TAB 4: GOALS & SAVINGS (پس‌انداز و اهداف — _1/code.html)
           =================================================================== */}
        {activeTab === 'GOALS' && (
          <div className="flex flex-col w-full gap-4">
            {/* Overview Bento Metric Card */}
            <section className="bg-surface-container-lowest rounded-lg p-5 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] relative overflow-hidden transition-all">
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <span className="w-8 h-8 rounded-full bg-secondary-fixed flex items-center justify-center text-secondary">
                    <span className="material-symbols-outlined text-[18px]">
                      account_balance_wallet
                    </span>
                  </span>
                  <span className="text-label-md text-on-surface-variant">
                    کل موجودی پس‌انداز و رشد
                  </span>
                </div>
                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-container-low text-secondary text-label-sm">
                  <span className="material-symbols-outlined text-[14px]">trending_up</span>
                  <span>+۲۸.۴٪ بازدهی</span>
                </span>
              </div>

              {/* Main Value Display */}
              <div className="flex items-baseline gap-1.5 mb-4">
                <span className="text-display-hero-mobile text-primary tracking-tight num-tabular">
                  {state.privacyMode
                    ? '••••••••'
                    : `${toFaDigits(totalSavingsMillion)},۰۰۰,۰۰۰`}
                </span>
                <span className="text-label-md text-outline">تومان</span>
              </div>

              {/* Multi-segmented Progress Bar */}
              <div className="space-y-2">
                <div className="w-full h-3 bg-surface-container-high rounded-full overflow-hidden flex flex-row-reverse p-0.5 gap-0.5">
                  <div
                    className="h-full bg-on-tertiary-container rounded-full transition-all duration-500"
                    style={{ width: '42%' }}
                    title="طلا و سکه ۴۲٪"
                  />
                  <div
                    className="h-full bg-secondary-container rounded-full transition-all duration-500"
                    style={{ width: '33%' }}
                    title="درآمد ثابت ۳۳٪"
                  />
                  <div
                    className="h-full bg-secondary-fixed-dim rounded-full transition-all duration-500"
                    style={{ width: '15%' }}
                    title="صندوق سهام ۱۵٪"
                  />
                  <div
                    className="h-full bg-primary-container rounded-full transition-all duration-500"
                    style={{ width: '10%' }}
                    title="رمزارز ۱۰٪"
                  />
                </div>

                {/* Segment Legends */}
                <div className="grid grid-cols-2 gap-2 pt-1 text-on-surface-variant text-label-sm">
                  <div className="flex items-center justify-between bg-surface-container-low px-2.5 py-1.5 rounded-full">
                    <div className="flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-on-tertiary-container" />
                      <span>طلا و سکه</span>
                    </div>
                    <span className="text-label-md text-primary">۴۲٪</span>
                  </div>
                  <div className="flex items-center justify-between bg-surface-container-low px-2.5 py-1.5 rounded-full">
                    <div className="flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-secondary-container" />
                      <span>درآمد ثابت</span>
                    </div>
                    <span className="text-label-md text-primary">۳۳٪</span>
                  </div>
                  <div className="flex items-center justify-between bg-surface-container-low px-2.5 py-1.5 rounded-full">
                    <div className="flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-secondary-fixed-dim" />
                      <span>صندوق سهام</span>
                    </div>
                    <span className="text-label-md text-primary">۱۵٪</span>
                  </div>
                  <div className="flex items-center justify-between bg-surface-container-low px-2.5 py-1.5 rounded-full">
                    <div className="flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full bg-primary-container" />
                      <span>رمزارز</span>
                    </div>
                    <span className="text-label-md text-primary">۱۰٪</span>
                  </div>
                </div>
              </div>
            </section>

            {/* Smart Micro-Savings Widget (Gordol Micro Habit Style) */}
            <section className="bg-surface-container-lowest rounded-lg p-5 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] relative">
              <div className="flex items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="w-11 h-11 rounded-full bg-tertiary-fixed flex items-center justify-center text-tertiary shadow-sm">
                    <span className="material-symbols-outlined text-[24px]">savings</span>
                  </div>
                  <div>
                    <div className="flex items-center gap-1.5">
                      <h2 className="text-headline-sm text-primary">قلک خودکار خرده‌خریدها</h2>
                      <span className="px-2 py-0.5 rounded-full bg-secondary-fixed text-on-secondary-fixed-variant text-label-sm">
                        هوشمند
                      </span>
                    </div>
                    <p className="text-body-sm text-on-surface-variant mt-0.5">
                      گرد کردن مانده کارت تا سقف ده هزار تومان
                    </p>
                  </div>
                </div>

                {/* Tactile Toggle Switch */}
                <button
                  onClick={() => setRoundupActive((v) => !v)}
                  aria-label="فعال یا غیرفعال سازی قلک خودکار"
                  className={`w-12 h-7 rounded-full p-1 transition-colors duration-300 relative flex items-center focus:outline-none ${
                    roundupActive ? 'bg-primary' : 'bg-outline-variant'
                  }`}
                  type="button"
                >
                  <span
                    className={`w-5 h-5 bg-surface-container-lowest rounded-full shadow-md transform transition-transform duration-300 flex items-center justify-center text-primary ${
                      roundupActive ? '-translate-x-5' : 'translate-x-0'
                    }`}
                  >
                    <span className="material-symbols-outlined text-[13px]">done</span>
                  </span>
                </button>
              </div>

              {/* Highlight Pod */}
              <div className="mt-4 pt-3.5 bg-surface-container-low rounded-xl p-3 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-secondary text-[20px]">
                    magic_button
                  </span>
                  <span className="text-label-md text-on-surface">پس‌انداز نامحسوس این ماه:</span>
                </div>
                <div className="flex items-baseline gap-1">
                  <span className="text-headline-sm text-primary num-tabular">
                    {roundupActive ? '۴۲۰,۰۰۰' : '۰'}
                  </span>
                  <span className="text-label-sm text-outline">تومان طلا</span>
                </div>
              </div>
            </section>

            {/* Filter Category Chips */}
            <section className="flex items-center gap-2 overflow-x-auto no-scrollbar py-1">
              {[
                { id: 'all', label: `همه اهداف (${toFaDigits(savingsGoals.length)})` },
                { id: 'short', label: 'کوتاه‌مدت' },
                { id: 'long', label: 'بزرگ و آینده‌ساز' },
                { id: 'emergency', label: 'اضطراری' },
                { id: 'portfolio', label: `سبد دارایی (${toFaDigits(state.holdings.length)})` },
              ].map((c) => {
                const active = goalFilter === c.id;
                return (
                  <button
                    key={c.id}
                    onClick={() =>
                      setGoalFilter(
                        c.id as 'all' | 'short' | 'long' | 'emergency' | 'portfolio'
                      )
                    }
                    className={`px-4 py-2 rounded-full text-label-md shadow-sm whitespace-nowrap active:scale-95 transition-all ${
                      active
                        ? 'bg-primary text-on-primary'
                        : 'bg-surface-container-lowest text-on-surface-variant hover:text-primary'
                    }`}
                    type="button"
                  >
                    {c.label}
                  </button>
                );
              })}
            </section>

            {/* Financial Goals Pods or Portfolio Holdings */}
            {goalFilter !== 'portfolio' ? (
              <section className="flex flex-col gap-3">
                <div className="flex items-center justify-between px-1">
                  <h3 className="text-headline-sm text-primary">اهداف فعال</h3>
                  <button
                    onClick={() => setGoalFilter('portfolio')}
                    className="text-label-md text-secondary hover:underline"
                    type="button"
                  >
                    مشاهده سبد دارایی‌ها
                  </button>
                </div>

                {savingsGoals.length === 0 ? (
                  <div className="flex flex-col items-center justify-center p-8 bg-surface-container-lowest rounded-lg text-center gap-3">
                    <span className="material-symbols-outlined text-[36px] text-outline">flag</span>
                    <h3 className="text-body-lg text-on-surface font-bold">هنوز هدف پس‌اندازی تعریف نشده است</h3>
                    <p className="text-body-sm text-outline max-w-xs">
                      برای پس‌انداز خرید خودرو، مسکن، سفر یا صندوق اضطراری، هدف مالی خود را مشخص کنید تا مسیر پیشرفت آن تحلیل شود.
                    </p>
                    <button
                      onClick={() => openQuickAdd('GOAL')}
                      className="px-4 py-2 rounded-full bg-primary text-on-primary text-label-md font-semibold"
                      type="button"
                    >
                      + تعریف اولین هدف پس‌انداز
                    </button>
                  </div>
                ) : (
                  savingsGoals
                    .filter((g) => (goalFilter === 'all' ? true : g.category === goalFilter))
                    .map((goal) => {
                    const pct = Math.min(
                      100,
                      Math.round((goal.currentMillion / Math.max(1, goal.targetMillion)) * 100)
                    );
                    const circumference = 113.1;
                    const dashOffset = circumference - (pct / 100) * circumference;

                    if (goal.category === 'emergency' && !goal.imageUrl) {
                      return (
                        <div
                          key={goal.id}
                          className="bg-surface-container-lowest rounded-lg p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] flex items-center justify-between gap-3"
                        >
                          <div className="flex items-center gap-3.5 min-w-0">
                            <div className="relative w-14 h-14 shrink-0 flex items-center justify-center">
                              <svg className="w-full h-full -rotate-90" viewBox="0 0 44 44">
                                <circle
                                  className="text-surface-container"
                                  cx="22"
                                  cy="22"
                                  fill="transparent"
                                  r="18"
                                  stroke="currentColor"
                                  strokeWidth="4.5"
                                />
                                <circle
                                  className="text-secondary-container transition-all duration-500"
                                  cx="22"
                                  cy="22"
                                  fill="transparent"
                                  r="18"
                                  stroke="currentColor"
                                  strokeDasharray={circumference}
                                  strokeDashoffset={dashOffset}
                                  strokeLinecap="round"
                                  strokeWidth="4.5"
                                />
                              </svg>
                              <span className="absolute text-label-md text-primary">
                                {toFaDigits(pct)}٪
                              </span>
                            </div>
                            <div className="min-w-0">
                              <h4 className="text-body-lg text-primary truncate">{goal.title}</h4>
                              <p className="text-body-sm text-on-surface-variant mt-0.5">
                                {toFaDigits(goal.currentMillion)} از{' '}
                                {toFaDigits(goal.targetMillion)} میلیون تومان
                              </p>
                              <span className="inline-block mt-1 text-label-sm text-secondary bg-surface-container-low px-2 py-0.5 rounded-full">
                                {goal.etaLabel}
                              </span>
                            </div>
                          </div>
                          <button
                            onClick={() => handleDepositToGoal(goal.id, 5)}
                            className="shrink-0 px-3.5 py-2 bg-primary text-on-primary rounded-full text-label-md flex items-center gap-1 active:scale-95 transition-all"
                            type="button"
                          >
                            <span className="material-symbols-outlined text-[16px]">add</span>
                            <span>واریز</span>
                          </button>
                        </div>
                      );
                    }

                    return (
                      <div
                        key={goal.id}
                        className="bg-surface-container-lowest rounded-lg p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] flex flex-col gap-3"
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-3 min-w-0">
                            {goal.imageUrl ? (
                              <div className="w-12 h-12 rounded-xl overflow-hidden shrink-0 bg-surface-container-low">
                                <img
                                  className="w-full h-full object-cover"
                                  alt={goal.title}
                                  src={goal.imageUrl}
                                />
                              </div>
                            ) : (
                              <div className="w-12 h-12 rounded-xl bg-secondary-fixed flex items-center justify-center text-secondary shrink-0">
                                <span className="material-symbols-outlined text-[22px]">flag</span>
                              </div>
                            )}
                            <div className="min-w-0">
                              <h4 className="text-body-lg text-primary truncate">{goal.title}</h4>
                              <p className="text-body-sm text-on-surface-variant mt-0.5">
                                {toFaDigits(goal.currentMillion)} از{' '}
                                {toFaDigits(goal.targetMillion)} میلیون تومان
                              </p>
                            </div>
                          </div>
                          <span className="px-2.5 py-1 rounded-full bg-surface-container-low text-primary text-label-md">
                            {toFaDigits(pct)}٪
                          </span>
                        </div>
                        <div className="w-full bg-surface-container-high h-2.5 rounded-full overflow-hidden flex flex-row-reverse">
                          <div
                            className="bg-on-tertiary-container h-full rounded-full transition-all duration-700"
                            style={{ width: `${pct}%` }}
                          />
                        </div>
                        <div className="flex items-center justify-between text-on-surface-variant text-label-sm pt-0.5">
                          <span className="flex items-center gap-1">
                            <span className="material-symbols-outlined text-[14px]">timelapse</span>
                            <span>{goal.etaLabel}</span>
                          </span>
                          <button
                            onClick={() => handleDepositToGoal(goal.id, 10)}
                            className="text-secondary text-label-md hover:underline"
                            type="button"
                          >
                            + افزایش موجودی هدف
                          </button>
                        </div>
                      </div>
                    );
                  })
                )}
              </section>
            ) : (
              /* Portfolio Asset Holdings View inside Tab 4 */
              <section className="flex flex-col gap-3">
                <div className="flex items-center justify-between px-1">
                  <h3 className="text-headline-sm text-primary">سبد طلا، ارز و صندوق‌ها</h3>
                  <button
                    onClick={() => openQuickAdd('HOLDING')}
                    className="text-label-md text-secondary font-bold"
                    type="button"
                  >
                    + دارایی جدید
                  </button>
                </div>
                {state.holdings.length === 0 ? (
                  <div className="flex flex-col items-center justify-center p-8 bg-surface-container-lowest rounded-lg text-center gap-3">
                    <span className="material-symbols-outlined text-[36px] text-outline">diamond</span>
                    <h3 className="text-body-lg text-on-surface font-bold">سبد دارایی خالی است</h3>
                    <p className="text-body-sm text-outline max-w-xs">
                      می‌توانید دارایی‌های طلا، سکه، تتر یا صندوق‌های سهامی خود را اضافه کنید تا ارزش روز آن‌ها به صورت زنده محاسبه شود.
                    </p>
                    <button
                      onClick={() => openQuickAdd('HOLDING')}
                      className="px-4 py-2 rounded-full bg-primary text-on-primary text-label-md font-semibold"
                      type="button"
                    >
                      + افزودن دارایی به سبد
                    </button>
                  </div>
                ) : (
                  state.holdings.map((h) => {
                  const q = quotes.find((item) => item.id === h.typeId);
                  const valToman = q ? Math.round(h.amount * q.priceToman) : 0;
                  return (
                    <div
                      key={h.id}
                      className="bg-surface-container-lowest rounded-lg p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] flex items-center justify-between"
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-surface-container-low flex items-center justify-center text-primary">
                          <span className="material-symbols-outlined text-[20px]">diamond</span>
                        </div>
                        <div>
                          <h4 className="text-body-lg text-primary">
                            {h.label || q?.fa || h.typeId}
                          </h4>
                          <span className="text-body-sm text-on-surface-variant">
                            {formatNumberWithDecimals(h.amount, 2, state.privacyMode)}{' '}
                            {q?.unitFa || 'واحد'}
                          </span>
                        </div>
                      </div>
                      <div className="text-left">
                        <span className="block text-body-lg text-primary font-extrabold num-tabular">
                          {formatToman(valToman, state.privacyMode)} ت
                        </span>
                        {q && (
                          <span className="text-label-sm text-secondary">
                            {formatPercent(q.change24hPct)}
                          </span>
                        )}
                      </div>
                    </div>
                  );
                })
                )}
              </section>
            )}

            {/* Smart Goal Forecast & Calculator Widget */}
            <section className="bg-surface-container-lowest rounded-lg p-5 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-secondary text-[22px]">
                    calculate
                  </span>
                  <h3 className="text-headline-sm text-primary">شبیه‌ساز هوشمند پس‌انداز</h3>
                </div>
                <span className="text-label-sm text-outline">برآورد گرداول</span>
              </div>

              {/* Target Amount Range Slider */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-label-md">
                  <span className="text-on-surface-variant">مبلغ هدف جدید:</span>
                  <span className="text-primary font-bold num-tabular">
                    {toFaDigits(simTargetMillion)},۰۰۰,۰۰۰ تومان
                  </span>
                </div>
                <input
                  className="w-full h-2 bg-surface-container rounded-lg appearance-none cursor-pointer accent-primary"
                  max={250}
                  min={10}
                  step={5}
                  type="range"
                  value={simTargetMillion}
                  onChange={(e) => setSimTargetMillion(Number(e.target.value))}
                />
              </div>

              {/* Monthly Contribution Range Slider */}
              <div className="space-y-1.5">
                <div className="flex justify-between text-label-md">
                  <span className="text-on-surface-variant">توان پس‌انداز ماهیانه:</span>
                  <span className="text-secondary font-bold num-tabular">
                    {toFaDigits(simMonthlyMillion)},۰۰۰,۰۰۰ تومان
                  </span>
                </div>
                <input
                  className="w-full h-2 bg-surface-container rounded-lg appearance-none cursor-pointer accent-secondary"
                  max={40}
                  min={2}
                  step={1}
                  type="range"
                  value={simMonthlyMillion}
                  onChange={(e) => setSimMonthlyMillion(Number(e.target.value))}
                />
              </div>

              {/* AI Projection Capsule */}
              <div className="bg-surface-container-low rounded-xl p-3.5 flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-secondary-fixed flex items-center justify-center text-on-secondary-fixed-variant shrink-0">
                  <span className="material-symbols-outlined text-[20px]">auto_awesome</span>
                </div>
                <div className="min-w-0">
                  <div className="text-label-md text-primary">تخمین هوش مصنوعی:</div>
                  <p className="text-body-sm text-on-surface-variant mt-0.5">
                    با حفظ این ریتم، حدود {toFaDigits(simMonthsNeeded)} ماه دیگر به این هدف
                    می‌رسید.
                  </p>
                </div>
              </div>
            </section>

            {/* Create New Goal Action Button */}
            <section className="pt-1 pb-4">
              <button
                onClick={() => openQuickAdd('GOAL')}
                className="w-full py-4 rounded-full bg-primary hover:bg-primary-container text-on-primary text-body-lg flex items-center justify-center gap-2 shadow-[0_6px_16px_-4px_rgba(31,35,43,0.18)] active:scale-95 transition-all"
                type="button"
              >
                <span className="material-symbols-outlined text-[22px]">flag</span>
                <span>تعریف هدف جدید پس‌انداز</span>
              </button>
            </section>
          </div>
        )}
      </main>

      {/* =====================================================================
          FLOATING GORDOL CAPSULE BOTTOM NAVIGATION BAR
         ===================================================================== */}
      <div className="fixed bottom-5 inset-x-0 z-40 px-4 pointer-events-none pb-safe">
        <nav className="pointer-events-auto max-w-[360px] mx-auto h-[64px] bg-white/95 backdrop-blur-2xl rounded-full border border-gray-200/80 shadow-[0_12px_32px_-6px_rgba(31,35,43,0.1)] px-3 flex items-center justify-between">
          {/* Tab 1: داشبورد */}
          <button
            onClick={() => setActiveTab('DASHBOARD')}
            title="داشبورد مالی"
            className={`flex flex-col items-center justify-center w-12 h-12 rounded-full transition-all ${
              activeTab === 'DASHBOARD'
                ? 'text-primary font-bold bg-surface-container-low/80'
                : 'text-outline hover:text-primary'
            }`}
            type="button"
          >
            <span
              className={`material-symbols-outlined text-[24px] ${
                activeTab === 'DASHBOARD' ? 'material-symbols-filled' : ''
              }`}
            >
              grid_view
            </span>
          </button>

          {/* Tab 2: تحلیل مخارج */}
          <button
            onClick={() => setActiveTab('ANALYTICS')}
            title="تحلیل مخارج"
            className={`flex flex-col items-center justify-center w-12 h-12 rounded-full transition-all ${
              activeTab === 'ANALYTICS'
                ? 'text-primary font-bold bg-surface-container-low/80'
                : 'text-outline hover:text-primary'
            }`}
            type="button"
          >
            <span
              className={`material-symbols-outlined text-[24px] ${
                activeTab === 'ANALYTICS' ? 'material-symbols-filled' : ''
              }`}
            >
              insights
            </span>
          </button>

          {/* Center Floating Black Action Button (+) */}
          <button
            onClick={() => openQuickAdd('TXN', 'DEBIT')}
            aria-label="تراکنش جدید"
            className="w-12 h-12 rounded-full bg-primary text-on-primary flex items-center justify-center shadow-[0_6px_16px_-4px_rgba(31,35,43,0.28)] hover:scale-105 active:scale-95 transition-transform"
            type="button"
          >
            <span className="material-symbols-outlined text-[26px]">add</span>
          </button>

          {/* Tab 3: اقساط و چک */}
          <button
            onClick={() => setActiveTab('COMMITMENTS')}
            title="اقساط و چک"
            className={`flex flex-col items-center justify-center w-12 h-12 rounded-full transition-all ${
              activeTab === 'COMMITMENTS'
                ? 'text-primary font-bold bg-surface-container-low/80'
                : 'text-outline hover:text-primary'
            }`}
            type="button"
          >
            <span
              className={`material-symbols-outlined text-[24px] ${
                activeTab === 'COMMITMENTS' ? 'material-symbols-filled' : ''
              }`}
            >
              receipt_long
            </span>
          </button>

          {/* Tab 4: پس‌انداز و اهداف */}
          <button
            onClick={() => setActiveTab('GOALS')}
            title="پس‌انداز و اهداف"
            className={`flex flex-col items-center justify-center w-12 h-12 rounded-full transition-all ${
              activeTab === 'GOALS'
                ? 'text-primary font-bold bg-surface-container-low/80'
                : 'text-outline hover:text-primary'
            }`}
            type="button"
          >
            <span
              className={`material-symbols-outlined text-[24px] ${
                activeTab === 'GOALS' ? 'material-symbols-filled' : ''
              }`}
            >
              savings
            </span>
          </button>
        </nav>
      </div>

      {/* =====================================================================
          SAYAD CHEQUE VIEWER MODAL
         ===================================================================== */}
      {selectedChequeModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4">
          <div className="w-full max-w-md bg-white rounded-[28px] p-5 shadow-2xl space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="w-9 h-9 rounded-full bg-secondary-fixed text-secondary flex items-center justify-center">
                  <span className="material-symbols-outlined text-[20px]">verified_user</span>
                </span>
                <div>
                  <h3 className="text-body-lg text-primary">{selectedChequeModal.title}</h3>
                  <span className="text-label-sm text-secondary">{selectedChequeModal.provider}</span>
                </div>
              </div>
              <button
                onClick={() => setSelectedChequeModal(null)}
                className="w-8 h-8 rounded-full bg-surface-container-low flex items-center justify-center"
                type="button"
              >
                <span className="material-symbols-outlined text-[18px]">close</span>
              </button>
            </div>
            <div className="p-4 rounded-2xl bg-surface-container-low space-y-2 text-xs">
              <div className="flex justify-between">
                <span className="text-outline">مبلغ چک:</span>
                <span className="font-black text-primary">
                  {formatToman(selectedChequeModal.totalAmountToman)} تومان
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-outline">در وجه:</span>
                <span className="font-bold text-on-surface">شرکت پیشگامان فناور</span>
              </div>
              <p className="text-on-surface-variant pt-2 border-t border-gray-200 leading-relaxed">
                {selectedChequeModal.note}
              </p>
            </div>
            <button
              onClick={() => {
                setSelectedChequeModal(null);
                showToast('استعلام صیاد با موفقیت تایید شد');
              }}
              className="w-full py-3 rounded-full bg-primary text-on-primary font-bold text-xs"
              type="button"
            >
              تایید و بستن برگه چک
            </button>
          </div>
        </div>
      )}

      {/* =====================================================================
          BANK CARDS & TRANSFER DRAWER (انتقال و کارت‌های شتاب)
         ===================================================================== */}
      {isTransferDrawerOpen && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/40 backdrop-blur-sm p-0 sm:p-4">
          <div className="w-full max-w-md bg-[#f8f9fc] rounded-t-[32px] sm:rounded-[32px] max-h-[88vh] flex flex-col overflow-hidden shadow-2xl">
            <div className="flex items-center justify-between px-5 py-4 bg-white border-b border-gray-100">
              <div className="flex items-center gap-2">
                <span className="w-9 h-9 rounded-full bg-surface-container-low flex items-center justify-center text-primary">
                  <span className="material-symbols-outlined text-[20px]">credit_card</span>
                </span>
                <div>
                  <h3 className="text-body-lg text-primary">کارت‌ها و شماره شبا</h3>
                  <p className="text-label-sm text-outline">کپی سریع کارت و شبا جهت انتقال وجه</p>
                </div>
              </div>
              <button
                onClick={() => setIsTransferDrawerOpen(false)}
                className="w-9 h-9 rounded-full bg-surface-container-low flex items-center justify-center"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="p-4 overflow-y-auto space-y-3">
              {state.accounts.map((acc) => (
                <div
                  key={acc.id}
                  className="bg-white rounded-[24px] p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] space-y-3"
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <h4 className="text-body-lg text-primary">{acc.bankNameFa}</h4>
                      <span className="text-label-sm text-outline">{acc.nickname}</span>
                    </div>
                    <span className="text-headline-sm text-primary num-tabular">
                      {formatToman(acc.balanceToman, state.privacyMode)} ت
                    </span>
                  </div>

                  <div className="p-3 rounded-2xl bg-surface-container-low flex items-center justify-between">
                    <span dir="ltr" className="font-mono text-sm font-bold text-primary">
                      {formatCardPan(acc.pan, false, state.privacyMode)}
                    </span>
                    <button
                      onClick={() => handleCopy(acc.pan, `modal_pan_${acc.id}`, 'شماره کارت')}
                      className="px-3 py-1 rounded-full bg-primary text-white text-label-sm"
                      type="button"
                    >
                      کپی کارت
                    </button>
                  </div>

                  {acc.sheba && (
                    <div className="px-3 py-2 rounded-xl bg-surface-container-low/60 flex items-center justify-between">
                      <span dir="ltr" className="font-mono text-[11px] text-on-surface-variant">
                        {formatSheba(acc.sheba)}
                      </span>
                      <button
                        onClick={() => handleCopy(acc.sheba!, `modal_sheba_${acc.id}`, 'شماره شبا')}
                        className="text-secondary font-bold text-label-sm"
                        type="button"
                      >
                        کپی شبا
                      </button>
                    </div>
                  )}
                </div>
              ))}

              <button
                onClick={() => {
                  setIsTransferDrawerOpen(false);
                  openQuickAdd('CARD');
                }}
                className="w-full py-3.5 rounded-full bg-primary text-white font-bold text-xs"
                type="button"
              >
                + افزودن کارت بانکی جدید
              </button>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          FULL LIVE MARKET DRAWER (نبض بازارها)
         ===================================================================== */}
      {isMarketDrawerOpen && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/40 backdrop-blur-sm p-0 sm:p-4">
          <div className="w-full max-w-md bg-[#f8f9fc] rounded-t-[32px] sm:rounded-[32px] max-h-[88vh] flex flex-col overflow-hidden shadow-2xl">
            <div className="flex items-center justify-between px-5 py-4 bg-white border-b border-gray-100">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse" />
                <h3 className="text-body-lg text-primary">نبض لحظه‌ای بازار طلا، ارز و رمزارز</h3>
              </div>
              <button
                onClick={() => setIsMarketDrawerOpen(false)}
                className="w-9 h-9 rounded-full bg-surface-container-low flex items-center justify-center"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="flex gap-2 overflow-x-auto no-scrollbar px-4 py-3">
              {[
                { id: 'ALL', label: 'همه' },
                { id: 'GOLD', label: 'طلا' },
                { id: 'COIN', label: 'سکه' },
                { id: 'FIAT', label: 'ارز' },
                { id: 'CRYPTO', label: 'رمزارز' },
                { id: 'STOCK', label: 'صندوق بورس' },
              ].map((t) => (
                <button
                  key={t.id}
                  onClick={() => setMarketFilter(t.id as 'ALL' | AssetKind)}
                  className={`px-3.5 py-1.5 rounded-full text-label-md whitespace-nowrap ${
                    marketFilter === t.id
                      ? 'bg-primary text-white'
                      : 'bg-white text-on-surface-variant'
                  }`}
                  type="button"
                >
                  {t.label}
                </button>
              ))}
            </div>

            <div className="p-4 overflow-y-auto grid grid-cols-2 gap-2.5">
              {quotes
                .filter((q) => (marketFilter === 'ALL' ? true : q.kind === marketFilter))
                .map((q) => {
                  const isUp = q.change24hPct >= 0;
                  return (
                    <div
                      key={q.id}
                      className="p-3.5 rounded-2xl bg-white border border-gray-100 shadow-xs flex flex-col justify-between"
                    >
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="text-xs font-bold text-gray-700 truncate">{q.fa}</span>
                        <span
                          className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full dir-ltr ${
                            isUp ? 'text-emerald-600 bg-emerald-50' : 'text-rose-600 bg-rose-50'
                          }`}
                        >
                          {formatPercent(q.change24hPct)}
                        </span>
                      </div>
                      <div className="text-base font-black text-gray-950 num-tabular">
                        {formatToman(q.priceToman)}
                      </div>
                      <span className="text-[10px] text-gray-400 mt-0.5">تومان / {q.unitFa}</span>
                    </div>
                  );
                })}
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          UNIFIED QUICK ADD MODAL (Lithe Vitality Light Style)
         ===================================================================== */}
      {isQuickAddOpen && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/40 backdrop-blur-sm p-0 sm:p-4">
          <div className="w-full max-w-md bg-[#f8f9fc] rounded-t-[32px] sm:rounded-[32px] overflow-hidden shadow-2xl border border-white">
            <div className="flex items-center justify-between px-5 py-4 bg-white border-b border-gray-100">
              <h3 className="text-[15px] font-extrabold text-primary">ثبت هوشمند در ژتون</h3>
              <button
                onClick={() => setIsQuickAddOpen(false)}
                className="w-9 h-9 rounded-full bg-surface-container-low flex items-center justify-center text-on-surface"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            {/* Sub-mode tabs */}
            <div className="flex gap-1.5 px-4 py-3 bg-[#f8f9fc] overflow-x-auto no-scrollbar">
              {[
                { id: 'TXN', label: 'تراکنش' },
                { id: 'SMS', label: 'اسکن پیامک' },
                { id: 'GOAL', label: 'هدف پس‌انداز' },
                { id: 'COMMITMENT', label: 'قسط و چک' },
                { id: 'HOLDING', label: 'دارایی' },
                { id: 'CARD', label: 'کارت بانکی' },
              ].map((m) => (
                <button
                  key={m.id}
                  onClick={() =>
                    setQuickAddTab(
                      m.id as 'TXN' | 'SMS' | 'HOLDING' | 'CARD' | 'COMMITMENT' | 'GOAL'
                    )
                  }
                  className={`px-3.5 py-1.5 rounded-full text-xs font-bold whitespace-nowrap transition ${
                    quickAddTab === m.id
                      ? 'bg-primary text-white shadow-xs'
                      : 'bg-white text-on-surface-variant'
                  }`}
                  type="button"
                >
                  {m.label}
                </button>
              ))}
            </div>

            <div className="p-4 pt-1">
              {quickAddTab === 'TXN' && (
                <form
                  onSubmit={handleAddTransaction}
                  className="bg-white rounded-[24px] p-4 shadow-xs space-y-3 text-xs"
                >
                  <div className="grid grid-cols-2 gap-2 p-1 bg-surface-container-low rounded-full">
                    <button
                      type="button"
                      onClick={() => setTxDirection('DEBIT')}
                      className={`py-2 rounded-full font-bold transition ${
                        txDirection === 'DEBIT'
                          ? 'bg-primary text-white shadow-xs'
                          : 'text-on-surface-variant'
                      }`}
                    >
                      برداشت / هزینه (−)
                    </button>
                    <button
                      type="button"
                      onClick={() => setTxDirection('CREDIT')}
                      className={`py-2 rounded-full font-bold transition ${
                        txDirection === 'CREDIT'
                          ? 'bg-emerald-600 text-white shadow-xs'
                          : 'text-on-surface-variant'
                      }`}
                    >
                      واریز / درآمد (+)
                    </button>
                  </div>

                  <div>
                    <label className="block text-on-surface-variant font-bold mb-1">
                      مبلغ (تومان)
                    </label>
                    <input
                      type="number"
                      required
                      value={txAmount}
                      onChange={(e) => setTxAmount(e.target.value)}
                      placeholder="مثلاً 350000"
                      className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-primary font-extrabold text-sm focus:outline-none"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-2.5">
                    <div>
                      <label className="block text-on-surface-variant font-bold mb-1">
                        دسته‌بندی
                      </label>
                      <select
                        value={txCategory}
                        onChange={(e) => setTxCategory(e.target.value)}
                        className="w-full bg-surface-container-low rounded-2xl px-3 py-2.5 text-primary font-bold"
                      >
                        {CATEGORIES.map((c) => (
                          <option key={c.nameFa} value={c.nameFa}>
                            {c.emoji} {c.nameFa}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-on-surface-variant font-bold mb-1">
                        حساب بانکی
                      </label>
                      <select
                        value={txAccountId}
                        onChange={(e) => setTxAccountId(e.target.value)}
                        className="w-full bg-surface-container-low rounded-2xl px-3 py-2.5 text-primary font-bold"
                      >
                        {state.accounts.map((a) => (
                          <option key={a.id} value={a.id}>
                            {a.bankNameFa}
                          </option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div>
                    <label className="block text-on-surface-variant font-bold mb-1">
                      عنوان / فروشگاه
                    </label>
                    <input
                      type="text"
                      value={txMerchant}
                      onChange={(e) => setTxMerchant(e.target.value)}
                      placeholder="مثلاً هایپراستار، کافه لمیز، اسنپ..."
                      className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-primary font-medium"
                    />
                  </div>

                  <button
                    type="submit"
                    className="w-full py-3.5 rounded-full bg-primary text-white font-extrabold text-sm shadow-md active:scale-95 transition"
                  >
                    ذخیره تراکنش
                  </button>
                </form>
              )}

              {quickAddTab === 'SMS' && (
                <div className="bg-white rounded-[24px] p-4 shadow-xs space-y-3 text-xs">
                  <p className="text-on-surface-variant">
                    متن پیامک بانک را وارد کنید یا یکی از نمونه‌های آماده را انتخاب نمایید:
                  </p>
                  <div className="flex gap-1.5 overflow-x-auto no-scrollbar pb-1">
                    {SAMPLE_BANK_SMS.map((s, idx) => (
                      <button
                        key={idx}
                        type="button"
                        onClick={() => setSmsRaw(s.text)}
                        className="px-3 py-1.5 rounded-full bg-surface-container-low hover:bg-surface-container text-secondary whitespace-nowrap font-bold"
                      >
                        {s.label}
                      </button>
                    ))}
                  </div>
                  <textarea
                    rows={4}
                    value={smsRaw}
                    onChange={(e) => setSmsRaw(e.target.value)}
                    className="w-full bg-surface-container-low rounded-2xl p-3 text-primary font-mono text-xs leading-relaxed focus:outline-none"
                  />

                  {(() => {
                    const preview = parseBankSms(smsRaw);
                    if (!preview) {
                      return (
                        <div className="p-3 rounded-2xl bg-error-container text-on-error-container font-bold">
                          پیامک معتبر بانکی تشخیص داده نشد.
                        </div>
                      );
                    }
                    return (
                      <div className="p-3.5 rounded-2xl bg-emerald-50 border border-emerald-100 space-y-1">
                        <div className="flex justify-between font-bold text-emerald-700">
                          <span>بانک: {preview.bankNameFa}</span>
                          <span>{preview.direction === 'DEBIT' ? 'برداشت' : 'واریز'}</span>
                        </div>
                        <div className="flex justify-between text-primary font-extrabold">
                          <span>مبلغ: {formatToman(preview.amountToman)} تومان</span>
                          <span>دسته: {preview.categoryFa}</span>
                        </div>
                      </div>
                    );
                  })()}

                  <button
                    type="button"
                    onClick={handleParseAndSaveSms}
                    className="w-full py-3.5 rounded-full bg-primary text-white font-extrabold text-sm shadow-md active:scale-95 transition"
                  >
                    استخراج و ثبت خودکار
                  </button>
                </div>
              )}

              {quickAddTab === 'GOAL' && (
                <form
                  onSubmit={handleAddGoal}
                  className="bg-white rounded-[24px] p-4 shadow-xs space-y-3 text-xs"
                >
                  <div>
                    <label className="block text-on-surface-variant font-bold mb-1">
                      عنوان هدف پس‌انداز
                    </label>
                    <input
                      type="text"
                      required
                      value={newGoalTitle}
                      onChange={(e) => setNewGoalTitle(e.target.value)}
                      placeholder="مثلاً خرید آیفون ۱۶ یا سفر شمال"
                      className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-primary font-bold"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-2.5">
                    <div>
                      <label className="block text-on-surface-variant font-bold mb-1">
                        مبلغ هدف (میلیون تومان)
                      </label>
                      <input
                        type="number"
                        required
                        value={newGoalTargetMillion}
                        onChange={(e) => setNewGoalTargetMillion(e.target.value)}
                        className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-primary font-extrabold"
                      />
                    </div>
                    <div>
                      <label className="block text-on-surface-variant font-bold mb-1">
                        پس‌انداز فعلی (میلیون)
                      </label>
                      <input
                        type="number"
                        value={newGoalCurrentMillion}
                        onChange={(e) => setNewGoalCurrentMillion(e.target.value)}
                        className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-primary font-extrabold"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-on-surface-variant font-bold mb-1">نوع هدف</label>
                    <select
                      value={newGoalCategory}
                      onChange={(e) =>
                        setNewGoalCategory(e.target.value as 'short' | 'long' | 'emergency')
                      }
                      className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-primary font-bold"
                    >
                      <option value="short">کوتاه‌مدت</option>
                      <option value="long">بزرگ و آینده‌ساز</option>
                      <option value="emergency">صندوق اضطراری</option>
                    </select>
                  </div>
                  <button
                    type="submit"
                    className="w-full py-3.5 rounded-full bg-primary text-white font-extrabold text-sm"
                  >
                    ایجاد هدف جدید
                  </button>
                </form>
              )}

              {quickAddTab === 'COMMITMENT' && (
                <form
                  onSubmit={handleAddCommitment}
                  className="bg-white rounded-[24px] p-4 shadow-xs space-y-3 text-xs"
                >
                  <div>
                    <label className="block text-on-surface-variant font-bold mb-1">
                      عنوان قسط یا چک
                    </label>
                    <input
                      type="text"
                      required
                      value={cmTitle}
                      onChange={(e) => setCmTitle(e.target.value)}
                      placeholder="مثلاً وام مسکن یا چک صیادی اجاره"
                      className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-primary font-bold"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-2.5">
                    <div>
                      <label className="block text-on-surface-variant font-bold mb-1">
                        بانک / سرویس
                      </label>
                      <input
                        type="text"
                        value={cmProvider}
                        onChange={(e) => setCmProvider(e.target.value)}
                        className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-primary font-bold"
                      />
                    </div>
                    <div>
                      <label className="block text-on-surface-variant font-bold mb-1">
                        نوع تعهد
                      </label>
                      <select
                        value={cmKind}
                        onChange={(e) => setCmKind(e.target.value as CommitmentKind)}
                        className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-primary font-bold"
                      >
                        <option value="LOAN">وام بانکی</option>
                        <option value="BNPL">خرید اقساطی (BNPL)</option>
                        <option value="CHECK">چک صیادی</option>
                      </select>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-2.5">
                    <div>
                      <label className="block text-on-surface-variant font-bold mb-1">
                        مبلغ کل (تومان)
                      </label>
                      <input
                        type="number"
                        required
                        value={cmTotal}
                        onChange={(e) => setCmTotal(e.target.value)}
                        placeholder="15000000"
                        className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-primary font-extrabold"
                      />
                    </div>
                    <div>
                      <label className="block text-on-surface-variant font-bold mb-1">
                        تعداد کل اقساط
                      </label>
                      <input
                        type="number"
                        value={cmInstallments}
                        onChange={(e) => setCmInstallments(e.target.value)}
                        className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-primary font-extrabold"
                      />
                    </div>
                  </div>
                  <button
                    type="submit"
                    className="w-full py-3.5 rounded-full bg-primary text-white font-extrabold text-sm"
                  >
                    ثبت تعهد مالی
                  </button>
                </form>
              )}

              {quickAddTab === 'HOLDING' && (
                <form
                  onSubmit={handleAddHolding}
                  className="bg-white rounded-[24px] p-4 shadow-xs space-y-3 text-xs"
                >
                  <div>
                    <label className="block text-on-surface-variant font-bold mb-1">
                      انتخاب دارایی
                    </label>
                    <select
                      value={holdTypeId}
                      onChange={(e) => setHoldTypeId(e.target.value)}
                      className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-primary font-bold"
                    >
                      {quotes.map((q) => (
                        <option key={q.id} value={q.id}>
                          {q.fa} ({formatToman(q.priceToman)} ت)
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-on-surface-variant font-bold mb-1">
                      مقدار / تعداد
                    </label>
                    <input
                      type="number"
                      step="any"
                      required
                      value={holdAmount}
                      onChange={(e) => setHoldAmount(e.target.value)}
                      placeholder="مثلاً 10 گرم یا 500 دلار"
                      className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-primary font-extrabold"
                    />
                  </div>
                  <div>
                    <label className="block text-on-surface-variant font-bold mb-1">
                      عنوان دلخواه
                    </label>
                    <input
                      type="text"
                      value={holdLabel}
                      onChange={(e) => setHoldLabel(e.target.value)}
                      placeholder="مثلاً پس‌انداز طلای ۱۸ عیار"
                      className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-primary"
                    />
                  </div>
                  <button
                    type="submit"
                    className="w-full py-3.5 rounded-full bg-primary text-white font-extrabold text-sm"
                  >
                    افزودن به سبد دارایی
                  </button>
                </form>
              )}

              {quickAddTab === 'CARD' && (
                <form
                  onSubmit={handleAddCard}
                  className="bg-white rounded-[24px] p-4 shadow-xs space-y-3 text-xs"
                >
                  <div>
                    <label className="block text-on-surface-variant font-bold mb-1">
                      شماره ۱۶ رقمی کارت شتاب
                    </label>
                    <input
                      type="text"
                      required
                      dir="ltr"
                      maxLength={16}
                      value={cardPan}
                      onChange={(e) => setCardPan(e.target.value)}
                      placeholder="62198619..."
                      className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-primary font-mono font-bold"
                    />
                    {cardPan.length >= 6 && (
                      <span className="text-[11px] text-secondary mt-1 block font-bold">
                        {detectBankByPan(cardPan)?.fa ?? 'بانک عضو شتاب'}{' '}
                        {cardPan.length === 16 && isValidLuhn(cardPan) ? '• Luhn معتبر ✓' : ''}
                      </span>
                    )}
                  </div>
                  <div>
                    <label className="block text-on-surface-variant font-bold mb-1">
                      شماره شبا (IR...)
                    </label>
                    <input
                      type="text"
                      dir="ltr"
                      value={cardSheba}
                      onChange={(e) => setCardSheba(e.target.value)}
                      placeholder="IR420560..."
                      className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-primary font-mono"
                    />
                    {cardSheba.length > 4 && isValidSheba(cardSheba) && (
                      <span className="text-[10px] text-emerald-600 mt-0.5 block font-bold">
                        ✓ شبا استاندارد ISO-7064
                      </span>
                    )}
                  </div>
                  <div className="grid grid-cols-2 gap-2.5">
                    <div>
                      <label className="block text-on-surface-variant font-bold mb-1">
                        موجودی (تومان)
                      </label>
                      <input
                        type="number"
                        value={cardBalance}
                        onChange={(e) => setCardBalance(e.target.value)}
                        placeholder="18500000"
                        className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-primary font-extrabold"
                      />
                    </div>
                    <div>
                      <label className="block text-on-surface-variant font-bold mb-1">
                        نام مستعار کارت
                      </label>
                      <input
                        type="text"
                        value={cardNickname}
                        onChange={(e) => setCardNickname(e.target.value)}
                        placeholder="حساب اصلی"
                        className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-primary"
                      />
                    </div>
                  </div>
                  <button
                    type="submit"
                    className="w-full py-3.5 rounded-full bg-primary text-white font-extrabold text-sm"
                  >
                    افزودن کارت بانکی
                  </button>
                </form>
              )}
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          ONBOARDING STARTER MODAL
         ===================================================================== */}
      <OnboardingModal
        isOpen={!state.hasSeenOnboarding}
        onComplete={() => {
          setState((prev) => {
            const next = { ...prev, hasSeenOnboarding: true };
            savePersistedState(next);
            return next;
          });
        }}
        onLoadDemo={() => {
          const demo = getDemoState();
          setState(demo);
          savePersistedState(demo);
          showToast('اطلاعات نمونه نمایشی بارگذاری شد');
        }}
      />

      {/* =====================================================================
          7 ANDIS FINANCIAL CALCULATORS MODAL
         ===================================================================== */}
      <CalculatorsModal
        isOpen={isCalcOpen}
        onClose={() => setIsCalcOpen(false)}
        quotes={quotes}
      />
    </div>
  );
}
