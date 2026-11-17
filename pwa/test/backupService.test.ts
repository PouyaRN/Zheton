import { describe, expect, it } from 'vitest';
import {
  DEFAULT_KDF_ITERATIONS,
  exportEncryptedBackup,
  importEncryptedBackup,
} from '../src/services/backupService';
import { INITIAL_STATE, AppPersistedState } from '../src/services/storageService';

describe('Portable Encrypted Backup Service (.zheton)', () => {
  const sampleState: AppPersistedState = {
    ...INITIAL_STATE,
    hasSeenOnboarding: true,
    themeMode: 'dark',
    cycleConfig: { anchorDay: 25, targetMonthlyBudgetToman: 42_000_000 },
    accounts: [
      {
        id: 'acc_test',
        bankKey: 'BLU',
        bankNameFa: 'بلو بانک',
        pan: '6219861900001234',
        balanceToman: 12_500_000,
        nickname: 'حساب اصلی',
        theme: 'default',
        updatedAt: 1_760_000_000_000,
      },
    ],
  };

  it('pins OWASP 600,000 PBKDF2-HMAC-SHA256 iterations as default', () => {
    expect(DEFAULT_KDF_ITERATIONS).toBe(600_000);
  });

  it('round-trips encrypted backup bytes with AES-256-GCM + AAD header', async () => {
    const sealed = await exportEncryptedBackup(sampleState, 'StrongPass!1405', {
      iterations: 1000,
      nowMs: 1_760_000_000_000,
    });
    expect(String.fromCharCode(...sealed.slice(0, 4))).toBe('ZHTN');

    const restored = await importEncryptedBackup(sealed, 'StrongPass!1405');
    expect(restored.accounts).toHaveLength(1);
    expect(restored.accounts[0].balanceToman).toBe(12_500_000);
    expect(restored.themeMode).toBe('dark');
    expect(restored.cycleConfig.anchorDay).toBe(25);
  });

  it('rejects short passphrases, wrong passphrases, and tampered AAD headers', async () => {
    await expect(exportEncryptedBackup(sampleState, 'short', { iterations: 1000 })).rejects.toThrow();

    const sealed = await exportEncryptedBackup(sampleState, 'StrongPass!1405', { iterations: 1000 });
    await expect(importEncryptedBackup(sealed, 'WrongPass!1405')).rejects.toThrow();

    // Tamper iteration byte inside AAD header (offset 9)
    const tamperedHeader = new Uint8Array(sealed);
    tamperedHeader[9] ^= 0xff;
    await expect(importEncryptedBackup(tamperedHeader, 'StrongPass!1405')).rejects.toThrow();
  });
});
