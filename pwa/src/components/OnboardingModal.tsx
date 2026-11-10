import React, { useState } from 'react';
import { Icon } from './Icon';

interface OnboardingModalProps {
  isOpen: boolean;
  onComplete: () => void;
  onLoadDemo?: () => void;
}

export const OnboardingModal: React.FC<OnboardingModalProps> = ({
  isOpen,
  onComplete,
  onLoadDemo,
}) => {
  const [currentStep, setCurrentStep] = useState(0);

  if (!isOpen) return null;

  const slides = [
    {
      icon: 'shield_lock',
      badge: 'امنیت و حریم خصوصی',
      badgeColor: 'bg-emerald-50 text-emerald-700 border-emerald-200',
      title: 'محرمانگی اول-محلی (Local-First)',
      description:
        'ژتون بدون نیاز به ثبت‌نام، شماره موبایل یا سرور خارجی کار می‌کند. تمام پیامک‌ها، حساب‌ها و تراکنش‌های شما فقط روی همین دستگاه ذخیره و پردازش می‌شوند.',
      highlight: 'بدون تلهمتری • بدون ذخیره ابری • ۱۰۰٪ محرمانه',
    },
    {
      icon: 'toll',
      badge: 'دیده‌بان قدرت خرید',
      badgeColor: 'bg-amber-50 text-amber-700 border-amber-200',
      title: 'سپر ضد تورم و معادل طلا',
      description:
        'موجودی نقدی و کارت‌های شما به صورت بلادرنگ به معادل گرم طلای ۱۸ عیار و قدرت خرید روز تبدیل می‌شود تا نوسانات تورم ریال را دقیق لمس کنید.',
      highlight: 'رصد زنده مظنه طلا، سکه، دلار و تتر',
    },
    {
      icon: 'sms',
      badge: 'مدیریت هوشمند',
      badgeColor: 'bg-blue-50 text-blue-700 border-blue-200',
      title: 'خوانش پیامک و کنترل اقساط',
      description:
        'با چسباندن یا دریافت پیامک‌های بانکی، مبالغ و بانک‌ها خودکار تشخیص داده می‌شوند. همچنین سررسید وام‌ها و چک‌های صیاد به موقع به شما یادآوری خواهند شد.',
      highlight: 'پشتیبانی از تمام بانک‌های عضو شتاب ایران',
    },
  ];

  const current = slides[currentStep];

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in"
      dir="rtl"
    >
      <div className="relative w-full max-w-md bg-white rounded-3xl shadow-2xl overflow-hidden border border-gray-100 flex flex-col p-6 text-right">
        {/* Top Header & Step Dots */}
        <div className="flex items-center justify-between mb-5">
          <div className="flex items-center gap-2">
            <div className="w-9 h-9 rounded-2xl bg-gray-900 text-white flex items-center justify-center font-black text-[15px]">
              ژ
            </div>
            <div className="flex flex-col">
              <span className="text-[15px] font-black text-gray-950">ژتون • Zheton</span>
              <span className="text-[11px] text-gray-400 font-medium">دستیار مالی و قدرت خرید</span>
            </div>
          </div>
          <div className="flex items-center gap-1.5 dir-ltr">
            {slides.map((_, idx) => (
              <span
                key={idx}
                className={`h-1.5 rounded-full transition-all duration-300 ${
                  idx === currentStep ? 'w-6 bg-gray-950' : 'w-2 bg-gray-200'
                }`}
              />
            ))}
          </div>
        </div>

        {/* Slide Visual Icon */}
        <div className="flex flex-col items-center text-center my-3">
          <div className="w-20 h-20 rounded-3xl bg-gray-50 border border-gray-100 flex items-center justify-center text-gray-900 shadow-inner mb-4">
            <Icon name={current.icon} size={40} className="text-gray-900" />
          </div>
          <span
            className={`text-[11px] font-bold px-3 py-1 rounded-full border mb-2.5 ${current.badgeColor}`}
          >
            {current.badge}
          </span>
          <h2 className="text-[20px] font-black text-gray-950 mb-2">{current.title}</h2>
          <p className="text-[13px] text-gray-600 leading-relaxed max-w-sm mb-3">
            {current.description}
          </p>
          <div className="w-full py-2 px-3 rounded-2xl bg-gray-50 border border-gray-100 text-[11px] font-semibold text-gray-500">
            {current.highlight}
          </div>
        </div>

        {/* Buttons / Actions */}
        <div className="flex flex-col gap-2.5 mt-5">
          {currentStep < slides.length - 1 ? (
            <button
              onClick={() => setCurrentStep((prev) => prev + 1)}
              className="w-full py-3.5 px-4 rounded-2xl bg-gray-950 hover:bg-black text-white text-[13px] font-extrabold flex items-center justify-center gap-2 active:scale-[0.98] transition-all shadow-md"
              type="button"
            >
              <span>مرحله بعد</span>
              <Icon name="arrow_back" size={18} />
            </button>
          ) : (
            <button
              onClick={onComplete}
              className="w-full py-3.5 px-4 rounded-2xl bg-gray-950 hover:bg-black text-white text-[13px] font-extrabold flex items-center justify-center gap-2 active:scale-[0.98] transition-all shadow-md"
              type="button"
            >
              <Icon name="check_circle" size={18} />
              <span>شروع استفاده از ژتون</span>
            </button>
          )}

          <div className="flex items-center justify-between pt-1">
            {currentStep > 0 ? (
              <button
                onClick={() => setCurrentStep((prev) => prev - 1)}
                className="text-[12px] font-bold text-gray-400 hover:text-gray-700 py-1 px-2"
                type="button"
              >
                بازگشت
              </button>
            ) : (
              <div />
            )}

            {onLoadDemo && (
              <button
                onClick={onLoadDemo}
                className="text-[11.5px] font-semibold text-gray-400 hover:text-secondary py-1 px-2"
                type="button"
              >
                کاوش با داده‌های دمو
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

