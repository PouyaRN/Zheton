import React, { useState } from 'react';
import { Icon } from './Icon';
import { AppPersistedState } from '../services/storageService';
import {
  exportEncryptedBackup,
  importEncryptedBackup,
  MIN_PASSPHRASE_LENGTH,
} from '../services/backupService';
import { toFaDigits } from '../utils/format';

interface BackupModalProps {
  isOpen: boolean;
  onClose: () => void;
  state: AppPersistedState;
  onRestoreState: (next: AppPersistedState) => void;
  onNotify: (msg: string) => void;
}

export const BackupModal: React.FC<BackupModalProps> = ({
  isOpen,
  onClose,
  state,
  onRestoreState,
  onNotify,
}) => {
  const [mode, setMode] = useState<'EXPORT' | 'IMPORT'>('EXPORT');
  const [passphrase, setPassphrase] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  if (!isOpen) return null;

  const isPassphraseValid = passphrase.length >= MIN_PASSPHRASE_LENGTH;

  const handleExport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isPassphraseValid || isBusy) {
      setErrorMsg(`رمز عبور باید دست‌کم ${toFaDigits(MIN_PASSPHRASE_LENGTH)} کاراکتر باشد.`);
      return;
    }
    setErrorMsg(null);
    setIsBusy(true);
    try {
      const sealed = await exportEncryptedBackup(state, passphrase);
      const buffer = new ArrayBuffer(sealed.byteLength);
      new Uint8Array(buffer).set(sealed);
      const blob = new Blob([buffer], { type: 'application/octet-stream' });
      const url = URL.createObjectURL(blob);
      const d = new Date();
      const stamp = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
      const a = document.createElement('a');
      a.href = url;
      a.download = `zheton-backup-${stamp}.zheton`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
      setPassphrase('');
      onNotify('فایل پشتیبان رمزنگاری‌شده با موفقیت ذخیره شد');
      onClose();
    } catch (err) {
      setErrorMsg((err as Error).message || 'خطا در تولید فایل پشتیبان');
    } finally {
      setIsBusy(false);
    }
  };

  const handleImport = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile || !passphrase || isBusy) {
      setErrorMsg('فایل پشتیبان و رمز عبور را وارد کنید.');
      return;
    }
    setErrorMsg(null);
    setIsBusy(true);
    try {
      const bytes = new Uint8Array(await selectedFile.arrayBuffer());
      const restored = await importEncryptedBackup(bytes, passphrase);
      onRestoreState(restored);
      setPassphrase('');
      setSelectedFile(null);
      onNotify('داده‌ها با موفقیت از فایل پشتیبان بازیابی شدند');
      onClose();
    } catch (err) {
      setErrorMsg((err as Error).message || 'بازیابی فایل پشتیبان ناموفق بود.');
    } finally {
      setIsBusy(false);
    }
  };

  return (
    <div
      data-testid="backup-modal"
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/50 backdrop-blur-sm p-0 sm:p-4"
      dir="rtl"
    >
      <div className="w-full max-w-md bg-[#f8f9fc] rounded-t-[32px] sm:rounded-[32px] overflow-hidden shadow-2xl border border-white flex flex-col">
        <div className="flex items-center justify-between px-5 py-4 bg-white border-b border-gray-100">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-2xl bg-gray-900 text-white flex items-center justify-center">
              <Icon name="shield_lock" size={20} />
            </div>
            <div>
              <h2 className="text-[15px] font-black text-on-surface">پشتیبان‌گیری رمزنگاری‌شده</h2>
              <p className="text-[11px] text-on-surface-variant">رمزنگاری آفلاین AES-256-GCM با کلید محلی</p>
            </div>
          </div>
          <button
            type="button"
            aria-label="بستن پشتیبان‌گیری"
            onClick={onClose}
            className="w-9 h-9 rounded-full bg-surface-container-low flex items-center justify-center text-on-surface"
          >
            <Icon name="close" size={18} />
          </button>
        </div>

        <div className="p-4 space-y-4">
          <div className="grid grid-cols-2 gap-2 bg-white p-1.5 rounded-2xl border border-gray-100">
            <button
              type="button"
              onClick={() => {
                setMode('EXPORT');
                setErrorMsg(null);
              }}
              className={`py-2 rounded-xl text-[12px] font-extrabold flex items-center justify-center gap-1.5 transition ${
                mode === 'EXPORT' ? 'bg-gray-900 text-white' : 'text-on-surface-variant'
              }`}
            >
              <Icon name="download" size={15} />
              <span>خروجی (.zheton)</span>
            </button>
            <button
              type="button"
              onClick={() => {
                setMode('IMPORT');
                setErrorMsg(null);
              }}
              className={`py-2 rounded-xl text-[12px] font-extrabold flex items-center justify-center gap-1.5 transition ${
                mode === 'IMPORT' ? 'bg-gray-900 text-white' : 'text-on-surface-variant'
              }`}
            >
              <Icon name="upload" size={15} />
              <span>بازیابی پشتیبان</span>
            </button>
          </div>

          {errorMsg && (
            <div
              data-testid="backup-error-banner"
              className="p-3 rounded-2xl bg-rose-50 border border-rose-200 text-rose-800 text-[12px] font-bold"
            >
              {errorMsg}
            </div>
          )}

          {mode === 'EXPORT' ? (
            <form onSubmit={handleExport} className="bg-white rounded-[24px] p-4 space-y-3 border border-gray-100">
              <p className="text-[12px] text-on-surface-variant leading-relaxed">
                یک رمز عبور حداقل {toFaDigits(MIN_PASSPHRASE_LENGTH)} کاراکتری برای رمزنگاری داده‌های حساب‌ها، تراکنش‌ها و اهداف تعیین کنید. این رمز هیچ‌جا ذخیره نمی‌شود.
              </p>
              <div>
                <label className="block text-[11px] font-bold text-on-surface-variant mb-1">
                  رمز عبور پشتیبان (حداقل {toFaDigits(MIN_PASSPHRASE_LENGTH)} کاراکتر)
                </label>
                <input
                  data-testid="backup-passphrase-input"
                  type="password"
                  value={passphrase}
                  onChange={(e) => setPassphrase(e.target.value)}
                  placeholder="••••••••"
                  className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-sm font-bold text-on-surface"
                />
              </div>
              <button
                data-testid="backup-export-submit"
                type="submit"
                disabled={!isPassphraseValid || isBusy}
                className="w-full py-3 rounded-full bg-primary text-on-primary text-[13px] font-extrabold disabled:opacity-45 transition"
              >
                {isBusy ? 'در حال رمزنگاری...' : 'ساخت و دانلود فایل پشتیبان'}
              </button>
            </form>
          ) : (
            <form onSubmit={handleImport} className="bg-white rounded-[24px] p-4 space-y-3 border border-gray-100">
              <div>
                <label className="block text-[11px] font-bold text-on-surface-variant mb-1">
                  انتخاب فایل پشتیبان (`.zheton`)
                </label>
                <input
                  data-testid="backup-file-input"
                  type="file"
                  accept=".zheton,application/octet-stream"
                  onChange={(e) => setSelectedFile(e.target.files?.[0] ?? null)}
                  className="w-full text-[12px] text-on-surface-variant file:ml-3 file:py-2 file:px-3 file:rounded-xl file:border-0 file:bg-surface-container-low file:font-bold"
                />
              </div>
              <div>
                <label className="block text-[11px] font-bold text-on-surface-variant mb-1">
                  رمز عبور فایل
                </label>
                <input
                  data-testid="backup-passphrase-input"
                  type="password"
                  value={passphrase}
                  onChange={(e) => setPassphrase(e.target.value)}
                  placeholder="••••••••"
                  className="w-full bg-surface-container-low rounded-2xl px-4 py-2.5 text-sm font-bold text-on-surface"
                />
              </div>
              <button
                data-testid="backup-import-submit"
                type="submit"
                disabled={!selectedFile || !passphrase || isBusy}
                className="w-full py-3 rounded-full bg-primary text-on-primary text-[13px] font-extrabold disabled:opacity-45 transition"
              >
                {isBusy ? 'در حال رمزگشایی...' : 'رمزگشایی و بازیابی داده‌ها'}
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};
