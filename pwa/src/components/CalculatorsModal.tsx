import React, { useState } from 'react';
import {
  COIN_SPECS,
  CPI_BY_YEAR,
  calcCoinBubble,
  calcDailyHabit,
  calcInflation,
  calcLoanPmt,
  calcMehrieh,
  calcTakhirTadieh,
} from '../tools/calculators';
import { formatToman, toFaDigits } from '../utils/format';
import { MarketQuote } from '../types';

interface CalculatorsModalProps {
  isOpen: boolean;
  onClose: () => void;
  quotes: MarketQuote[];
}

type ToolId =
  | 'hobab'
  | 'vam'
  | 'tavarom'
  | 'mehrieh'
  | 'takhir'
  | 'hoghugh'
  | 'habit';

const TOOLS: { id: ToolId; title: string; icon: string }[] = [
  { id: 'hobab', title: 'حباب‌سنج سکه', icon: 'toll' },
  { id: 'vam', title: 'محاسبهٔ قسط وام', icon: 'percent' },
  { id: 'tavarom', title: 'تورم و قدرت خرید', icon: 'trending_up' },
  { id: 'mehrieh', title: 'ارزش روز مهریه', icon: 'balance' },
  { id: 'takhir', title: 'خسارت تأخیر تادیه', icon: 'calculate' },
  { id: 'hoghugh', title: 'قدرت خرید حقوق', icon: 'work' },
  { id: 'habit', title: 'هزینهٔ عادت روزانه', icon: 'local_cafe' },
];

const YEARS = Object.keys(CPI_BY_YEAR).map(Number);

export const CalculatorsModal: React.FC<CalculatorsModalProps> = ({ isOpen, onClose, quotes }) => {
  const [activeTool, setActiveTool] = useState<ToolId>('hobab');

  const liveGold18 = quotes.find((q) => q.id === 'gold18')?.priceToman ?? 3450000;
  const liveEmami = quotes.find((q) => q.id === 'coin_emami')?.priceToman ?? 41200000;

  // 1. Hobab state
  const [selectedCoinId, setSelectedCoinId] = useState('coin_emami');
  const [customCoinPrice, setCustomCoinPrice] = useState<number>(liveEmami);
  const [customGoldPrice, setCustomGoldPrice] = useState<number>(liveGold18);

  // 2. Loan PMT state
  const [loanPrincipal, setLoanPrincipal] = useState(200000000);
  const [loanRate, setLoanRate] = useState(23);
  const [loanMonths, setLoanMonths] = useState(36);

  // 3. Inflation state
  const [infAmount, setInfAmount] = useState(10000000);
  const [infFromYear, setInfFromYear] = useState(1398);
  const [infToYear, setInfToYear] = useState(1404);

  // 4. Mehrieh state
  const [mehriehCount, setMehriehCount] = useState(114);
  const [mehriehCoinPrice, setMehriehCoinPrice] = useState(liveEmami);

  // 5. Takhir Tadieh state
  const [debtAmount, setDebtAmount] = useState(50000000);
  const [dueYear, setDueYear] = useState(1399);
  const [payYear, setPayYear] = useState(1404);

  // 6. Salary state
  const [salary1400, setSalary1400] = useState(12000000);
  const [salaryCurrent, setSalaryCurrent] = useState(38000000);

  // 7. Habit state
  const [habitDaily, setHabitDaily] = useState(120000);
  const [habitYears, setHabitYears] = useState(5);
  const [habitInflation, setHabitInflation] = useState(40);

  if (!isOpen) return null;

  const coinSpec = COIN_SPECS.find((c) => c.id === selectedCoinId) ?? COIN_SPECS[0];
  const bubbleRes = calcCoinBubble(customCoinPrice, customGoldPrice, coinSpec.weightGram, coinSpec.karat);
  const loanRes = calcLoanPmt(loanPrincipal, loanRate, loanMonths);
  const infRes = calcInflation(infAmount, infFromYear, infToYear);
  const mehriehRes = calcMehrieh(mehriehCount, mehriehCoinPrice, 8.133);
  const takhirRes = calcTakhirTadieh(debtAmount, dueYear, payYear);
  const salaryReq = calcInflation(salary1400, 1400, 1404);
  const salaryGap = salaryCurrent - salaryReq.equivalentToman;
  const habitRes = calcDailyHabit(habitDaily, habitYears, habitInflation);

  const handleSelectCoin = (coinId: string) => {
    setSelectedCoinId(coinId);
    const q = quotes.find((item) => item.id === coinId);
    if (q) setCustomCoinPrice(q.priceToman);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/40 backdrop-blur-sm p-0 sm:p-4">
      <div className="w-full max-w-md bg-[#f8f9fc] rounded-t-[32px] sm:rounded-[32px] max-h-[90vh] flex flex-col overflow-hidden shadow-[0_20px_50px_rgba(9,13,21,0.2)] border border-white">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 bg-white border-b border-gray-100">
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-full bg-secondary-fixed flex items-center justify-center text-secondary">
              <span className="material-symbols-outlined text-[20px]">auto_awesome</span>
            </div>
            <div>
              <h2 className="text-[15px] font-extrabold text-on-surface">دستیار و ماشین‌حساب هوشمند</h2>
              <p className="text-[11px] text-on-surface-variant">محاسبات دقیق اقتصادی با نرخ‌های زنده بازار</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="w-9 h-9 rounded-full bg-surface-container-low hover:bg-surface-container flex items-center justify-center text-on-surface transition active:scale-95"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        </div>

        {/* Tool selector pills */}
        <div className="flex gap-2 overflow-x-auto no-scrollbar px-4 py-3 bg-[#f8f9fc] shrink-0">
          {TOOLS.map((t) => {
            const active = t.id === activeTool;
            return (
              <button
                key={t.id}
                onClick={() => setActiveTool(t.id)}
                className={`flex items-center gap-1.5 px-3.5 py-2 rounded-full text-[12px] font-bold whitespace-nowrap transition-all active:scale-95 ${
                  active
                    ? 'bg-primary text-white shadow-[0_4px_12px_rgba(9,13,21,0.18)]'
                    : 'bg-white text-on-surface-variant shadow-xs hover:text-on-surface'
                }`}
              >
                <span className="material-symbols-outlined text-[16px]">{t.icon}</span>
                <span>{t.title}</span>
              </button>
            );
          })}
        </div>

        {/* Content body */}
        <div className="p-4 overflow-y-auto space-y-4 text-sm">
          {activeTool === 'hobab' && (
            <div className="bg-white rounded-[24px] p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] space-y-4">
              <div className="flex gap-1.5 overflow-x-auto no-scrollbar pb-1">
                {COIN_SPECS.map((c) => (
                  <button
                    key={c.id}
                    onClick={() => handleSelectCoin(c.id)}
                    className={`py-1.5 px-3 rounded-full text-[11px] font-bold whitespace-nowrap transition ${
                      selectedCoinId === c.id
                        ? 'bg-secondary-container text-white'
                        : 'bg-surface-container-low text-on-surface-variant'
                    }`}
                  >
                    {c.fa}
                  </button>
                ))}
              </div>

              <div className="grid grid-cols-1 gap-3">
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">قیمت بازار سکه (تومان)</label>
                  <input
                    type="number"
                    value={customCoinPrice}
                    onChange={(e) => setCustomCoinPrice(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold focus:outline-none focus:ring-2 focus:ring-secondary-container/30"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">نرخ هر گرم طلای ۱۸ عیار (تومان)</label>
                  <input
                    type="number"
                    value={customGoldPrice}
                    onChange={(e) => setCustomGoldPrice(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold focus:outline-none focus:ring-2 focus:ring-secondary-container/30"
                  />
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-surface-container-low space-y-2.5">
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">ارزش ذاتی طلای سکه:</span>
                  <span className="text-[14px] font-extrabold text-on-surface num-tabular">
                    {formatToman(bubbleRes.intrinsicToman)} تومان
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">مقدار حباب اسمی:</span>
                  <span className="text-[16px] font-black text-secondary num-tabular">
                    {formatToman(bubbleRes.bubbleToman)} تومان
                  </span>
                </div>
                <div className="flex justify-between items-center pt-2 border-t border-gray-200/70">
                  <span className="text-[12px] text-on-surface-variant">درصد حباب و وضعیت:</span>
                  <span className="px-2.5 py-1 rounded-full bg-secondary-fixed text-secondary font-bold text-[11px]">
                    {toFaDigits(bubbleRes.bubblePct)}٪ — {bubbleRes.statusFa}
                  </span>
                </div>
              </div>
            </div>
          )}

          {activeTool === 'vam' && (
            <div className="bg-white rounded-[24px] p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] space-y-4">
              <div className="grid grid-cols-1 gap-3">
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">مبلغ اصل وام (تومان)</label>
                  <input
                    type="number"
                    value={loanPrincipal}
                    onChange={(e) => setLoanPrincipal(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                  />
                </div>
                <div className="grid grid-cols-2 gap-2.5">
                  <div>
                    <label className="block text-[11px] font-bold text-on-surface-variant mb-1">نرخ سود سالانه (٪)</label>
                    <input
                      type="number"
                      value={loanRate}
                      onChange={(e) => setLoanRate(Number(e.target.value))}
                      className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                    />
                  </div>
                  <div>
                    <label className="block text-[11px] font-bold text-on-surface-variant mb-1">مدت بازپرداخت (ماه)</label>
                    <input
                      type="number"
                      value={loanMonths}
                      onChange={(e) => setLoanMonths(Number(e.target.value))}
                      className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                    />
                  </div>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-surface-container-low space-y-2.5">
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">مبلغ هر قسط ماهانه:</span>
                  <span className="text-[18px] font-black text-primary num-tabular">
                    {formatToman(loanRes.monthlyPayment)} تومان
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">مجموع کل بازپرداخت:</span>
                  <span className="text-[13px] font-bold text-on-surface num-tabular">
                    {formatToman(loanRes.totalPayment)} تومان
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">کل سود بانکی دوره:</span>
                  <span className="text-[13px] font-bold text-on-tertiary-container num-tabular">
                    {formatToman(loanRes.totalInterest)} تومان
                  </span>
                </div>
              </div>
            </div>
          )}

          {activeTool === 'tavarom' && (
            <div className="bg-white rounded-[24px] p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] space-y-4">
              <div>
                <label className="block text-[11px] font-bold text-on-surface-variant mb-1">مبلغ در سال مبدا (تومان)</label>
                <input
                  type="number"
                  value={infAmount}
                  onChange={(e) => setInfAmount(Number(e.target.value))}
                  className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                />
              </div>
              <div className="grid grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">سال مبدا</label>
                  <select
                    value={infFromYear}
                    onChange={(e) => setInfFromYear(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-on-surface font-bold"
                  >
                    {YEARS.map((y) => (
                      <option key={y} value={y}>{toFaDigits(y)}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">سال مقصد</label>
                  <select
                    value={infToYear}
                    onChange={(e) => setInfToYear(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-on-surface font-bold"
                  >
                    {YEARS.map((y) => (
                      <option key={y} value={y}>{toFaDigits(y)}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-surface-container-low space-y-2.5">
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">معادل قدرت خرید در {toFaDigits(infToYear)}:</span>
                  <span className="text-[17px] font-black text-primary num-tabular">
                    {formatToman(infRes.equivalentToman)} تومان
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">تورم انباشته دوره:</span>
                  <span className="text-[13px] font-bold text-on-tertiary-container num-tabular">
                    {toFaDigits(infRes.cumulativeInflationPct)}٪ ({toFaDigits(infRes.ratio)} برابر)
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">قدرت خرید باقی‌مانده:</span>
                  <span className="text-[13px] font-bold text-secondary num-tabular">
                    {toFaDigits(infRes.purchasingPowerRetainedPct)}٪
                  </span>
                </div>
              </div>
            </div>
          )}

          {activeTool === 'mehrieh' && (
            <div className="bg-white rounded-[24px] p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] space-y-4">
              <div className="grid grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">تعداد سکه مهریه</label>
                  <input
                    type="number"
                    value={mehriehCount}
                    onChange={(e) => setMehriehCount(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">نرخ روز سکه (تومان)</label>
                  <input
                    type="number"
                    value={mehriehCoinPrice}
                    onChange={(e) => setMehriehCoinPrice(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                  />
                </div>
              </div>
              <div className="p-4 rounded-2xl bg-surface-container-low space-y-2">
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">ارزش کل مهریه به نرخ روز:</span>
                  <span className="text-[17px] font-black text-primary num-tabular">
                    {formatToman(mehriehRes.totalValueToman)} تومان
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">معادل وزن کل طلا:</span>
                  <span className="text-[13px] font-bold text-secondary num-tabular">
                    {toFaDigits(mehriehRes.totalGoldGrams)} گرم طلا
                  </span>
                </div>
              </div>
            </div>
          )}

          {activeTool === 'takhir' && (
            <div className="bg-white rounded-[24px] p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] space-y-4">
              <div>
                <label className="block text-[11px] font-bold text-on-surface-variant mb-1">اصل مبلغ طلب یا چک (تومان)</label>
                <input
                  type="number"
                  value={debtAmount}
                  onChange={(e) => setDebtAmount(Number(e.target.value))}
                  className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                />
              </div>
              <div className="grid grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">سال سررسید</label>
                  <select
                    value={dueYear}
                    onChange={(e) => setDueYear(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-on-surface font-bold"
                  >
                    {YEARS.map((y) => (
                      <option key={y} value={y}>{toFaDigits(y)}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">سال وصول</label>
                  <select
                    value={payYear}
                    onChange={(e) => setPayYear(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-3.5 py-2.5 text-on-surface font-bold"
                  >
                    {YEARS.map((y) => (
                      <option key={y} value={y}>{toFaDigits(y)}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="p-4 rounded-2xl bg-surface-container-low space-y-2">
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">کل قابل مطالبه (اصل + خسارت):</span>
                  <span className="text-[17px] font-black text-primary num-tabular">
                    {formatToman(takhirRes.totalClaimToman)} تومان
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">خسارت تأخیر تادیه (ماده ۵۲۲):</span>
                  <span className="text-[13px] font-bold text-emerald-600 num-tabular">
                    {formatToman(takhirRes.penaltyDamageToman)} تومان
                  </span>
                </div>
              </div>
            </div>
          )}

          {activeTool === 'hoghugh' && (
            <div className="bg-white rounded-[24px] p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] space-y-4">
              <div className="grid grid-cols-1 gap-3">
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">حقوق ماهانه در سال ۱۴۰۰ (تومان)</label>
                  <input
                    type="number"
                    value={salary1400}
                    onChange={(e) => setSalary1400(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">حقوق فعلی شما (تومان)</label>
                  <input
                    type="number"
                    value={salaryCurrent}
                    onChange={(e) => setSalaryCurrent(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                  />
                </div>
              </div>
              <div className="p-4 rounded-2xl bg-surface-container-low space-y-2">
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">حقوق لازم برای حفظ قدرت خرید ۱۴۰۰:</span>
                  <span className="text-[16px] font-black text-primary num-tabular">
                    {formatToman(salaryReq.equivalentToman)} تومان
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">وضعیت تراز قدرت خرید:</span>
                  <span className={`text-[13px] font-bold num-tabular ${salaryGap >= 0 ? 'text-emerald-600' : 'text-on-tertiary-container'}`}>
                    {salaryGap >= 0
                      ? `+${formatToman(salaryGap)} تومان جلوتر از تورم`
                      : `${formatToman(Math.abs(salaryGap))} تومان عقب‌تر از تورم`}
                  </span>
                </div>
              </div>
            </div>
          )}

          {activeTool === 'habit' && (
            <div className="bg-white rounded-[24px] p-4 shadow-[0_4px_20px_-2px_rgba(31,35,43,0.04)] space-y-4">
              <div>
                <label className="block text-[11px] font-bold text-on-surface-variant mb-1">هزینه روزانه عادت (تومان)</label>
                <input
                  type="number"
                  value={habitDaily}
                  onChange={(e) => setHabitDaily(Number(e.target.value))}
                  className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                />
              </div>
              <div className="grid grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">افق زمانی (سال)</label>
                  <input
                    type="number"
                    value={habitYears}
                    min={1}
                    max={15}
                    onChange={(e) => setHabitYears(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                  />
                </div>
                <div>
                  <label className="block text-[11px] font-bold text-on-surface-variant mb-1">نرخ تورم سالانه (٪)</label>
                  <input
                    type="number"
                    value={habitInflation}
                    onChange={(e) => setHabitInflation(Number(e.target.value))}
                    className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-on-surface font-extrabold"
                  />
                </div>
              </div>
              <div className="p-4 rounded-2xl bg-surface-container-low space-y-2">
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">هزینه سال اول:</span>
                  <span className="text-[14px] font-bold text-on-surface num-tabular">
                    {formatToman(habitRes.firstYearCost)} تومان
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-[12px] text-on-surface-variant">مجموع انباشته در {toFaDigits(habitYears)} سال:</span>
                  <span className="text-[18px] font-black text-primary num-tabular">
                    {formatToman(habitRes.cumulativeTotal)} تومان
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
