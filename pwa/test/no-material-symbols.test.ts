import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const PWA_ROOT = join(__dirname, '..');

function walkFiles(dir: string, exts: string[]): string[] {
  const results: string[] = [];
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry === 'dist' || entry === '.git') continue;
    const full = join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) {
      results.push(...walkFiles(full, exts));
    } else if (exts.some((ext) => entry.endsWith(ext))) {
      results.push(full);
    }
  }
  return results;
}

describe('PWA Offline Icon & Font Hygiene Gate', () => {
  it('contains zero material-symbols-outlined references across pwa/src and index.html', () => {
    const files = [
      join(PWA_ROOT, 'index.html'),
      ...walkFiles(join(PWA_ROOT, 'src'), ['.ts', '.tsx', '.css']),
    ];

    for (const file of files) {
      const content = readFileSync(file, 'utf8');
      expect(content, `${file} must not reference material-symbols`).not.toMatch(
        /material-symbols-outlined|material-symbols-filled|Material\+Symbols/i,
      );
    }
  });

  it('contains zero Google Fonts CDN links across pwa/index.html and pwa/tools', () => {
    const htmlFiles = [
      join(PWA_ROOT, 'index.html'),
      ...walkFiles(join(PWA_ROOT, 'tools'), ['.html']),
    ];

    for (const file of htmlFiles) {
      const content = readFileSync(file, 'utf8');
      expect(content, `${file} must not load fonts.googleapis.com`).not.toContain('fonts.googleapis.com');
      expect(content, `${file} must not load fonts.gstatic.com`).not.toContain('fonts.gstatic.com');
    }
  });

  it('contains zero emoji glyphs across pwa/src React components and utilities', () => {
    const srcFiles = walkFiles(join(PWA_ROOT, 'src'), ['.ts', '.tsx']);
    const emojiRegex = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/u;

    for (const file of srcFiles) {
      const content = readFileSync(file, 'utf8');
      expect(emojiRegex.test(content), `${file} must not contain Unicode emoji glyphs`).toBe(false);
    }
  });

  it('contains all 14 Android asset & flag VectorDrawable XMLs and zero emojis in Android Kotlin sources', () => {
    const repoRoot = join(PWA_ROOT, '..');
    const drawableDir = join(repoRoot, 'app', 'src', 'main', 'res', 'drawable');
    const requiredDrawables = [
      'ic_asset_cash.xml',
      'ic_asset_bank.xml',
      'ic_asset_usd.xml',
      'ic_asset_eur.xml',
      'ic_asset_gbp.xml',
      'ic_asset_gold.xml',
      'ic_asset_silver.xml',
      'ic_asset_coin.xml',
      'ic_asset_stock.xml',
      'ic_asset_crypto.xml',
      'flag_no.xml',
      'flag_tr.xml',
      'flag_ae.xml',
      'flag_ca.xml',
    ];
    for (const fileName of requiredDrawables) {
      const xml = readFileSync(join(drawableDir, fileName), 'utf8');
      expect(xml, `${fileName} must contain <vector>`).toContain('<vector');
      expect(xml, `${fileName} must contain android:pathData`).toContain('android:pathData=');
    }

    const ktFiles = walkFiles(join(repoRoot, 'app', 'src', 'main', 'java'), ['.kt']);
    const emojiRegex = /[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\u{1F1E6}-\u{1F1FF}]/u;
    for (const file of ktFiles) {
      const content = readFileSync(file, 'utf8');
      expect(emojiRegex.test(content), `${file} must not contain Unicode emoji glyphs`).toBe(false);
    }
  });
});
