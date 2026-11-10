import { describe, it, expect } from 'vitest';
import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const repoRoot = path.resolve(__dirname, '../..');

describe('Phase A Security & Public Readiness Gate Tests', () => {
  it('ReleaseWorkflowSecurityTest: release.yml requires KEYSTORE_B64 and has no hardcoded passwords', () => {
    const releaseYml = fs.readFileSync(
      path.join(repoRoot, '.github/workflows/release.yml'),
      'utf8',
    );
    expect(releaseYml).not.toContain('zheton2026');
    expect(releaseYml).not.toContain('keytool -genkeypair');
    expect(releaseYml).toContain('if [ -z "$KEYSTORE_B64" ]');
    expect(releaseYml).toContain('exit 1');
    expect(releaseYml).toContain('SHA256SUMS');
  });

  it('RepoHygieneTest: git ls-files contains no non-product artifacts, keystores, or walkthrough dumps', () => {
    const tracked = execSync('git ls-files', { cwd: repoRoot, encoding: 'utf8' })
      .split(/\r?\n/)
      .filter(Boolean);

    const forbidden = tracked.filter(
      (f) =>
        f.startsWith('.aistudio/') ||
        f.startsWith('public/assets/aistudio/') ||
        f.startsWith('pwa/public/assets/aistudio/') ||
        f.startsWith('docs/screenshots/walkthrough/') ||
        f.endsWith('.jks') ||
        f.endsWith('.keystore') ||
        f.endsWith('.p12') ||
        f.endsWith('.key') ||
        f === 'keystore.properties' ||
        (f.startsWith('pwa/public/') && f.endsWith('.zip')) ||
        (f.startsWith('public/') && f.endsWith('.zip')),
    );

    expect(forbidden).toEqual([]);
  });

  it('LicenseNoticeTest: LICENSE preserves upstream and fork copyright and security docs exist', () => {
    const license = fs.readFileSync(path.join(repoRoot, 'LICENSE'), 'utf8');
    expect(license).toContain('MIT License');
    expect(license).toContain('securityvisions');
    expect(license).toContain('PouyaRN');

    for (const docFile of ['NOTICE.md', 'THIRD_PARTY_NOTICES.md', 'SECURITY.md', '.gitleaks.toml']) {
      const fullPath = path.join(repoRoot, docFile);
      expect(fs.existsSync(fullPath), `${docFile} should exist`).toBe(true);
      expect(fs.statSync(fullPath).size).toBeGreaterThan(50);
    }
  });

  it('ManifestSecurityTest & BackupRulesTest: AndroidManifest and XML rules enforce offline privacy', () => {
    const manifest = fs.readFileSync(
      path.join(repoRoot, 'app/src/main/AndroidManifest.xml'),
      'utf8',
    );
    expect(manifest).toContain('android:allowBackup="false"');
    expect(manifest).toContain('android:usesCleartextTraffic="false"');
    expect(manifest).toContain('android:networkSecurityConfig="@xml/network_security_config"');
    expect(manifest).not.toContain('android:debuggable="true"');
    expect(manifest).not.toContain('android:autoVerify="true"');

    const backupRules = fs.readFileSync(
      path.join(repoRoot, 'app/src/main/res/xml/backup_rules.xml'),
      'utf8',
    );
    expect(backupRules).not.toContain('<include');

    const netSec = fs.readFileSync(
      path.join(repoRoot, 'app/src/main/res/xml/network_security_config.xml'),
      'utf8',
    );
    expect(netSec).toContain('cleartextTrafficPermitted="false"');
  });

  it('NoSensitiveLogTest: Android Kotlin sources do not log financial amounts or PII', () => {
    const kotlinDir = path.join(repoRoot, 'app/src/main/java/com/doxigo/muchtoman');
    const sensitiveLogRegex =
      /Log\.[divew]\s*\([^)]*(total=\$|\bamount=\$|\bbalance=\$|\bfold=\$|\bderived=\$|\bfoldedRial\b|\bbalance\.rial\b|\btotalRial\b|\binstallmentRial\b|\bfullPan\b|\bsheba\b|m\.body)/;

    const walk = (dir: string): string[] =>
      fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
        const res = path.join(dir, entry.name);
        return entry.isDirectory() ? walk(res) : res.endsWith('.kt') ? [res] : [];
      });

    const violations: string[] = [];
    for (const file of walk(kotlinDir)) {
      const content = fs.readFileSync(file, 'utf8');
      const match = sensitiveLogRegex.exec(content);
      if (match) {
        violations.push(`${path.relative(repoRoot, file)}: ${match[0]}`);
      }
    }
    expect(violations).toEqual([]);
  });
});
