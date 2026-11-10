import { createPrivateKey, createPublicKey, generateKeyPairSync, sign, verify } from 'node:crypto';

/**
 * Signs exact UTF-8 payload bytes using ECDSA P-256 (SHA-256) in IEEE-P1363 (64-byte r||s) format,
 * returning a base64-encoded signature string suitable for WebCrypto and Android verification.
 */
export function signRatesBytes(payloadBytes: Uint8Array | Buffer, privateKeyPem: string): string {
  const privKey = createPrivateKey(privateKeyPem.trim());
  const sig = sign('sha256', Buffer.from(payloadBytes), {
    key: privKey,
    dsaEncoding: 'ieee-p1363',
  });
  if (sig.length !== 64) {
    throw new Error(`Expected 64-byte IEEE-P1363 P-256 signature, got ${sig.length} bytes`);
  }
  return sig.toString('base64');
}

/**
 * Verifies a base64-encoded IEEE-P1363 (64-byte r||s) ECDSA P-256 signature against exact UTF-8 bytes.
 */
export function verifyRatesBytes(
  payloadBytes: Uint8Array | Buffer,
  signatureBase64: string,
  publicKeyPem: string,
): boolean {
  try {
    const cleanB64 = signatureBase64.trim();
    if (!cleanB64) return false;
    const sigBuf = Buffer.from(cleanB64, 'base64');
    if (sigBuf.length !== 64) return false;

    const pubKey = createPublicKey(publicKeyPem.trim());
    return verify(
      'sha256',
      Buffer.from(payloadBytes),
      {
        key: pubKey,
        dsaEncoding: 'ieee-p1363',
      },
      sigBuf,
    );
  } catch {
    return false;
  }
}

/**
 * Converts a 64-byte IEEE-P1363 (r||s) ECDSA signature into ASN.1 DER format
 * (mirrors Android's P1363-to-DER converter for cross-testing).
 */
export function p1363ToDer(p1363: Uint8Array): Buffer {
  if (p1363.length !== 64) {
    throw new Error(`Invalid P1363 length: ${p1363.length}`);
  }
  const encodeInt = (slice: Uint8Array): Buffer => {
    let i = 0;
    while (i < slice.length - 1 && slice[i] === 0) {
      i++;
    }
    const trimmed = slice.subarray(i);
    if ((trimmed[0] & 0x80) !== 0) {
      return Buffer.concat([Buffer.from([0x02, trimmed.length + 1, 0x00]), Buffer.from(trimmed)]);
    }
    return Buffer.concat([Buffer.from([0x02, trimmed.length]), Buffer.from(trimmed)]);
  };

  const rDer = encodeInt(p1363.subarray(0, 32));
  const sDer = encodeInt(p1363.subarray(32, 64));
  const seqLen = rDer.length + sDer.length;
  return Buffer.concat([Buffer.from([0x30, seqLen]), rDer, sDer]);
}

/**
 * Generates an ephemeral ECDSA P-256 keypair in PEM format (for tests).
 */
export function generateTestKeyPair(): { privateKeyPem: string; publicKeyPem: string } {
  const { privateKey, publicKey } = generateKeyPairSync('ec', {
    namedCurve: 'prime256v1',
  });
  return {
    privateKeyPem: privateKey.export({ type: 'pkcs8', format: 'pem' }).toString(),
    publicKeyPem: publicKey.export({ type: 'spki', format: 'pem' }).toString(),
  };
}

