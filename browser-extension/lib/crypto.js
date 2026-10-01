// The browser half of EZZY's link crypto. Mirrors app/.../sync/SyncCrypto.kt exactly:
// P-256 ECDH, HKDF-SHA256, AES-256-GCM, HMAC-SHA256, public keys as SPKI DER in base64.

const enc = new TextEncoder();
const dec = new TextDecoder();
const subtle = crypto.subtle;

export const utf8 = (text) => enc.encode(text);
export const fromUtf8 = (bytes) => dec.decode(bytes);

export function b64(bytes) {
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let binary = '';
  for (let i = 0; i < view.length; i += 0x8000) {
    binary += String.fromCharCode.apply(null, view.subarray(i, i + 0x8000));
  }
  return btoa(binary);
}

export function unb64(text) {
  const binary = atob(text);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

export function randomBytes(n) {
  return crypto.getRandomValues(new Uint8Array(n));
}

export function randomHex(n) {
  return [...randomBytes(n)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export function concat(...parts) {
  const total = parts.reduce((sum, p) => sum + p.length, 0);
  const out = new Uint8Array(total);
  let offset = 0;
  for (const p of parts) {
    out.set(p, offset);
    offset += p.length;
  }
  return out;
}

export async function sha256(bytes) {
  return new Uint8Array(await subtle.digest('SHA-256', bytes));
}

export async function hmac(keyBytes, data) {
  const key = await subtle.importKey('raw', keyBytes, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return new Uint8Array(await subtle.sign('HMAC', key, data));
}

export function timingSafeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

export function normalizeCode(code) {
  return String(code).toUpperCase().replace(/[^A-Z0-9]/g, '');
}

/** SHA256("ezzy-pair-v1:" + code) — the same key the phone derives from the code it shows. */
export async function codeKey(code) {
  return sha256(utf8(`ezzy-pair-v1:${normalizeCode(code)}`));
}

export async function newEcdhKeyPair() {
  return subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, true, ['deriveBits']);
}

export async function exportSpki(publicKey) {
  return new Uint8Array(await subtle.exportKey('spki', publicKey));
}

/** ECDH → HKDF-SHA256(salt = codeKey, info = "ezzy-sync-key-v1") → 32-byte session key. */
export async function deriveSessionKey(privateKey, peerSpki, salt) {
  const peer = await subtle.importKey('spki', peerSpki, { name: 'ECDH', namedCurve: 'P-256' }, false, []);
  const shared = new Uint8Array(await subtle.deriveBits({ name: 'ECDH', public: peer }, privateKey, 256));
  const ikm = await subtle.importKey('raw', shared, 'HKDF', false, ['deriveBits']);
  const bits = await subtle.deriveBits(
    { name: 'HKDF', hash: 'SHA-256', salt, info: utf8('ezzy-sync-key-v1') },
    ikm,
    256,
  );
  return new Uint8Array(bits);
}

async function aesKey(raw, usages) {
  return subtle.importKey('raw', raw, { name: 'AES-GCM' }, false, usages);
}

/** @returns {{iv: Uint8Array, ct: Uint8Array}} with ct = ciphertext || tag, like the phone's. */
export async function seal(keyBytes, plain, aad) {
  const iv = randomBytes(12);
  const key = await aesKey(keyBytes, ['encrypt']);
  const params = { name: 'AES-GCM', iv, tagLength: 128 };
  if (aad) params.additionalData = aad;
  const ct = new Uint8Array(await subtle.encrypt(params, key, plain));
  return { iv, ct };
}

export async function open(keyBytes, iv, ct, aad) {
  const key = await aesKey(keyBytes, ['decrypt']);
  const params = { name: 'AES-GCM', iv, tagLength: 128 };
  if (aad) params.additionalData = aad;
  return new Uint8Array(await subtle.decrypt(params, key, ct));
}

/** A deliberately slow PIN hash: every wrong guess against a stolen profile costs real time. */
export const PIN_ITERATIONS = 310000;

export async function pinKey(pin, salt) {
  const base = await subtle.importKey('raw', utf8(pin), 'PBKDF2', false, ['deriveBits']);
  const bits = await subtle.deriveBits(
    { name: 'PBKDF2', hash: 'SHA-256', salt, iterations: PIN_ITERATIONS },
    base,
    256,
  );
  return new Uint8Array(bits);
}

/** Seals a JSON value with a raw key into a compact, storable object. */
export async function sealJson(keyBytes, value, aadText) {
  const { iv, ct } = await seal(keyBytes, utf8(JSON.stringify(value)), aadText ? utf8(aadText) : undefined);
  return { iv: b64(iv), c: b64(ct) };
}

export async function openJson(keyBytes, box, aadText) {
  const plain = await open(keyBytes, unb64(box.iv), unb64(box.c), aadText ? utf8(aadText) : undefined);
  return JSON.parse(fromUtf8(plain));
}
