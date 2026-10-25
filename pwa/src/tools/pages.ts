/**
 * The seven calculator screens and the `/tools/` index.
 *
 * Each renderer owns its form, its result card and its one caveat; the arithmetic is in
 * `calc.ts` and the metadata in `meta.ts`. Rates, when a page needs one, are applied twice:
 * once from the last-good cache so the page is usable immediately, then from `/rates` when the
 * network answers. A manual price is always editable, which is what keeps these tools working
 * offline and behind a flaky connection.
 */

import {
  coinBubble,
  delayDamagesToman,
  habitEstimate,
  inflationEstimate,
  loanEstimate,
  mehrValueToman,
  salaryPurchasingPower,
  GOLD_PURITY_PERMILLE_18K,
} from './calc';
import { cpiIndex, cpiYears, isEstimatedYear } from './cpi';
import { COIN_PRESETS, coinPreset } from './coins';
import { TOOLS, TOOLS_INDEX_META, toolById } from './meta';
import type { ToolMeta } from './meta';
import { cachedRates, fetchRates, GOLD_18_RATE_KEY, rateToman, ratesUpdatedLabel } from './rates';
import type { RateSnapshot } from './rates';
import { fa, faYear, parseNumber } from './number';
import {
  bindCalculator,
  el,
  emptyResultHtml,
  errorHtml,
  escape,
  formatPercent,
  formatToman,
  numberValue,
  resultHtml,
  selectValue,
  setInputValue,
  yearOptions,
} from './ui';

const YEAR_LABEL = (year: number): string =>
  `${faYear(year)}${isEstimatedYear(year) ? ' (تقریبی)' : ''}`;

function pageShell(tool: ToolMeta, body: string): HTMLElement {
  return el(`
    <div>
      <header class="hero tool-hero">
        <a class="crumb" href="/tools/">← همهٔ ماشین‌حساب‌ها</a>
        <h1>${escape(tool.heading)}</h1>
        <p class="hero-label">${escape(tool.lead)}</p>
      </header>
      <div class="page stack">${body}</div>
    </div>
  `);
}

function resultCard(root: ParentNode): HTMLElement {
  return root.querySelector<HTMLElement>('#result')!;
}

function roundedToman(value: number): string {
  return formatToman(Math.round(value));
}

function coinOptions(selected: string): string {
  return COIN_PRESETS.map(
    (coin) =>
      `<option value="${coin.id}"${coin.id === selected ? ' selected' : ''}>${escape(coin.label)}</option>`,
  ).join('');
}

// ───────────────────────────── مهریه ─────────────────────────────

/**
 * The Mehrich tool. Live rates fill the coin price; the field stays editable so the tool works
 * with no network and with a rate the user got somewhere else.
 */
export function renderMehrieh(root: HTMLElement): void {
  const tool = toolById('mehrieh')!;
  root.replaceChildren(
    pageShell(
      tool,
      `
      <form class="panel" id="calc">
        <label for="coin">نوع سکه</label>
        <select id="coin">${coinOptions('emami')}</select>
        <div class="field-grid">
          <div>
            <label for="count">تعداد سکه</label>
            <input id="count" inputmode="numeric" value="۱۱۰" autocomplete="off" />
          </div>
          <div>
            <label for="price">قیمت هر سکه (تومان)</label>
            <input id="price" inputmode="numeric" autocomplete="off" placeholder="مثلاً ۱۸۰,۰۰۰,۰۰۰" />
          </div>
        </div>
        <p class="fineprint" id="rates-note">نرخ زنده در حال دریافت است…</p>
        <button class="primary" type="submit">حساب کن</button>
      </form>
      <section class="panel result" id="result" aria-live="polite">${emptyResultHtml()}</section>
      <p class="fineprint">
        فرمول: تعداد سکه × قیمت هر سکه. این عدد هزینهٔ جایگزینی امروز است؛ عیار، فرسودگی،
        کارمزد و ارزش کلکسیونی در آن نیامده و ارزیابی رسمی یا شرعی نیست.
      </p>
      `,
    ),
  );

  const result = resultCard(root);
  const select = root.querySelector<HTMLSelectElement>('#coin')!;
  const priceField = root.querySelector<HTMLInputElement>('#price')!;
  const note = root.querySelector<HTMLElement>('#rates-note')!;
  let rates: RateSnapshot | null = null;
  let livePrice: number | null = null;

  const run = (): void => {
    const count = numberValue(root, 'count');
    const price = numberValue(root, 'price');
    if (count == null || price == null) {
      result.innerHTML = emptyResultHtml();
      return;
    }
    const value = mehrValueToman(count, price);
    if (value == null) {
      result.innerHTML = errorHtml('تعداد سکه و قیمت باید عددی و صفر یا بزرگ‌تر باشند.');
      return;
    }
    const coin = coinPreset(select.value) ?? COIN_PRESETS[0]!;
    result.innerHTML = resultHtml(
      'ارزش تقریبی مهریه',
      fa(Math.round(value)),
      'تومان',
      [
        { label: 'نوع سکه', value: coin.label },
        { label: 'تعداد', value: `${fa(count, Number.isInteger(count) ? 0 : 2)} سکه` },
        { label: 'قیمت هر سکه', value: formatToman(price) },
      ],
      'تخمین هزینهٔ جایگزینی؛ نه ارزیابی رسمی یا کارشناسی.',
    );
  };

  const applyRates = (snapshot: RateSnapshot, stale: boolean, force = false): void => {
    rates = snapshot;
    const coin = coinPreset(select.value) ?? COIN_PRESETS[0]!;
    const price = rateToman(snapshot, coin.rateKey);
    if (price == null) {
      note.textContent = `نرخ ${coin.label} در دادهٔ زنده نبود؛ نرخ را دستی وارد کن.`;
      run();
      return;
    }
    const typed = parseNumber(priceField.value);
    if (force || priceField.value.trim() === '' || typed == null || typed === livePrice) {
      priceField.value = String(Math.round(price));
    }
    livePrice = price;
    note.textContent = `نرخ ${coin.label}: ${formatToman(price)}${
      stale ? ' (ذخیره‌شده)' : ''
    } — ${ratesUpdatedLabel(snapshot)}`;
    run();
  };

  bindCalculator(root, 'calc', run);
  select.addEventListener('change', () => {
    if (rates) {
      applyRates(rates, false, true);
    } else {
      priceField.value = '';
      livePrice = null;
      note.textContent = 'نرخ زنده در دسترس نیست؛ قیمت سکهٔ انتخاب‌شده را دستی وارد کن.';
      run();
    }
  });

  const cached = cachedRates();
  if (cached) applyRates(cached, true);
  void fetchRates().then((fresh) => {
    if (fresh) applyRates(fresh, false);
    else if (!cached) note.textContent = 'نرخ زنده در دسترس نیست؛ قیمت هر سکه را دستی وارد کن.';
  });
}

// ───────────────────── خسارت تأخیر تادیه ─────────────────────

export function renderTakhir(root: HTMLElement): void {
  const tool = toolById('takhir-tadieh')!;
  root.replaceChildren(
    pageShell(
      tool,
      `
      <form class="panel" id="calc">
        <label for="principal">مبلغ بدهی (تومان)</label>
        <input id="principal" inputmode="numeric" value="۱۰,۰۰۰,۰۰۰" autocomplete="off" />
        <div class="field-grid">
          <div>
            <label for="due">سال سررسید</label>
            <select id="due">${yearOptions(cpiYears(), 1400, YEAR_LABEL)}</select>
          </div>
          <div>
            <label for="paid">سال پرداخت</label>
            <select id="paid">${yearOptions(cpiYears(), 1405, YEAR_LABEL)}</select>
          </div>
        </div>
        <button class="primary" type="submit">تخمین بزن</button>
      </form>
      <section class="panel result" id="result" aria-live="polite">${emptyResultHtml()}</section>
      <p class="fineprint">
        فرض حقوقی: مادهٔ ۵۲۲ قانون آیین دادرسی مدنی به دادگاه اجازه می‌دهد کاهش ارزش پول را
        بر پایهٔ شاخص تورم رسمی، از زمان سررسید تا پرداخت، جبران کند. این ابزار همان ایده را
        با شاخص سالانهٔ CPI تخمین می‌زند و خودش هیچ تصمیم حقوقی نمی‌گیرد. جریمهٔ قراردادی،
        سود توافقی، هزینهٔ دادرسی و قواعد خاص بانکی در آن نیست؛ عدد دادگاه می‌تواند متفاوت باشد.
      </p>
      `,
    ),
  );

  const result = resultCard(root);
  const run = (): void => {
    const principal = numberValue(root, 'principal');
    const due = Number(selectValue(root, 'due'));
    const paid = Number(selectValue(root, 'paid'));
    if (principal == null) {
      result.innerHTML = emptyResultHtml();
      return;
    }
    const estimate = delayDamagesToman(principal, due, paid);
    if (estimate == null) {
      result.innerHTML = errorHtml('مبلغ یا سال‌ها معتبر نیستند.');
      return;
    }
    result.innerHTML = resultHtml(
      'خسارت تأخیر تادیه (تخمین)',
      fa(Math.round(estimate.damagesToman)),
      'تومان',
      [
        { label: 'مبلغ اصل', value: roundedToman(estimate.principalToman) },
        { label: 'مجموع با خسارت', value: roundedToman(estimate.totalToman) },
        { label: 'تورم انباشته', value: formatPercent(estimate.cumulativeInflationPercent) },
        {
          label: 'شاخص CPI سررسید',
          value: fa(estimate.cpiFrom, 1),
          hint: 'پایهٔ ۱۴۰۰ = ۱۰۰',
        },
        { label: 'شاخص CPI پرداخت', value: fa(estimate.cpiTo, 1) },
      ],
      'اگر شاخص کاهش پیدا کند، خسارت صفر است؛ خسارت منفی معنا ندارد.',
    );
  };

  bindCalculator(root, 'calc', run);
}

// ───────────────────── تورم / قدرت خرید ─────────────────────

export function renderTavarom(root: HTMLElement): void {
  const tool = toolById('tavarom')!;
  root.replaceChildren(
    pageShell(
      tool,
      `
      <form class="panel" id="calc">
        <label for="amount">مبلغ (تومان)</label>
        <input id="amount" inputmode="numeric" value="۱۰,۰۰۰,۰۰۰" autocomplete="off" />
        <div class="field-grid">
          <div>
            <label for="from">از سال</label>
            <select id="from">${yearOptions(cpiYears(), 1400, YEAR_LABEL)}</select>
          </div>
          <div>
            <label for="to">به سال</label>
            <select id="to">${yearOptions(cpiYears(), 1405, YEAR_LABEL)}</select>
          </div>
        </div>
        <button class="primary" type="submit">حساب کن</button>
      </form>
      <section class="panel result" id="result" aria-live="polite">${emptyResultHtml()}</section>
      <p class="fineprint">
        داده: شاخص بهای کالاها و خدمات مصرفی، میانگین سالانه، پایهٔ ۱۴۰۰ = ۱۰۰ (بانک مرکزی /
        مرکز آمار). ۱۴۰۴ و ۱۴۰۵ تقریبی‌اند. فرمول قدرت خرید: مبلغ × شاخص مبدأ ÷ شاخص مقصد.
      </p>
      `,
    ),
  );

  const result = resultCard(root);
  const run = (): void => {
    const amount = numberValue(root, 'amount');
    const from = Number(selectValue(root, 'from'));
    const to = Number(selectValue(root, 'to'));
    if (amount == null) {
      result.innerHTML = emptyResultHtml();
      return;
    }
    const estimate = inflationEstimate(amount, from, to);
    if (estimate == null) {
      result.innerHTML = errorHtml('مبلغ یا سال‌ها معتبر نیستند.');
      return;
    }
    result.innerHTML = resultHtml(
      `قدرت خرید در ${faYear(to)}`,
      fa(Math.round(estimate.purchasingPowerToman)),
      'تومان',
      [
        {
          label: 'هم‌ارز اسمی در سال مقصد',
          value: roundedToman(estimate.equivalentToman),
          hint: 'مبلغی که همان سبد را در سال مقصد می‌خرد',
        },
        { label: 'تورم انباشته', value: formatPercent(estimate.cumulativeInflationPercent) },
        {
          label: 'تورم سالانهٔ میانگین',
          value:
            estimate.annualizedInflationPercent == null
              ? '—'
              : formatPercent(estimate.annualizedInflationPercent),
        },
        {
          label: 'شاخص CPI مبدأ',
          value: fa(estimate.cpiFrom, 1),
          hint: 'پایهٔ ۱۴۰۰ = ۱۰۰',
        },
        { label: 'شاخص CPI مقصد', value: fa(estimate.cpiTo, 1) },
      ],
      'این عدد میانگین سبد مصرفی است؛ اگر مخارج تو با سبد رسمی فرق دارد، عدد هم فرق می‌کند.',
    );
  };

  bindCalculator(root, 'calc', run);
}

// ───────────────────── قدرت خرید حقوق ─────────────────────

const SALARY_FROM_YEAR = 1400;
const SALARY_TO_YEAR = 1405;

export function renderHoghugh(root: HTMLElement): void {
  const tool = toolById('hoghugh')!;
  root.replaceChildren(
    pageShell(
      tool,
      `
      <form class="panel" id="calc">
        <label for="salary">حقوق در سال ۱۴۰۰ (تومان)</label>
        <input id="salary" inputmode="numeric" value="۱۰,۰۰۰,۰۰۰" autocomplete="off" />
        <p class="fineprint">مقایسهٔ ثابت: ۱۴۰۰ → ۱۴۰۵، با همان شاخص CPI.</p>
        <button class="primary" type="submit">حساب کن</button>
      </form>
      <section class="panel result" id="result" aria-live="polite">${emptyResultHtml()}</section>
      <p class="fineprint">
        فرض: حقوق در تمام دوره ثابت مانده و افزایش یا ترفیع نگرفته است. ۱۴۰۵ تقریبی است.
        اگر در این دوره حقوقت تغییر کرده، حقوق فعلی را با همین ابزار برای بازهٔ سال تغییر
        مقایسه کن.
      </p>
      `,
    ),
  );

  const result = resultCard(root);
  const run = (): void => {
    const salary = numberValue(root, 'salary');
    if (salary == null) {
      result.innerHTML = emptyResultHtml();
      return;
    }
    const estimate = salaryPurchasingPower(salary, SALARY_FROM_YEAR, SALARY_TO_YEAR);
    if (estimate == null) {
      result.innerHTML = errorHtml('حقوق را درست وارد کن.');
      return;
    }
    result.innerHTML = resultHtml(
      'قدرت خرید حقوق ۱۴۰۰ در قیمت‌های ۱۴۰۵',
      fa(Math.round(estimate.purchasingPowerToman)),
      'تومان',
      [
        {
          label: 'حقوق لازم در ۱۴۰۵ برای همان قدرت خرید',
          value: roundedToman(estimate.requiredSalaryToman),
        },
        { label: 'افت قدرت خرید', value: formatPercent(estimate.lossPercent) },
        {
          label: 'تورم انباشتهٔ ۱۴۰۰ تا ۱۴۰۵',
          value: formatPercent(estimate.cumulativeInflationPercent),
        },
        {
          label: 'شاخص CPI ۱۴۰۰',
          value: fa(cpiIndex(SALARY_FROM_YEAR) ?? 0, 1),
          hint: 'پایهٔ ۱۴۰۰ = ۱۰۰',
        },
        { label: 'شاخص CPI ۱۴۰۵', value: fa(cpiIndex(SALARY_TO_YEAR) ?? 0, 1) },
      ],
      'حقوق ثابت فرض شده؛ هر افزایش حقوقی که گرفته باشی، این عدد را بهتر می‌کند.',
    );
  };

  bindCalculator(root, 'calc', run);
}

// ───────────────────── حباب سکه ─────────────────────

export function renderHobab(root: HTMLElement): void {
  const tool = toolById('hobab-sekke')!;
  root.replaceChildren(
    pageShell(
      tool,
      `
      <form class="panel" id="calc">
        <label for="coin">نوع سکه</label>
        <select id="coin">${coinOptions('emami')}</select>
        <div class="field-grid">
          <div>
            <label for="weight">وزن (گرم)</label>
            <input id="weight" inputmode="decimal" value="۸٫۱۳۳" autocomplete="off" />
          </div>
          <div>
            <label for="purity">عیار (از ۱۰۰۰)</label>
            <input id="purity" inputmode="numeric" value="۹۰۰" autocomplete="off" />
          </div>
          <div>
            <label for="coin-price">قیمت سکه (تومان)</label>
            <input id="coin-price" inputmode="numeric" autocomplete="off" placeholder="مثلاً ۱۸۰,۰۰۰,۰۰۰" />
          </div>
          <div>
            <label for="gold-price">قیمت گرم طلای ۱۸ عیار (تومان)</label>
            <input id="gold-price" inputmode="numeric" autocomplete="off" placeholder="مثلاً ۱۸,۰۰۰,۰۰۰" />
          </div>
        </div>
        <p class="fineprint" id="rates-note">نرخ زنده در حال دریافت است…</p>
        <button class="primary" type="submit">حساب کن</button>
      </form>
      <section class="panel result" id="result" aria-live="polite">${emptyResultHtml()}</section>
      <p class="fineprint">
        فرمول: ارزش ذاتی = وزن × (عیار سکه ÷ عیار مبنای نرخ) × قیمت هر گرم؛ حباب =
        (قیمت سکه − ارزش ذاتی) ÷ ارزش ذاتی × ۱۰۰. عیار مبنای نرخ ۱۸ عیار (۷۵۰) است.
        کارمزد، مالیات و ارزش کلکسیونی در محاسبه نیست.
      </p>
      `,
    ),
  );

  const result = resultCard(root);
  const select = root.querySelector<HTMLSelectElement>('#coin')!;
  const coinPriceField = root.querySelector<HTMLInputElement>('#coin-price')!;
  const note = root.querySelector<HTMLElement>('#rates-note')!;
  let rates: RateSnapshot | null = null;
  let liveCoinPrice: number | null = null;
  let liveGoldPrice: number | null = null;

  const run = (): void => {
    const coinPrice = numberValue(root, 'coin-price');
    const weight = numberValue(root, 'weight');
    const purity = numberValue(root, 'purity');
    const goldPrice = numberValue(root, 'gold-price');
    if (coinPrice == null || weight == null || purity == null || goldPrice == null) {
      result.innerHTML = emptyResultHtml();
      return;
    }
    const estimate = coinBubble(coinPrice, weight, purity, goldPrice);
    if (estimate == null) {
      result.innerHTML = errorHtml(
        'قیمت‌ها و وزن باید صفر یا بزرگ‌تر باشند و عیارها بین ۱ و ۱۰۰۰.',
      );
      return;
    }
    result.innerHTML = resultHtml(
      'حباب سکه',
      fa(estimate.bubblePercent, 1),
      '٪',
      [
        {
          label: 'ارزش ذاتی (طلای داخل سکه)',
          value: roundedToman(estimate.intrinsicToman),
        },
        { label: 'قیمت سکه', value: formatToman(estimate.coinPriceToman) },
        {
          label: 'اختلاف با ارزش ذاتی',
          value: `${roundedToman(estimate.premiumToman)}`,
          hint: estimate.premiumToman < 0 ? 'زیر ارزش ذاتی (حباب منفی)' : 'بیش از ارزش ذاتی',
        },
        {
          label: 'وزن / عیار سکه',
          value: `${fa(estimate.weightGrams, 3)} گرم / ${fa(estimate.purityPermille)} از ۱۰۰۰`,
        },
      ],
      'حباب مثبت یعنی سکه از طلایش گران‌تر است. حباب منفی نادر است و معمولاً فرصت فروش طلا نیست؛ کارمزد بازار را هم در نظر بگیر.',
    );
  };

  /** Preset choice owns weight and purity; the user may then edit them and keep the edit. */
  const syncCoinPreset = (): void => {
    const coin = coinPreset(select.value) ?? COIN_PRESETS[0]!;
    setInputValue(root, 'weight', coin.weightGrams);
    setInputValue(root, 'purity', coin.purityPermille);
  };

  const applyLivePrices = (force: boolean): void => {
    const coin = coinPreset(select.value) ?? COIN_PRESETS[0]!;
    const price = rates ? rateToman(rates, coin.rateKey) : null;
    if (price == null) return;
    const typed = parseNumber(coinPriceField.value);
    if (force || coinPriceField.value.trim() === '' || typed == null || typed === liveCoinPrice) {
      coinPriceField.value = String(Math.round(price));
    }
    liveCoinPrice = price;
  };

  const applyRates = (snapshot: RateSnapshot, stale: boolean, force = false): void => {
    rates = snapshot;
    applyLivePrices(force);
    const goldField = root.querySelector<HTMLInputElement>('#gold-price')!;
    const gold = rateToman(snapshot, GOLD_18_RATE_KEY);
    if (gold != null) {
      const typed = parseNumber(goldField.value);
      if (force || goldField.value.trim() === '' || typed == null || typed === liveGoldPrice) {
        goldField.value = String(Math.round(gold));
      }
      liveGoldPrice = gold;
      note.textContent = `نرخ طلای ۱۸ عیار: ${formatToman(gold)}${
        stale ? ' (ذخیره‌شده)' : ''
      } — ${ratesUpdatedLabel(snapshot)}`;
    } else {
      note.textContent = 'نرخ طلا در دادهٔ زنده نبود؛ قیمت‌ها را دستی وارد کن.';
    }
    run();
  };

  bindCalculator(root, 'calc', run);
  select.addEventListener('change', () => {
    syncCoinPreset();
    if (rates) {
      applyRates(rates, false, true);
    } else {
      coinPriceField.value = '';
      liveCoinPrice = null;
      run();
    }
  });
  syncCoinPreset();

  const cached = cachedRates();
  if (cached) applyRates(cached, true);
  void fetchRates().then((fresh) => {
    if (fresh) applyRates(fresh, false);
    else if (!cached) note.textContent = 'نرخ زنده در دسترس نیست؛ قیمت‌ها را دستی وارد کن.';
  });
}

// ───────────────── هزینهٔ قهوه / سیگار ─────────────────

const INFLATION_CHOICES = [0, 10, 20, 30, 40, 50] as const;

export function renderHazine(root: HTMLElement): void {
  const tool = toolById('hazine-adi')!;
  root.replaceChildren(
    pageShell(
      tool,
      `
      <form class="panel" id="calc">
        <label for="daily">هزینهٔ روزانه (قهوه، سیگار یا هر عادت) — تومان</label>
        <input id="daily" inputmode="numeric" value="۱۰۰,۰۰۰" autocomplete="off" />
        <div class="field-grid">
          <div>
            <label for="days">روز در سال</label>
            <input id="days" inputmode="numeric" value="۳۶۵" autocomplete="off" />
          </div>
          <div>
            <label for="inflation">تورم سالانه</label>
            <select id="inflation">
              ${INFLATION_CHOICES.map(
                (rate) =>
                  `<option value="${rate}"${rate === 40 ? ' selected' : ''}>${fa(rate)}٪</option>`,
              ).join('')}
            </select>
          </div>
          <div>
            <label for="years">چند سال</label>
            <input id="years" inputmode="numeric" value="۵" autocomplete="off" />
          </div>
        </div>
        <button class="primary" type="submit">حساب کن</button>
      </form>
      <section class="panel result" id="result" aria-live="polite">${emptyResultHtml()}</section>
      <p class="fineprint">
        فرض: هزینهٔ روزانه امروز است، هر روز سال تکرار می‌شود و قیمت یک‌بار در سال با نرخ
        انتخابی مرکب می‌شود. این هزینهٔ اسمی است، نه هزینهٔ فرصت سرمایه‌گذاری آن پول.
      </p>
      `,
    ),
  );

  const result = resultCard(root);
  const run = (): void => {
    const daily = numberValue(root, 'daily');
    const days = numberValue(root, 'days');
    const years = numberValue(root, 'years');
    const inflation = Number(selectValue(root, 'inflation'));
    if (daily == null || days == null || years == null) {
      result.innerHTML = emptyResultHtml();
      return;
    }
    const estimate = habitEstimate(daily, days, inflation, years);
    if (estimate == null) {
      result.innerHTML = errorHtml('هزینه، روزها و سال‌ها باید معتبر باشند (سال بین ۱ تا ۱۰۰۰).');
      return;
    }
    result.innerHTML = resultHtml(
      `هزینهٔ ${fa(estimate.years)} سال`,
      fa(Math.round(estimate.totalToman)),
      'تومان',
      [
        { label: 'سال اول', value: roundedToman(estimate.firstYearToman) },
        {
          label: 'سال آخر',
          value: roundedToman(estimate.finalYearToman),
          hint: `با تورم ${formatPercent(estimate.inflationPercent, 0)} مرکب`,
        },
        { label: 'میانگین سالانه', value: roundedToman(estimate.totalToman / estimate.years) },
        { label: 'هزینهٔ روزانه', value: formatToman(daily) },
      ],
      'اگر همان پول را سرمایه‌گذاری می‌کردی، عدد دیگری می‌شد؛ این فقط خرج اسمی عادت است.',
    );
  };

  bindCalculator(root, 'calc', run);
}

// ───────────────────────── قسط وام ─────────────────────────

export function renderGhast(root: HTMLElement): void {
  const tool = toolById('ghast-vam')!;
  root.replaceChildren(
    pageShell(
      tool,
      `
      <form class="panel" id="calc">
        <label for="principal">مبلغ وام (تومان)</label>
        <input id="principal" inputmode="numeric" value="۱۰۰,۰۰۰,۰۰۰" autocomplete="off" />
        <div class="field-grid">
          <div>
            <label for="rate">نرخ سالانه (٪)</label>
            <input id="rate" inputmode="decimal" value="۲۳" autocomplete="off" />
          </div>
          <div>
            <label for="months">تعداد ماه</label>
            <input id="months" inputmode="numeric" value="۶۰" autocomplete="off" />
          </div>
        </div>
        <button class="primary" type="submit">حساب کن</button>
      </form>
      <section class="panel result" id="result" aria-live="polite">${emptyResultHtml()}</section>
      <p class="fineprint">
        قسط ثابت (PMT)؛ r نرخ ماهانه است:
        <code class="formula" dir="ltr">PMT = P × r / (1 − (1 + r)⁻ⁿ),  r = annual rate / 1200</code>
        فرض: نرخ ثابت، پرداخت پایان هر ماه، بدون کارمزد، بیمه، تنفس یا قسط آخر متفاوت.
      </p>
      `,
    ),
  );

  const result = resultCard(root);
  const run = (): void => {
    const principal = numberValue(root, 'principal');
    const rate = numberValue(root, 'rate');
    const months = numberValue(root, 'months');
    if (principal == null || rate == null || months == null) {
      result.innerHTML = emptyResultHtml();
      return;
    }
    const estimate = loanEstimate(principal, rate, months);
    if (estimate == null) {
      result.innerHTML = errorHtml(
        'مبلغ و نرخ باید صفر یا بزرگ‌تر و تعداد ماه بین ۱ تا ۱۲۰۰ باشد.',
      );
      return;
    }
    result.innerHTML = resultHtml(
      'قسط ماهانه',
      fa(Math.round(estimate.installmentToman)),
      'تومان',
      [
        { label: 'مجموع بازپرداخت', value: roundedToman(estimate.totalToman) },
        { label: 'مجموع سود', value: roundedToman(estimate.interestToman) },
        {
          label: 'نرخ ماهانه',
          value: formatPercent(estimate.monthlyRate * 100, 2),
          hint: 'نرخ اسمی، مرکب ماهانه',
        },
        { label: 'تعداد قسط', value: `${fa(estimate.months)} ماه` },
      ],
      'نرخ مؤثر واقعی با کارمزد و بیمه بالاتر می‌شود؛ این عدد فقط خود وام است.',
    );
  };

  bindCalculator(root, 'calc', run);
}

// ───────────────────────── index / 404 ─────────────────────────

export function renderToolsIndex(root: HTMLElement): void {
  root.replaceChildren(
    el(`
      <div>
        <header class="hero tool-hero">
          <a class="crumb" href="/">اندیس</a>
          <h1>${escape(TOOLS_INDEX_META.heading)}</h1>
          <p class="hero-label">${escape(TOOLS_INDEX_META.lead)}</p>
        </header>
        <div class="page stack">
          <div class="tools-grid">
            ${TOOLS.map(
              (tool) => `
              <a class="tool-card" href="${tool.href}">
                <span class="tool-card-body">
                  <span class="tool-card-title">${escape(tool.heading)}</span>
                  <span class="muted">${escape(tool.blurb)}</span>
                </span>
                <span class="tool-card-go" aria-hidden="true">←</span>
              </a>`,
            ).join('')}
          </div>
          <p class="fineprint">
            نرخ‌ها از همان سرویس نرخ اندیس می‌آیند و روی دستگاه ذخیره می‌شوند؛ هیچ
            ورودی‌ای به سرور فرستاده نمی‌شود. محاسبه‌ها تخمینی‌اند، نه مشاورهٔ مالی یا حقوقی.
          </p>
        </div>
      </div>
    `),
  );
}

export function renderToolsNotFound(root: HTMLElement): void {
  root.replaceChildren(
    el(`
      <div>
        <header class="hero tool-hero">
          <a class="crumb" href="/">اندیس</a>
          <h1>این صفحه پیدا نشد</h1>
          <p class="hero-label">نشانی اشتباه است یا این ماشین‌حساب هنوز ساخته نشده.</p>
        </header>
        <div class="page stack">
          <a class="panel tools-entry" href="/tools/">
            <span class="tools-entry-body">
              <strong>همهٔ ماشین‌حساب‌ها</strong>
              <span class="muted">هفت ابزار مالی</span>
            </span>
            <span aria-hidden="true">←</span>
          </a>
        </div>
      </div>
    `),
  );
}

export const PAGES: Readonly<Record<string, (root: HTMLElement) => void>> = Object.freeze({
  mehrieh: renderMehrieh,
  'takhir-tadieh': renderTakhir,
  tavarom: renderTavarom,
  hoghugh: renderHoghugh,
  'hobab-sekke': renderHobab,
  'hazine-adi': renderHazine,
  'ghast-vam': renderGhast,
});
