import { AppPersistedState, INITIAL_STATE } from './storageService';

/**
 * Portable encrypted offline backup container (`*.zheton`):
 *
 * offset  size  field
 *      0     4   magic "ZHTN"
 *      4     1   envelopeVersion (1)
 *      5     1   kdfId (1 = PBKDF2-HMAC-SHA256)
 *      6     4   kdfIterations (uint32 big-endian, default 600,000)
 *     10    16   random salt
 *     26    12   random AES-GCM nonce
 *     38     4   ciphertextLength (uint32 big-endian)
 *     42     n   AES-256-GCM ciphertext || 16-byte auth tag (26-byte header as AAD)
 *   42+n    32   SHA-256(plaintext)
 */
export const BACKUP_MAGIC = new Uint8Array([0x5a, 0x48, 0x54, 0x4e]); // "ZHTN"
export const BACKUP_ENVELOPE_VERSION = 1;
export const KDF_ID_PBKDF2_SHA256 = 1;
export const DEFAULT_KDF_ITERATIONS = 600_000;
export const MAX_KDF_ITERATIONS = 2_000_000;
export const MIN_PASSPHRASE_LENGTH = 8;
export const HEADER_BYTES = 26;
export const NONCE_BYTES = 12;
export const SALT_BYTES = 16;
export const SHA256_BYTES = 32;
export const CIPHER_OFFSET = 42;
export const MAX_BACKUP_BYTES = 20 * 1024 * 1024;

export interface BackupEnvelopePayload {
  schemaVersion: 1;
  exportedAt: number;
  app: 'zheton-pwa';
  state: AppPersistedState;
}

function getCrypto(): Crypto {
  if (!globalThis.crypto?.subtle) {
    throw new Error('مرورگر فعلی از رمزنگاری WebCrypto پشتیبانی نمی‌کند.');
  }
  return globalThis.crypto;
}

function toArrayBuffer(view: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(view.byteLength);
  copy.set(view);
  return copy.buffer;
}

async function deriveAesKey(
  passphrase: string,
  salt: Uint8Array,
  iterations: number,
  usage: KeyUsage[],
): Promise<CryptoKey> {
  const cryptoApi = getCrypto();
  const keyMaterial = await cryptoApi.subtle.importKey(
    'raw',
    new TextEncoder().encode(passphrase),
    'PBKDF2',
    false,
    ['deriveKey'],
  );
  return cryptoApi.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: toArrayBuffer(salt),
      iterations,
      hash: 'SHA-256',
    },
    keyMaterial,
    { name: 'AES-GCM', length: 256 },
    false,
    usage,
  );
}

export async function exportEncryptedBackup(
  state: AppPersistedState,
  passphrase: string,
  options?: { iterations?: number; nowMs?: number },
): Promise<Uint8Array> {
  if (passphrase.length < MIN_PASSPHRASE_LENGTH) {
    throw new Error(`رمز عبور پشتیبان باید دست‌کم ${MIN_PASSPHRASE_LENGTH} کاراکتر باشد.`);
  }
  const iterations = options?.iterations ?? DEFAULT_KDF_ITERATIONS;
  if (!Number.isInteger(iterations) || iterations < 1 || iterations > MAX_KDF_ITERATIONS) {
    throw new Error('تعداد تکرار مشتق‌سازی کلید خارج از بازه مجاز است.');
  }

  const cryptoApi = getCrypto();
  const payload: BackupEnvelopePayload = {
    schemaVersion: 1,
    exportedAt: options?.nowMs ?? Date.now(),
    app: 'zheton-pwa',
    state,
  };
  const plaintext = new TextEncoder().encode(JSON.stringify(payload));
  const digest = new Uint8Array(await cryptoApi.subtle.digest('SHA-256', toArrayBuffer(plaintext)));

  const salt = cryptoApi.getRandomValues(new Uint8Array(SALT_BYTES));
  const nonce = cryptoApi.getRandomValues(new Uint8Array(NONCE_BYTES));

  const header = new Uint8Array(HEADER_BYTES);
  header.set(BACKUP_MAGIC, 0);
  header[4] = BACKUP_ENVELOPE_VERSION;
  header[5] = KDF_ID_PBKDF2_SHA256;
  new DataView(header.buffer).setUint32(6, iterations, false);
  header.set(salt, 10);

  const key = await deriveAesKey(passphrase, salt, iterations, ['encrypt']);
  const cipherBuf = await cryptoApi.subtle.encrypt(
    {
      name: 'AES-GCM',
      iv: toArrayBuffer(nonce),
      additionalData: toArrayBuffer(header),
      tagLength: 128,
    },
    key,
    toArrayBuffer(plaintext),
  );
  const cipherBytes = new Uint8Array(cipherBuf);

  const out = new Uint8Array(CIPHER_OFFSET + cipherBytes.byteLength + SHA256_BYTES);
  out.set(header, 0);
  out.set(nonce, 26);
  new DataView(out.buffer).setUint32(38, cipherBytes.byteLength, false);
  out.set(cipherBytes, CIPHER_OFFSET);
  out.set(digest, CIPHER_OFFSET + cipherBytes.byteLength);
  return out;
}

export async function importEncryptedBackup(
  bytes: Uint8Array,
  passphrase: string,
): Promise<AppPersistedState> {
  if (bytes.byteLength < CIPHER_OFFSET + 16 + SHA256_BYTES || bytes.byteLength > MAX_BACKUP_BYTES) {
    throw new Error('اندازه فایل پشتیبان نامعتبر است.');
  }
  for (let i = 0; i < BACKUP_MAGIC.length; i++) {
    if (bytes[i] !== BACKUP_MAGIC[i]) {
      throw new Error('قالب فایل پشتیبان ژتون معتبر نیست.');
    }
  }
  if (bytes[4] !== BACKUP_ENVELOPE_VERSION) {
    throw new Error('نسخهٔ این فایل پشتیبان پشتیبانی نمی‌شود.');
  }
  if (bytes[5] !== KDF_ID_PBKDF2_SHA256) {
    throw new Error('الگوریتم مشتق‌سازی کلید در این فایل پشتیبانی نمی‌شود.');
  }

  const header = bytes.slice(0, HEADER_BYTES);
  const iterations = new DataView(header.buffer, header.byteOffset, header.byteLength).getUint32(6, false);
  if (iterations < 1 || iterations > MAX_KDF_ITERATIONS) {
    throw new Error('تعداد تکرار KDF در فایل پشتیبان نامعتبر است.');
  }
  const salt = header.slice(10, 26);
  const nonce = bytes.slice(26, 38);
  const cipherLen = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getUint32(38, false);
  if (cipherLen < 16 || CIPHER_OFFSET + cipherLen + SHA256_BYTES !== bytes.byteLength) {
    throw new Error('طول بخش رمزنگاری‌شده در فایل پشتیبان دستکاری شده است.');
  }
  const ciphertext = bytes.slice(CIPHER_OFFSET, CIPHER_OFFSET + cipherLen);
  const expectedDigest = bytes.slice(CIPHER_OFFSET + cipherLen);

  const cryptoApi = getCrypto();
  const key = await deriveAesKey(passphrase, salt, iterations, ['decrypt']);

  let plainBuf: ArrayBuffer;
  try {
    plainBuf = await cryptoApi.subtle.decrypt(
      {
        name: 'AES-GCM',
        iv: toArrayBuffer(nonce),
        additionalData: toArrayBuffer(header),
        tagLength: 128,
      },
      key,
      toArrayBuffer(ciphertext),
    );
  } catch {
    throw new Error('رمز عبور اشتباه است یا فایل پشتیبان دستکاری شده است.');
  }

  const plaintext = new Uint8Array(plainBuf);
  const actualDigest = new Uint8Array(await cryptoApi.subtle.digest('SHA-256', toArrayBuffer(plaintext)));
  for (let i = 0; i < SHA256_BYTES; i++) {
    if (actualDigest[i] !== expectedDigest[i]) {
      throw new Error('بررسی یکپارچگی SHA-256 فایل پشتیبان ناموفق بود.');
    }
  }

  const parsed = JSON.parse(new TextDecoder().decode(plaintext)) as Partial<BackupEnvelopePayload>;
  if (
    !parsed ||
    parsed.schemaVersion !== 1 ||
    !parsed.state ||
    !Array.isArray(parsed.state.accounts) ||
    !Array.isArray(parsed.state.transactions)
  ) {
    throw new Error('ساختار داده‌های فایل پشتیبان معتبر نیست.');
  }

  return {
    ...INITIAL_STATE,
    ...parsed.state,
  };
}
