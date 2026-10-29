/**
 * The optional vault: opaque, end-to-end-encrypted backups, addressed by an unguessable id.
 *
 * ## What this Worker can see
 *
 * Nothing. The body is the `.mtbackup` envelope the phone produced — AES-256-GCM ciphertext
 * under a key derived from a passphrase this Worker never receives. The id in the path is
 * `SHA-256("muchtoman-vault-id-v1" ‖ vaultKey)`, so it identifies a slot without being a
 * credential that can be reversed into the recovery code.
 *
 * Consequences that are deliberate, not omissions:
 *
 * - **Every stored object is `application/octet-stream` and is never parsed.** The only thing
 *   inspected is the 4-byte `MTBK` magic, which exists to catch a client that forgot to encrypt
 *   before it uploaded — a bug, not a format. The Worker has no schema, no JSON parser on the
 *   hot path, and no field it could log.
 * - **No request or response body is ever logged.** There is no logging of the id either: an
 *   access log URL is the whole of what an operator sees, and even that is a hash.
 * - **No auth header.** The id is the capability. Whoever holds the id can fetch a blob — which
 *   is useless without the passphrase — and can overwrite or delete it. That is a real risk and
 *   is stated in the app's threat-model text: this is an availability risk, not a
 *   confidentiality one, and the recovery code must be treated as a house key.
 *
 * ## Wiring (the Lead owns `index.ts`; this module does not edit it)
 *
 * ```ts
 * import { routeVault } from './vault';
 *
 * // …inside the default fetch handler, before the /v1/ block:
 * if (path.startsWith('/v1/vault')) {
 *   return await routeVault(request, env);
 * }
 * ```
 *
 * `routeVault` accepts the real `Env` because both bindings are optional; with neither bound it
 * answers 503 `vault_not_configured` instead of throwing.
 *
 * ## Bindings (report only — `wrangler.jsonc` is outside this file's write scope)
 *
 * KV is the default: native TTL, 25 MiB values, no lifecycle rule to remember.
 *
 * ```jsonc
 * // wrangler.jsonc
 * "kv_namespaces": [{ "binding": "VAULT_KV", "id": "<kv-namespace-id>" }]
 * ```
 *
 * ```sh
 * npx wrangler kv namespace create VAULT_KV
 * ```
 *
 * R2 is supported for larger or longer-lived blobs; expiry is enforced by this module from
 * object metadata, and the lifecycle rule is belt-and-braces:
 *
 * ```jsonc
 * "r2_buckets": [{ "binding": "VAULT", "bucket_name": "muchtoman-vault" }]
 * ```
 *
 * ```sh
 * npx wrangler r2 bucket create muchtoman-vault
 * # optional: expire objects at the storage layer too
 * npx wrangler r2 bucket lifecycle add muchtoman-vault --expire-days 90
 * ```
 */

export const MAX_VAULT_BYTES = 24 * 1024 * 1024;
export const VAULT_TTL_DAYS = 90;
export const VAULT_TTL_SECONDS = VAULT_TTL_DAYS * 24 * 60 * 60;
/** KV's minimum `expirationTtl`, and the floor R2's metadata check uses. */
export const MIN_TTL_SECONDS = 60;

/** A recovery-code id: the lowercase hex SHA-256. Nothing else names a vault slot. */
export const VAULT_ID = /^[a-f0-9]{64}$/;

const ENVELOPE_MAGIC = [0x4d, 0x54, 0x42, 0x4b]; // "MTBK"

/** One stored blob, behind the storage the deployment actually has. */
export interface VaultStore {
  put(id: string, value: ArrayBuffer): Promise<void>;
  get(id: string): Promise<ArrayBuffer | null>;
  delete(id: string): Promise<void>;
}

class BodyTooLargeError extends Error {}

class InvalidBlobError extends Error {}

function jsonResponse(payload: unknown, status: number): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      'x-content-type-options': 'nosniff',
    },
  });
}

function emptyResponse(status: number, allow?: string): Response {
  const headers: Record<string, string> = {
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
  };
  if (allow) headers.allow = allow;
  return new Response(null, { status, headers });
}

function envelopeResponse(body: ArrayBuffer): Response {
  return new Response(body, {
    status: 200,
    headers: {
      'content-type': 'application/octet-stream',
      'content-length': String(body.byteLength),
      'cache-control': 'no-store',
      'x-content-type-options': 'nosniff',
      // The URL is the capability. A shared cache must never keep it.
      'referrer-policy': 'no-referrer',
    },
  });
}

/**
 * The 4-byte magic check. Not a parse: the rest of the envelope is never looked at, so the
 * Worker stays unable to make a statement about what is inside.
 */
function isEnvelope(bytes: Uint8Array): boolean {
  if (bytes.byteLength < 5) return false;
  for (let i = 0; i < ENVELOPE_MAGIC.length; i++) {
    if (bytes[i] !== ENVELOPE_MAGIC[i]) return false;
  }
  return true;
}

async function readBodyLimited(request: Request, maxBytes: number): Promise<Uint8Array> {
  const declared = request.headers.get('content-length');
  if (declared && Number(declared) > maxBytes) throw new BodyTooLargeError();
  const body = request.body;
  if (!body) return new Uint8Array(0);

  const reader = body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > maxBytes) throw new BodyTooLargeError();
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }

  const joined = new Uint8Array(total);
  let at = 0;
  for (const chunk of chunks) {
    joined.set(chunk, at);
    at += chunk.byteLength;
  }
  return joined;
}

/** A copy that owns its ArrayBuffer, so callers cannot observe a larger pooled buffer. */
function ownedBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

/**
 * Handle `PUT|GET|DELETE /v1/vault/<id>`.
 *
 * `store` is null when the deployment has no binding; the answer is a stable 503 rather than a
 * crash, because "the vault was never configured" is a thing the app can explain.
 */
export async function handleVault(
  request: Request,
  store: VaultStore | null,
): Promise<Response> {
  if (!store) return jsonResponse({ code: 'vault_not_configured' }, 503);

  const url = new URL(request.url);
  const id = url.pathname.slice(url.pathname.lastIndexOf('/') + 1);
  if (!VAULT_ID.test(id)) return jsonResponse({ code: 'invalid_id' }, 404);

  try {
    switch (request.method) {
      case 'PUT': {
        const bytes = await readBodyLimited(request, MAX_VAULT_BYTES);
        if (bytes.byteLength === 0) throw new InvalidBlobError();
        if (!isEnvelope(bytes)) throw new InvalidBlobError();
        await store.put(id, ownedBuffer(bytes));
        return emptyResponse(204);
      }
      case 'GET': {
        const blob = await store.get(id);
        if (!blob || blob.byteLength === 0) return jsonResponse({ code: 'not_found' }, 404);
        return envelopeResponse(blob);
      }
      case 'DELETE': {
        await store.delete(id);
        return emptyResponse(204);
      }
      default:
        return emptyResponse(405, 'GET, PUT, DELETE');
    }
  } catch (error) {
    if (error instanceof BodyTooLargeError) return jsonResponse({ code: 'too_large' }, 413);
    if (error instanceof InvalidBlobError) return jsonResponse({ code: 'invalid_blob' }, 400);
    // Deliberately no request data, no id, no body — an operator gets one line and a status.
    console.error(JSON.stringify({ event: 'vault_failure' }));
    return jsonResponse({ code: 'internal' }, 500);
  }
}

/**
 * KV: native TTL. Recommended default — no lifecycle rule to forget, and 25 MiB per value is
 * comfortably above the phone's 24 MiB cap.
 */
export function kvVaultStore(
  kv: KVNamespace,
  ttlSeconds: number = VAULT_TTL_SECONDS,
): VaultStore {
  const ttl = Math.max(MIN_TTL_SECONDS, Math.floor(ttlSeconds));
  return {
    async put(id, value) {
      await kv.put(id, value, { expirationTtl: ttl });
    },
    async get(id) {
      return kv.get(id, 'arrayBuffer');
    },
    async delete(id) {
      await kv.delete(id);
    },
  };
}

/**
 * R2: no native TTL, so expiry lives in object metadata and is checked on every read. The
 * lifecycle rule in the header comment is the backstop for objects nobody reads again.
 */
export function r2VaultStore(bucket: R2Bucket, ttlSeconds: number = VAULT_TTL_SECONDS): VaultStore {
  const ttl = Math.max(MIN_TTL_SECONDS, Math.floor(ttlSeconds));

  function expiresAt(): string {
    return String(Date.now() + ttl * 1000);
  }

  return {
    async put(id, value) {
      await bucket.put(id, value, { customMetadata: { expiresAt: expiresAt() } });
    },
    async get(id) {
      const object = await bucket.get(id);
      if (!object) return null;
      const deadline = Number(object.customMetadata?.expiresAt ?? '0');
      if (!Number.isFinite(deadline) || deadline <= Date.now()) {
        await bucket.delete(id);
        return null;
      }
      return object.arrayBuffer();
    },
    async delete(id) {
      await bucket.delete(id);
    },
  };
}

/** The bindings this module needs, both optional. */
export interface VaultEnv {
  VAULT?: R2Bucket;
  VAULT_KV?: KVNamespace;
}

/**
 * One-line routing helper for `index.ts`. R2 wins when both are bound: it is the only one that
 * can hold a backup larger than KV's 25 MiB ceiling.
 */
export function routeVault(request: Request, env: VaultEnv): Promise<Response> {
  const store = env.VAULT
    ? r2VaultStore(env.VAULT)
    : env.VAULT_KV
      ? kvVaultStore(env.VAULT_KV)
      : null;
  return handleVault(request, store);
}
