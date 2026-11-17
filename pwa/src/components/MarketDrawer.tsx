import React from 'react';
import { Icon } from './Icon';
import { VerifiedMarketState } from '../services/marketService';
import { AssetKind, MarketQuote } from '../types';
import { formatPercent, formatToman } from '../utils/format';

interface MarketDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  marketState: VerifiedMarketState;
  quotes: MarketQuote[];
  marketFilter: 'ALL' | AssetKind;
  onFilterChange: (next: 'ALL' | AssetKind) => void;
  onRefreshRates: () => void;
  isRefreshingRates: boolean;
}

export const MarketDrawer: React.FC<MarketDrawerProps> = ({
  isOpen,
  onClose,
  marketState,
  quotes,
  marketFilter,
  onFilterChange,
  onRefreshRates,
  isRefreshingRates,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/40 backdrop-blur-sm p-0 sm:p-4">
      <div className="w-full max-w-md bg-[#f8f9fc] rounded-t-[32px] sm:rounded-[32px] max-h-[88vh] flex flex-col overflow-hidden shadow-2xl">
        <div className="flex items-center justify-between px-5 py-4 bg-white border-b border-gray-100">
          <div className="flex items-center gap-2">
            <span
              className={`w-2.5 h-2.5 rounded-full ${
                marketState.freshness === 'fresh' && marketState.verified
                  ? 'bg-emerald-500 animate-pulse'
                  : marketState.freshness === 'stale'
                  ? 'bg-amber-500'
                  : 'bg-rose-500'
              }`}
            />
            <h3 className="text-body-lg text-primary">نبض لحظه‌ای بازار طلا، ارز و رمزارز</h3>
          </div>
          <button
            onClick={onClose}
            className="w-9 h-9 rounded-full bg-surface-container-low flex items-center justify-center"
            type="button"
          >
            <Icon name="close" size={20} />
          </button>
        </div>

        {(marketState.freshness !== 'fresh' ||
          marketState.statusReason === 'tampered' ||
          marketState.statusReason === 'replay') && (
          <div
            data-testid="market-status-banner"
            className="mx-4 mt-3 px-3.5 py-2 rounded-2xl bg-amber-50 border border-amber-200 text-amber-900 text-xs font-bold flex items-center justify-between gap-2"
          >
            <div className="flex items-center gap-2 min-w-0">
              <Icon name="warning" size={16} />
              <span>
                {marketState.statusReason === 'tampered'
                  ? 'امضای دیجیتال نرخ‌ها نامعتبر است؛ آخرین نرخ معتبر نمایش داده می‌شود.'
                  : marketState.statusReason === 'replay'
                  ? 'پاسخ قدیمی‌تر از کش فعلی رد شد (محافظت در برابر تکرار).'
                  : marketState.freshness === 'expired'
                  ? 'نرخ‌های ذخیره شده بیش از ۷ روز قدمت دارند و منقضی شده‌اند.'
                  : 'نرخ‌ها قدیمی‌تر از ۴۸ ساعت هستند (حالت آفلاین).'}
              </span>
            </div>
            {(marketState.freshness === 'stale' || marketState.freshness === 'expired') && (
              <button
                type="button"
                data-testid="market-drawer-retry-button"
                onClick={onRefreshRates}
                disabled={isRefreshingRates}
                className="shrink-0 inline-flex items-center gap-1 rounded-full border border-amber-300 bg-amber-100/80 px-2.5 py-1 text-[11px] font-bold text-amber-900 hover:bg-amber-200/70 transition-colors disabled:opacity-50"
              >
                <Icon
                  name="sync"
                  size={13}
                  className={isRefreshingRates ? 'animate-spin' : ''}
                />
                <span>تلاش مجدد</span>
              </button>
            )}
          </div>
        )}

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
              onClick={() => onFilterChange(t.id as 'ALL' | AssetKind)}
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
  );
};
