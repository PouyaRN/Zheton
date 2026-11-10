import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { fetchTgjuAjax } from './sources/tgju.ts';
import { fetchBonbast } from './sources/bonbast.ts';
import { fetchWallexMarkets } from './sources/wallex.ts';
import { fetchNobitexUsdt } from './sources/nobitex.ts';
import { fetchCoinGeckoMarkets } from './sources/coingecko.ts';
import { fetchBinanceVisionUsd } from './sources/binance.ts';
import { fetchFawazForex } from './sources/fawazahmed0.ts';
import { mergeRatesSources } from './merge.ts';
import { validateRatesPayload } from './validate.ts';
import { signRatesBytes, verifyRatesBytes } from './sign.ts';
import { RatesPayload, ReleaseInfo } from './types.ts';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PIPELINE_ROOT = resolve(__dirname, '..');

async function fetchPreviousPayload(): Promise<RatesPayload | null> {
  try {
    const res = await fetch('https://pouyarn.github.io/Zheton/rates.json', {
      signal: AbortSignal.timeout(8_000),
      headers: { Accept: 'application/json' },
    });
    if (res.ok) {
      const json = (await res.json()) as RatesPayload;
      if (json && json.schema === 1 && json.toman) {
        return json;
      }
    }
  } catch {
    // Fallback to golden fixture
  }

  try {
    const goldenPath = join(PIPELINE_ROOT, 'fixtures', 'rates.golden.json');
    if (existsSync(goldenPath)) {
      return JSON.parse(readFileSync(goldenPath, 'utf8')) as RatesPayload;
    }
  } catch {
    // ignore
  }
  return null;
}

async function fetchLatestReleaseInfo(): Promise<ReleaseInfo | null> {
  try {
    const res = await fetch('https://api.github.com/repos/PouyaRN/Zheton/releases/latest', {
      signal: AbortSignal.timeout(8_000),
      headers: {
        Accept: 'application/vnd.github+json',
        'User-Agent': 'ZhetonRatesPipeline/1.2',
      },
    });
    if (!res.ok) return null;
    const data = (await res.json()) as any;
    const tagName = String(data?.tag_name || data?.name || '').trim();
    const htmlUrl = String(data?.html_url || '').trim();
    if (!tagName || !htmlUrl.startsWith('https://github.com/PouyaRN/Zheton/')) {
      return null;
    }
    const assets = Array.isArray(data?.assets) ? data.assets : [];
    const apkAsset = assets.find((a: any) => String(a?.name || '').endsWith('.apk'));
    const apkUrl = String(apkAsset?.browser_download_url || htmlUrl).trim();
    if (!apkUrl.startsWith('https://github.com/PouyaRN/Zheton/')) {
      return null;
    }
    const bodyLines = String(data?.body || '')
      .split(/\r?\n/)
      .map((l) => l.replace(/^[-*#>\s]+/, '').trim())
      .filter((l) => l.length > 0 && l.length <= 140)
      .slice(0, 5);

    return {
      name: tagName,
      url: htmlUrl,
      apk: apkUrl,
      notes: bodyLines.length > 0 ? bodyLines : [`نسخه ${tagName}`],
    };
  } catch {
    return null;
  }
}

async function safeCall<T>(label: string, fn: () => Promise<T>): Promise<T | null> {
  try {
    const result = await fn();
    console.log(`[rates-pipeline] Source '${label}' succeeded.`);
    return result;
  } catch (err) {
    console.warn(`[rates-pipeline] Source '${label}' failed: ${(err as Error).message}`);
    return null;
  }
}

export async function runPipeline(options: {
  outDir: string;
  dailyDir?: string;
  unsignedDev?: boolean;
}): Promise<{ payload: RatesPayload; jsonText: string; signatureBase64: string | null }> {
  const nowSec = Date.now();

  const [
    previous,
    latestRelease,
    tgju,
    bonbast,
    wallex,
    nobitex,
    coingecko,
    binanceUsd,
  ] = await Promise.all([
    fetchPreviousPayload(),
    fetchLatestReleaseInfo(),
    safeCall('tgju', () => fetchTgjuAjax()),
    safeCall('bonbast', () => fetchBonbast()),
    safeCall('wallex', () => fetchWallexMarkets()),
    safeCall('nobitex', () => fetchNobitexUsdt()),
    safeCall('coingecko', () => fetchCoinGeckoMarkets()),
    safeCall('binance', () => fetchBinanceVisionUsd()),
  ]);

  const baseUsdToman =
    tgju?.toman?.usd ??
    bonbast?.toman?.usd ??
    wallex?.prices?.usdt ??
    nobitex?.prices?.usdt ??
    previous?.toman?.usd ??
    80_000;

  const fawazForexToman = await safeCall('fawazahmed0', () => fetchFawazForex(baseUsdToman));

  const payload = mergeRatesSources({
    nowSec,
    kid: 'k1',
    tgju,
    bonbast,
    fawazForexToman,
    wallex,
    nobitex,
    coingecko,
    binanceUsd,
    previous,
    latestRelease,
  });

  const validation = validateRatesPayload(payload);
  for (const w of validation.warnings) {
    console.warn(`[rates-pipeline] WARNING: ${w}`);
  }
  if (!validation.valid) {
    throw new Error(`RatesPayload failed validation:\n${validation.errors.join('\n')}`);
  }

  const jsonText = JSON.stringify(payload, null, 2) + '\n';
  const jsonBytes = Buffer.from(jsonText, 'utf8');

  let signatureBase64: string | null = null;
  const privKeyPem = process.env.RATES_SIGNING_KEY?.trim();
  const pubKeyPath = join(PIPELINE_ROOT, 'keys', 'rates.pub.pem');
  const pubKeyPem = readFileSync(pubKeyPath, 'utf8');

  if (privKeyPem) {
    signatureBase64 = signRatesBytes(jsonBytes, privKeyPem);
    const verified = verifyRatesBytes(jsonBytes, signatureBase64, pubKeyPem);
    if (!verified) {
      throw new Error('Signature self-verification against keys/rates.pub.pem FAILED');
    }
    console.log('[rates-pipeline] Payload signed and verified with keys/rates.pub.pem.');
  } else if (options.unsignedDev) {
    console.warn('[rates-pipeline] Running in --unsigned-dev mode without RATES_SIGNING_KEY.');
  } else {
    throw new Error('RATES_SIGNING_KEY environment variable is required for production builds');
  }

  mkdirSync(options.outDir, { recursive: true });
  writeFileSync(join(options.outDir, 'rates.json'), jsonBytes);
  if (signatureBase64) {
    writeFileSync(join(options.outDir, 'rates.json.sig'), signatureBase64 + '\n', 'utf8');
  }

  if (options.dailyDir) {
    const dateStr = new Date(nowSec).toISOString().slice(0, 10);
    mkdirSync(options.dailyDir, { recursive: true });
    writeFileSync(join(options.dailyDir, `${dateStr}.json`), jsonBytes);
    if (signatureBase64) {
      writeFileSync(join(options.dailyDir, `${dateStr}.json.sig`), signatureBase64 + '\n', 'utf8');
    }
    console.log(`[rates-pipeline] Wrote daily snapshot ${dateStr}.json`);
  }

  return { payload, jsonText, signatureBase64 };
}

// CLI entrypoint
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const unsignedDev = args.includes('--unsigned-dev');
  const isDaily = args.includes('--daily');
  const outIdx = args.indexOf('--out-dir');
  const outDir =
    outIdx >= 0 && args[outIdx + 1]
      ? resolve(process.cwd(), args[outIdx + 1])
      : join(PIPELINE_ROOT, 'dist');
  const dailyDir = isDaily ? join(outDir, 'daily') : undefined;

  runPipeline({ outDir, dailyDir, unsignedDev }).catch((err) => {
    console.error('[rates-pipeline] FATAL:', err);
    process.exit(1);
  });
}
