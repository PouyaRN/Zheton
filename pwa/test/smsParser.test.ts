import { describe, expect, it } from 'vitest';
import { parseBankSms } from '../src/utils/smsParser';

describe('Extended Bank SMS Parser (Paya, Satna, Pol, Refunds, Fees)', () => {
  it('detects PAYA, SATNA, and POL rails without false positives on مشابه words like اپل', () => {
    const paya = parseBankSms('بانک سامان\nانتقال پایا\nمبلغ: ۲۵,۰۰۰,۰۰۰ ریال\nمانده: ۱۰۰,۰۰۰,۰۰۰ ریال');
    expect(paya?.transferRail).toBe('PAYA');
    expect(paya?.categoryFa).toBe('انتقال وجه');
    expect(paya?.amountToman).toBe(2_500_000);

    const satna = parseBankSms('بانک ملت\nانتقال ساتنا\nمبلغ: ۶۰۰,۰۰۰,۰۰۰ ریال\nمانده: ۸۰۰,۰۰۰,۰۰۰ ریال');
    expect(satna?.transferRail).toBe('SATNA');
    expect(satna?.amountToman).toBe(60_000_000);

    const pol = parseBankSms('بلو بانک\nانتقال از طریق پل موفق\nمبلغ: ۴,۵۰۰,۰۰۰ ریال\nمانده: ۱۹,۰۰۰,۰۰۰ ریال');
    expect(pol?.transferRail).toBe('POL');
    expect(pol?.merchant).toBe('انتقال پل');

    const apple = parseBankSms('بلو بانک\nخرید از فروشگاه اپل پلاس\nمبلغ: ۱,۲۰۰,۰۰۰ ریال\nمانده: ۱۰,۰۰۰,۰۰۰ ریال');
    expect(apple?.transferRail).toBeUndefined();
    expect(apple?.amountToman).toBe(120_000);
  });

  it('separates explicit کارمزد fee from the main transaction amount', () => {
    const sms = parseBankSms(
      'بانک تجارت\nانتقال پایا\nکارمزد: ۵۰,۰۰۰ ریال\nمبلغ: ۱۰,۰۰۰,۰۰۰ ریال\nمانده: ۵۰,۰۰۰,۰۰۰ ریال',
    );
    expect(sms?.feeToman).toBe(5_000);
    expect(sms?.amountToman).toBe(1_000_000);
    expect(sms?.balanceToman).toBe(5_000_000);
  });

  it('detects purchase refunds as CREDIT and labels merchant as refund', () => {
    const refund = parseBankSms(
      'بانک ملت\nاسترداد وجه خرید اینترنتی\nمبلغ: ۸,۵۰۰,۰۰۰ ریال\nمانده: ۲۸,۵۰۰,۰۰۰ ریال',
    );
    expect(refund?.direction).toBe('CREDIT');
    expect(refund?.isRefund).toBe(true);
    expect(refund?.amountToman).toBe(850_000);
  });
});
