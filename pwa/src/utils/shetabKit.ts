import { toAsciiDigits } from './format';

export interface BankInfo {
  key: string;
  fa: string;
  bins: string[];
  gradient: string;
  accent: string;
}

export const IRANIAN_BANKS: BankInfo[] = [
  { key: 'BLU', fa: 'بلو بانک', bins: ['62198619', '62198618'], gradient: 'from-[#0D47A1] via-[#1565C0] to-[#002171]', accent: '#64B5F6' },
  { key: 'SAMAN', fa: 'بانک سامان', bins: ['621986'], gradient: 'from-[#0A2540] via-[#10375C] to-[#071A2E]', accent: '#38BDF8' },
  { key: 'MELLAT', fa: 'بانک ملت', bins: ['610433', '991975'], gradient: 'from-[#4A0E17] via-[#7F1D1D] to-[#31080E]', accent: '#FCA5A5' },
  { key: 'MELLI', fa: 'بانک ملی ایران', bins: ['603799'], gradient: 'from-[#0F2027] via-[#203A43] to-[#2C5364]', accent: '#4FC3F7' },
  { key: 'PASARGAD', fa: 'بانک پاسارگاد', bins: ['502229', '639347'], gradient: 'from-[#1C1917] via-[#292524] to-[#0C0A09]', accent: '#FBBF24' },
  { key: 'TEJARAT', fa: 'بانک تجارت', bins: ['627353', '585983'], gradient: 'from-[#1E1B4B] via-[#312E81] to-[#1E3A8A]', accent: '#93C5FD' },
  { key: 'SADERAT', fa: 'بانک صادرات', bins: ['603769'], gradient: 'from-[#0C2D48] via-[#145DA0] to-[#07192B]', accent: '#7DD3FC' },
  { key: 'KESHAVARZI', fa: 'بانک کشاورزی', bins: ['603770', '639217'], gradient: 'from-[#064E3B] via-[#065F46] to-[#022C22]', accent: '#6EE7B7' },
  { key: 'MASKAN', fa: 'بانک مسکن', bins: ['628023'], gradient: 'from-[#7C2D12] via-[#9A3412] to-[#431407]', accent: '#FDBA74' },
  { key: 'RESALAT', fa: 'بانک رسالت', bins: ['504172'], gradient: 'from-[#0D3B4C] via-[#155E75] to-[#082F49]', accent: '#67E8F9' },
  { key: 'REFAH', fa: 'بانک رفاه', bins: ['589463'], gradient: 'from-[#1E293B] via-[#334155] to-[#0F172A]', accent: '#38BDF8' },
  { key: 'PARSIAN', fa: 'بانک پارسیان', bins: ['622106', '627884'], gradient: 'from-[#3B0764] via-[#581C87] to-[#2E1065]', accent: '#D8B4FE' },
  { key: 'EGHTESAD_NOVIN', fa: 'بانک اقتصاد نوین', bins: ['627412'], gradient: 'from-[#311042] via-[#4A154B] to-[#1F0929]', accent: '#F0ABFC' },
  { key: 'SEPAH', fa: 'بانک سپه', bins: ['589210'], gradient: 'from-[#3F2305] via-[#6C3428] to-[#261503]', accent: '#FCD34D' },
];

export const CARD_THEME_PRESETS: Record<string, { labelFa: string; gradient: string; accent: string }> = {
  obsidian: { labelFa: 'ابسیدین تیره', gradient: 'from-[#0D0D11] via-[#1E1F24] to-[#141519]', accent: '#F7C948' },
  gold: { labelFa: 'طلای سلطنتی', gradient: 'from-[#5C4012] via-[#8A6220] to-[#3B2809]', accent: '#FDE047' },
  emerald: { labelFa: 'زمردی اندیس', gradient: 'from-[#0C544A] via-[#064E3B] to-[#03201C]', accent: '#3BE0A8' },
  royal: { labelFa: 'لاجوردی نئون', gradient: 'from-[#1E1B4B] via-[#312E81] to-[#0F172A]', accent: '#818CF8' },
  crimson: { labelFa: 'یاقوت سرخ', gradient: 'from-[#4C0519] via-[#881337] to-[#2B030E]', accent: '#FDA4AF' },
  titanium: { labelFa: 'تیتانیوم متالیک', gradient: 'from-[#1F2937] via-[#374151] to-[#111827]', accent: '#93C5FD' },
};

export function detectBankByPan(pan: string): BankInfo | null {
  const clean = toAsciiDigits(pan).replace(/\D/g, '');
  if (clean.length < 6) return null;
  for (const bank of IRANIAN_BANKS) {
    for (const bin of bank.bins) {
      if (clean.startsWith(bin)) return bank;
    }
  }
  return null;
}

export function isValidLuhn(pan: string): boolean {
  const clean = toAsciiDigits(pan).replace(/\D/g, '');
  if (clean.length !== 16) return false;
  let sum = 0;
  for (let i = 0; i < 16; i++) {
    let d = Number(clean[i]);
    if (i % 2 === 0) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
  }
  return sum % 10 === 0;
}

export function isValidSheba(sheba: string): boolean {
  const clean = toAsciiDigits(sheba).replace(/[\s-]/g, '').toUpperCase();
  if (!/^IR\d{24}$/.test(clean)) return false;
  // ISO 7064 Mod 97-10
  const rearranged = clean.slice(4) + '1827' + clean.slice(2, 4);
  let remainder = 0;
  for (let i = 0; i < rearranged.length; i++) {
    remainder = (remainder * 10 + Number(rearranged[i])) % 97;
  }
  return remainder === 1;
}
