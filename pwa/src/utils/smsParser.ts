import { toAsciiDigits } from './format';
import { NecessityTag, TxnDirection } from '../types';

export interface ParsedBankSms {
  bankNameFa: string;
  direction: TxnDirection;
  amountToman: number;
  balanceToman: number | null;
  merchant: string;
  categoryFa: string;
  necessity: NecessityTag;
}

export const CATEGORIES: { nameFa: string; icon: string; defaultNecessity: NecessityTag }[] = [
  { nameFa: 'خواربار', icon: 'shopping_cart', defaultNecessity: 'NEED' },
  { nameFa: 'رستوران و کافه', icon: 'local_cafe', defaultNecessity: 'WANT' },
  { nameFa: 'حمل و نقل', icon: 'directions_car', defaultNecessity: 'NEED' },
  { nameFa: 'قبض‌ها', icon: 'receipt_long', defaultNecessity: 'NEED' },
  { nameFa: 'سلامت', icon: 'medical_services', defaultNecessity: 'NEED' },
  { nameFa: 'خرید', icon: 'shopping_bag', defaultNecessity: 'WANT' },
  { nameFa: 'پس‌انداز و سرمایه', icon: 'toll', defaultNecessity: 'SAVINGS' },
  { nameFa: 'انتقال وجه', icon: 'send_money', defaultNecessity: 'NEED' },
  { nameFa: 'خانه و کاشانه', icon: 'home', defaultNecessity: 'NEED' },
  { nameFa: 'اشتراک و اینترنت', icon: 'language', defaultNecessity: 'WANT' },
  { nameFa: 'بنزین و سوخت', icon: 'local_gas_station', defaultNecessity: 'NEED' },
  { nameFa: 'مد و پوشاک', icon: 'checkroom', defaultNecessity: 'WANT' },
  { nameFa: 'سرگرمی و فرهنگ', icon: 'movie', defaultNecessity: 'WANT' },
  { nameFa: 'درآمد و حقوق', icon: 'payments', defaultNecessity: 'SAVINGS' },
];

export function getCategoryIcon(categoryFa: string): string {
  const found = CATEGORIES.find((c) => c.nameFa === categoryFa);
  return found ? found.icon : 'push_pin';
}

export function parseBankSms(rawText: string): ParsedBankSms | null {
  const text = toAsciiDigits(rawText).trim();
  if (!text) return null;

  // Reject OTP messages
  if (/رمز|پویا|یکبار مصرف|code|otp/i.test(text) && !/مانده|موجودی/i.test(text)) {
    return null;
  }

  // Detect bank name
  let bankNameFa = 'بانک';
  if (/بلو|blu/i.test(text)) bankNameFa = 'بلو بانک';
  else if (/ملت|mellat/i.test(text)) bankNameFa = 'بانک ملت';
  else if (/ملی|melli/i.test(text)) bankNameFa = 'بانک ملی ایران';
  else if (/سامان|saman/i.test(text)) bankNameFa = 'بانک سامان';
  else if (/پاسارگاد|pasargad/i.test(text)) bankNameFa = 'بانک پاسارگاد';
  else if (/تجارت|tejarat/i.test(text)) bankNameFa = 'بانک تجارت';
  else if (/صادرات|saderat/i.test(text)) bankNameFa = 'بانک صادرات';
  else if (/رفاه|refah/i.test(text)) bankNameFa = 'بانک رفاه';
  else if (/رسالت|resalat/i.test(text)) bankNameFa = 'بانک رسالت';
  else if (/مسکن|maskan/i.test(text)) bankNameFa = 'بانک مسکن';

  // Direction
  let direction: TxnDirection = 'DEBIT';
  if (/واریز|افزایش|بستانکار|\+/i.test(text) && !/برداشت|کاهش|خرید/i.test(text)) {
    direction = 'CREDIT';
  }

  // Extract numbers with commas
  const numberMatches = [...text.matchAll(/(\d{1,3}(?:,\d{3})+|\d{5,15})\s*(ریال|تومان)?/g)];
  if (numberMatches.length === 0) return null;

  let amountRial = 0;
  let balanceRial: number | null = null;

  // Check explicit balance line
  const balanceMatch = text.match(/(?:مانده|موجودی)\s*[:\s]*(\d{1,3}(?:,\d{3})+|\d{4,15})/);
  if (balanceMatch) {
    balanceRial = Number(balanceMatch[1].replace(/,/g, ''));
  }

  // Find first amount that isn't the balance
  for (const m of numberMatches) {
    const num = Number(m[1].replace(/,/g, ''));
    const unit = m[2];
    const asRial = unit === 'تومان' ? num * 10 : num;
    if (balanceRial !== null && num === balanceRial) continue;
    if (asRial >= 10000) {
      amountRial = asRial;
      break;
    }
  }

  if (amountRial === 0 && numberMatches.length > 0) {
    amountRial = Number(numberMatches[0][1].replace(/,/g, ''));
  }

  const amountToman = Math.round(amountRial / 10);
  const balanceToman = balanceRial !== null ? Math.round(balanceRial / 10) : null;

  // Guess merchant & category
  let merchant = direction === 'CREDIT' ? 'واریز بانکی' : 'خرید کارتی';
  let categoryFa = direction === 'CREDIT' ? 'درآمد و حقوق' : 'خرید';
  let necessity: NecessityTag = direction === 'CREDIT' ? 'SAVINGS' : 'WANT';

  if (/اسنپ|snapp|تپسی|tapsi/i.test(text)) {
    merchant = 'اسنپ / تپسی';
    categoryFa = 'حمل و نقل';
    necessity = 'NEED';
  } else if (/دیجی‌کالا|دیجی کالا|digikala/i.test(text)) {
    merchant = 'دیجی‌کالا';
    categoryFa = 'خرید';
    necessity = 'WANT';
  } else if (/کافه|رستوران|پیتزا|برگر|غذا|اسنپ‌فود/i.test(text)) {
    merchant = 'کافه و رستوران';
    categoryFa = 'رستوران و کافه';
    necessity = 'WANT';
  } else if (/هایپر|سوپرمارکت|کوروش|جانبو|رفاه|نانوایی|پروتئین/i.test(text)) {
    merchant = 'سوپرمارکت و خواربار';
    categoryFa = 'خواربار';
    necessity = 'NEED';
  } else if (/داروخانه|پزشک|درمانگاه|بیمارستان/i.test(text)) {
    merchant = 'داروخانه و سلامت';
    categoryFa = 'سلامت';
    necessity = 'NEED';
  } else if (/قبض|همراه اول|ایرانسل|مخابرات|برق|گاز/i.test(text)) {
    merchant = 'پرداخت قبض / اپراتور';
    categoryFa = 'قبض‌ها';
    necessity = 'NEED';
  } else if (/حقوق|پاداش|سود/i.test(text)) {
    merchant = 'واریز حقوق / سود';
    categoryFa = 'درآمد و حقوق';
    necessity = 'SAVINGS';
  }

  return {
    bankNameFa,
    direction,
    amountToman,
    balanceToman,
    merchant,
    categoryFa,
    necessity,
  };
}

export interface BankSmsSample {
  label: string;
  text: string;
}

export const SAMPLE_BANK_SMS: BankSmsSample[] = [
  {
    label: 'بلو بانک (خرید اسنپ)',
    text: 'بلو بانک\nخرید از درگاه اسنپ\nمبلغ: ۱,۴۵۰,۰۰۰ ریال\nمانده: ۱۸۴,۵۵۰,۰۰۰ ریال\n۱۴۰۳/۰۸/۰۷',
  },
  {
    label: 'بانک ملت (هایپرمارکت)',
    text: 'بانک ملت\nبرداشت: ۸,۹۲۰,۰۰۰ ریال\nفروشگاه افق کوروش\nمانده: ۶۲,۱۰۰,۰۰۰ ریال',
  },
  {
    label: 'بانک سامان (واریز حقوق)',
    text: 'بانک سامان\nواریز به حساب\nمبلغ: ۳۸۰,۰۰۰,۰۰۰ ریال\nشرح: واریز حقوق ماهانه\nمانده: ۴۴۲,۱۰۰,۰۰۰ ریال',
  },
  {
    label: 'بانک ملی (کافه)',
    text: 'بانک ملی ایران\nخرید اینترنتی کافه لمیز\nمبلغ: ۲,۸۵۰,۰۰۰ ریال\nمانده: ۳۱,۴۰۰,۰۰۰ ریال',
  },
];
