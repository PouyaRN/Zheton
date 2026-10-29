/**
 * One list of the seven calculators, and the page metadata that goes with each.
 *
 * This is the single source of truth for the `/tools/` index, the router, `<title>`, the meta
 * description, the canonical URL and the sitemap. The static HTML files repeat the same titles
 * and descriptions so a crawler that does not run JavaScript still sees them; the test suite
 * checks that the two copies agree.
 */

export const SITE_ORIGIN = 'https://sync.muchtoman.com';
export const TOOLS_PATH = '/tools/';

export interface ToolMeta {
  /** Stable id, also the last segment of the route. */
  readonly id: string;
  readonly href: string;
  readonly title: string;
  readonly description: string;
  readonly heading: string;
  readonly lead: string;
  /** One line for the index card. */
  readonly blurb: string;
}

export const TOOLS_INDEX_META = {
  title: 'ماشین‌حساب‌های مالی | اندیس',
  description:
    'هفت ماشین‌حساب فارسی برای مهریه، تأخیر تادیه، تورم، قدرت خرید حقوق، حباب سکه، هزینهٔ عادت روزانه و قسط وام.',
  heading: 'ماشین‌حساب‌ها',
  lead: 'هفت محاسبهٔ مالی با نرخ روز یا دادهٔ رسمی. بدون ثبت‌نام، بدون ارسال داده.',
} as const;

export const TOOLS: readonly ToolMeta[] = Object.freeze([
  {
    id: 'mehrieh',
    href: '/tools/mehrieh/',
    title: 'محاسبهٔ ارزش مهریه با نرخ سکه | اندیس',
    description:
      'ارزش مهریه را بر اساس تعداد سکه و نرخ زندهٔ سکهٔ امامی، بهار، نیم، ربع و گرمی حساب کن؛ با ورودی دستی نرخ. تخمینی و بدون ثبت‌نام.',
    heading: 'ارزش مهریه',
    lead: 'تعداد سکه را با نرخ روز حساب کن. این یک تخمین از هزینهٔ جایگزینی است، نه ارزیابی رسمی.',
    blurb: 'ارزش سکه‌های مهریه با نرخ روز سکه',
  },
  {
    id: 'takhir-tadieh',
    href: '/tools/takhir-tadieh/',
    title: 'محاسبهٔ خسارت تأخیر تادیه با تورم | اندیس',
    description:
      'خسارت تأخیر تادیه را با شاخص قیمت مصرف‌کننده و مادهٔ ۵۲۲ قانون آیین دادرسی مدنی تخمین بزن. مبلغ بدهی، سال سررسید و سال پرداخت را وارد کن.',
    heading: 'خسارت تأخیر تادیه',
    lead: 'تخمین جبران کاهش قدرت خرید بدهی، بر پایهٔ شاخص قیمت مصرف‌کننده.',
    blurb: 'تخمین خسارت تأخیر با شاخص CPI',
  },
  {
    id: 'tavarom',
    href: '/tools/tavarom/',
    title: 'محاسبهٔ تورم و قدرت خرید تومان | اندیس',
    description:
      'قدرت خرید مبلغی تومان را از سالی به سال دیگر با شاخص CPI محاسبه کن؛ هم‌ارز اسمی و تورم انباشته را ببین. دادهٔ ۱۳۸۰ تا ۱۴۰۵.',
    heading: 'تورم و قدرت خرید',
    lead: 'ببین مبلغی در سال مبدأ، به قیمت‌های سال مقصد چقدر می‌ارزد.',
    blurb: 'قدرت خرید تومان بین دو سال',
  },
  {
    id: 'hoghugh',
    href: '/tools/hoghugh/',
    title: 'قدرت خرید حقوق ۱۴۰۰ تا ۱۴۰۵ | اندیس',
    description:
      'قدرت خرید حقوق ۱۴۰۰ را با قیمت‌های ۱۴۰۵ بسنج: چقدر ارزشش کم شده و در ۱۴۰۵ چقدر حقوق لازم است تا همان قدرت خرید حفظ شود.',
    heading: 'قدرت خرید حقوق',
    lead: 'حقوق ۱۴۰۰ را با قیمت‌های ۱۴۰۵ بسنج.',
    blurb: 'حقوق ۱۴۰۰ در قیمت‌های ۱۴۰۵',
  },
  {
    id: 'hobab-sekke',
    href: '/tools/hobab-sekke/',
    title: 'محاسبهٔ حباب سکه | اندیس',
    description:
      'حباب سکه را با وزن، عیار، قیمت سکه و قیمت گرم طلای ۱۸ عیار حساب کن؛ ارزش ذاتی و درصد حباب را ببین. نرخ زنده یا دستی.',
    heading: 'حباب سکه',
    lead: 'فاصلهٔ قیمت سکه از ارزش طلای داخلش، به درصد.',
    blurb: 'فاصلهٔ قیمت سکه از ارزش ذاتی طلا',
  },
  {
    id: 'hazine-adi',
    href: '/tools/hazine-adi/',
    title: 'هزینهٔ سالانهٔ قهوه و سیگار | اندیس',
    description:
      'هزینهٔ سالانه و چندسالهٔ قهوه یا سیگار را با هزینهٔ روزانه و نرخ تورم قابل انتخاب محاسبه کن؛ هزینهٔ هر سال و مجموع را ببین.',
    heading: 'هزینهٔ قهوه و سیگار',
    lead: 'هزینهٔ سالانهٔ یک عادت روزانه، با تورم قابل انتخاب.',
    blurb: 'هزینهٔ سالانه و چندسالهٔ یک عادت',
  },
  {
    id: 'ghast-vam',
    href: '/tools/ghast-vam/',
    title: 'محاسبهٔ قسط وام | اندیس',
    description:
      'قسط ماهانهٔ وام را با مبلغ اصل، نرخ سالانه و تعداد ماه حساب کن؛ مجموع بازپرداخت و سود را هم ببین. فرمول قسط ثابت (PMT).',
    heading: 'قسط وام',
    lead: 'قسط ماهانه، مجموع بازپرداخت و سود وام.',
    blurb: 'قسط ماهانه و مجموع بازپرداخت وام',
  },
]);

export function toolById(id: string): ToolMeta | undefined {
  return TOOLS.find((tool) => tool.id === id);
}

/**
 * What the current path is asking for.
 *
 * A tool route works with or without the trailing slash and with a query string; the canonical
 * URL always carries the slash because that is the static directory the build emits. Anything
 * under `/tools/` that is not the index or a known tool is a 404 page, not a silent redirect
 * home — a wrong URL should say so.
 */
export type ToolsRoute =
  | { kind: 'index' }
  | { kind: 'tool'; tool: ToolMeta }
  | { kind: 'not-found' };

export function toolsRoute(pathname: string): ToolsRoute | null {
  let path = pathname.split(/[?#]/, 1)[0] ?? '';
  if (path.length > 1) path = path.replace(/\/+$/, '');
  if (path !== '/tools' && !path.startsWith('/tools/')) return null;
  if (path === '/tools') return { kind: 'index' };
  const tool = TOOLS.find((entry) => entry.href.replace(/\/$/, '') === path);
  return tool ? { kind: 'tool', tool } : { kind: 'not-found' };
}

/** The canonical absolute URL for a path, always with its trailing slash. */
export function canonicalUrl(pathname: string): string {
  let path = pathname.split(/[?#]/, 1)[0] ?? '/';
  if (!path.startsWith('/')) path = `/${path}`;
  if (path !== '/' && !path.endsWith('/')) path = `${path}/`;
  return `${SITE_ORIGIN}${path}`;
}

function ensureMeta(name: string): HTMLMetaElement {
  let element = document.head.querySelector<HTMLMetaElement>(`meta[name="${name}"]`);
  if (element == null) {
    element = document.createElement('meta');
    element.name = name;
    document.head.append(element);
  }
  return element;
}

function ensureCanonical(): HTMLLinkElement {
  let element = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
  if (element == null) {
    element = document.createElement('link');
    element.rel = 'canonical';
    document.head.append(element);
  }
  return element;
}

/**
 * Writes the per-page title, description and canonical link. Static HTML already carries them;
 * this runs for the SPA fallback case (a URL without its trailing slash that the host resolved
 * to `/index.html`) and keeps the document consistent with what is on screen.
 */
export function setPageMeta(title: string, description: string, pathname: string): void {
  document.title = title;
  ensureMeta('description').content = description;
  ensureCanonical().href = canonicalUrl(pathname);
}
