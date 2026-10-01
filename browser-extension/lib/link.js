// Everything about the link to the phone: pairing, the PIN-wrapped session key, the sealed
// local copy of the vault, and the encrypted calls. Shared by the popup, the pairing page and
// the background worker.

import {
  b64, unb64, utf8, fromUtf8, randomBytes, randomHex, concat, hmac, timingSafeEqual,
  codeKey, newEcdhKeyPair, exportSpki, deriveSessionKey, seal, open, pinKey, sealJson, openJson,
} from './crypto.js';

export const PORTS = [47821, 47822, 47823, 47824, 47825];
const MAX_WRONG_PINS = 10;

export class LinkRevokedError extends Error {
  constructor() { super('This browser was logged out from the phone'); this.name = 'LinkRevokedError'; }
}
export class PhoneOfflineError extends Error {
  constructor() { super('Phone not reachable'); this.name = 'PhoneOfflineError'; }
}

// ---- Storage --------------------------------------------------------------------------------

const local = chrome.storage.local;
const session = chrome.storage.session;

export async function getLink() {
  return (await local.get('link')).link || null;
}

async function setLink(link) {
  await local.set({ link });
}

export async function getPrefs() {
  return { autoLockMinutes: 5, theme: 'system', ...((await local.get('prefs')).prefs || {}) };
}

export async function setPrefs(patch) {
  const prefs = { ...(await getPrefs()), ...patch };
  await local.set({ prefs });
  return prefs;
}

/** The unwrapped session key, present only while EZZY is unlocked in this browser session. */
export async function getSessionKey() {
  const s = (await session.get('session')).session;
  return s ? unb64(s.key) : null;
}

export async function getSessionState() {
  return (await session.get('session')).session || null;
}

export async function setUnlockedSections(ids) {
  const s = await getSessionState();
  if (!s) return;
  await session.set({ session: { ...s, sections: ids } });
}

export async function lockNow() {
  await session.remove('session');
}

/** Wipes every trace of the link from this browser. */
export async function wipeAll() {
  await session.clear();
  await local.remove(['link', 'cache', 'meta', 'security']);
}

// ---- PIN -------------------------------------------------------------------------------------

async function wrapKey(sessionKey, pin) {
  const salt = randomBytes(16);
  const k = await pinKey(pin, salt);
  const { iv, ct } = await seal(k, sessionKey, utf8('ezzy-wrap-v1'));
  return { salt: b64(salt), iv: b64(iv), c: b64(ct) };
}

export async function securityState() {
  return { failed: 0, lockedUntil: 0, ...((await local.get('security')).security || {}) };
}

/**
 * Opens the vault with the PIN. Wrong PINs back off, and enough of them wipe the browser's
 * copy entirely — the phone still has everything, so the worst case is pairing again.
 */
export async function unlock(pin) {
  const link = await getLink();
  if (!link) throw new Error('Not paired');
  const sec = await securityState();
  if (Date.now() < sec.lockedUntil) {
    const wait = Math.ceil((sec.lockedUntil - Date.now()) / 1000);
    return { ok: false, wait };
  }
  try {
    const k = await pinKey(pin, unb64(link.wrapped.salt));
    const key = await open(k, unb64(link.wrapped.iv), unb64(link.wrapped.c), utf8('ezzy-wrap-v1'));
    await local.set({ security: { failed: 0, lockedUntil: 0 } });
    await session.set({ session: { key: b64(key), at: Date.now(), sections: [] } });
    return { ok: true };
  } catch {
    const failed = sec.failed + 1;
    if (failed >= MAX_WRONG_PINS) {
      await wipeAll();
      return { ok: false, wiped: true };
    }
    const lockedUntil = failed >= 5 ? Date.now() + 30_000 * (failed - 4) : 0;
    await local.set({ security: { failed, lockedUntil } });
    return { ok: false, left: MAX_WRONG_PINS - failed, wait: lockedUntil ? Math.ceil((lockedUntil - Date.now()) / 1000) : 0 };
  }
}

/** Checks a PIN without changing the session — used to open a locked section. */
export async function verifyPin(pin) {
  const link = await getLink();
  if (!link) return false;
  try {
    const k = await pinKey(pin, unb64(link.wrapped.salt));
    await open(k, unb64(link.wrapped.iv), unb64(link.wrapped.c), utf8('ezzy-wrap-v1'));
    return true;
  } catch {
    return false;
  }
}

// ---- Network ---------------------------------------------------------------------------------

async function fetchWithTimeout(url, options = {}, timeout = 8000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    return await fetch(url, { ...options, signal: controller.signal, cache: 'no-store' });
  } finally {
    clearTimeout(timer);
  }
}

async function ping(host, port, timeout = 1500) {
  try {
    const res = await fetchWithTimeout(`http://${host}:${port}/ping`, {}, timeout);
    if (!res.ok) return null;
    const body = await res.json();
    return body && body.app === 'ezzy' ? body : null;
  } catch {
    return null;
  }
}

/**
 * Finds the phone again after its Wi-Fi address changed: the known ports on the old address
 * first, then every address on the same /24 network, in parallel.
 */
export async function rediscover(link) {
  for (const port of [link.port, ...PORTS.filter((p) => p !== link.port)]) {
    const hit = await ping(link.host, port, 1200);
    if (hit && hit.phoneId === link.phoneId) return { host: link.host, port };
  }
  const parts = String(link.host).split('.');
  if (parts.length !== 4) return null;
  const prefix = parts.slice(0, 3).join('.');
  const hosts = [];
  for (let i = 1; i < 255; i++) hosts.push(`${prefix}.${i}`);
  let found = null;
  const queue = [...hosts];
  const worker = async () => {
    while (queue.length && !found) {
      const host = queue.shift();
      const hit = await ping(host, link.port, 1200);
      if (hit && hit.phoneId === link.phoneId) found = { host, port: link.port };
    }
  };
  await Promise.all(Array.from({ length: 48 }, worker));
  return found;
}

async function rawCall(link, key, op, args, timeout) {
  const request = { ts: Date.now(), rid: randomHex(12), op, a: args ?? null };
  const { iv, ct } = await seal(key, utf8(JSON.stringify(request)), utf8(`ezzy-req:${link.deviceId}`));
  let res;
  try {
    // text/plain keeps this a "simple" request, so Chrome sends it without a CORS preflight.
    res = await fetchWithTimeout(
      `http://${link.host}:${link.port}/api`,
      { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: JSON.stringify({ d: link.deviceId, iv: b64(iv), c: b64(ct) }) },
      timeout,
    );
  } catch {
    throw new PhoneOfflineError();
  }
  const body = await res.json().catch(() => ({}));
  // Only the phone saying it no longer knows this browser wipes it. A message that failed to open
  // (bad_seal) could be someone on the Wi-Fi tampering with traffic, and must not be enough.
  if (res.status === 401 && body.error === 'unknown_device') throw new LinkRevokedError();
  if (!res.ok) throw new Error(body.error || `HTTP ${res.status}`);
  const plain = await open(key, unb64(body.iv), unb64(body.c), utf8(`ezzy-res:${link.deviceId}`));
  const response = JSON.parse(fromUtf8(plain));
  if (response.rid !== request.rid) throw new Error('Mismatched reply');
  if (!response.ok) throw new Error(response.err || 'Request failed');
  return response.r;
}

/** One sealed call to the phone, finding it again if its address moved. */
export async function call(op, args, { timeout = 8000, key: keyOverride } = {}) {
  let link = await getLink();
  if (!link) throw new Error('Not paired');
  const key = keyOverride || (await getSessionKey());
  if (!key) throw new Error('Locked');
  try {
    return await rawCall(link, key, op, args, timeout);
  } catch (err) {
    if (err instanceof LinkRevokedError) {
      await wipeAll();
      throw err;
    }
    if (!(err instanceof PhoneOfflineError)) throw err;
    const moved = await rediscover(link);
    if (!moved || (moved.host === link.host && moved.port === link.port)) throw err;
    link = { ...link, host: moved.host, port: moved.port };
    await setLink(link);
    return rawCall(link, key, op, args, timeout);
  }
}

// ---- The local copy --------------------------------------------------------------------------

export async function readCache() {
  const key = await getSessionKey();
  const box = (await local.get('cache')).cache;
  if (!key || !box) return null;
  try {
    return await openJson(key, box, 'ezzy-cache-v1');
  } catch {
    return null;
  }
}

async function writeCache(snapshot, key) {
  const value = { snapshot, syncedAt: Date.now() };
  await local.set({ cache: await sealJson(key, value, 'ezzy-cache-v1') });
  return value;
}

/** Asks the phone whether anything moved, and pulls the vault only when it has. */
export async function sync({ force = false } = {}) {
  const key = await getSessionKey();
  if (!key) throw new Error('Locked');
  const cached = await readCache();
  const status = await call('status');
  if (!force && cached && cached.snapshot.revision === status.revision) {
    const value = { ...cached, syncedAt: Date.now() };
    await local.set({ cache: await sealJson(key, value, 'ezzy-cache-v1') });
    return { ...value, changed: false };
  }
  const snapshot = await call('snapshot', null, { timeout: 20000 });
  const value = await writeCache(snapshot, key);
  return { ...value, changed: true };
}

/** Recently opened entries, kept on this browser only and sealed like the cache. */
export async function getRecent() {
  const key = await getSessionKey();
  const box = (await local.get('meta')).meta;
  if (!key || !box) return [];
  try {
    return (await openJson(key, box, 'ezzy-meta-v1')).recent || [];
  } catch {
    return [];
  }
}

export async function pushRecent(id) {
  const key = await getSessionKey();
  if (!key) return;
  const recent = [id, ...(await getRecent()).filter((x) => x !== id)].slice(0, 8);
  await local.set({ meta: await sealJson(key, { recent }, 'ezzy-meta-v1') });
}

// ---- Pairing ---------------------------------------------------------------------------------

function browserName() {
  const ua = navigator.userAgent;
  const os = /Windows/.test(ua) ? 'Windows' : /Mac OS X/.test(ua) ? 'Mac' : /CrOS/.test(ua) ? 'ChromeOS' : /Linux/.test(ua) ? 'Linux' : 'computer';
  const browser = /Edg\//.test(ua) ? 'Edge' : /Brave/.test(ua) ? 'Brave' : 'Chrome';
  return `${browser} on ${os}`;
}

/** Step one: the ECDH handshake, proved on both sides by the code shown on the phone. */
export async function pair({ host, port, code }) {
  const ck = await codeKey(code);
  const pair = await newEcdhKeyPair();
  const pub = await exportSpki(pair.publicKey);
  let res;
  try {
    res = await fetchWithTimeout(
      `http://${host}:${port}/pair`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain' },
        body: JSON.stringify({ v: 1, pub: b64(pub), name: browserName(), mac: b64(await hmac(ck, pub)) }),
      },
      10000,
    );
  } catch {
    throw new PhoneOfflineError();
  }
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const messages = {
      wrong_code: 'That code does not match the one on your phone.',
      code_expired: 'That code has expired. Tap "New code" on your phone.',
      not_pairing: 'Your phone is not showing a pairing code right now. Open Connect Browser and tap Proceed.',
      version_mismatch: 'Update EZZY on your phone and this extension to the same version.',
    };
    throw new Error(messages[body.error] || 'Pairing failed. Try again.');
  }
  const phonePub = unb64(body.pub);
  const expected = await hmac(ck, concat(phonePub, pub));
  if (!timingSafeEqual(expected, unb64(body.mac))) throw new Error('The phone could not prove it showed that code.');
  const key = await deriveSessionKey(pair.privateKey, phonePub, ck);
  return {
    key,
    link: {
      host, port,
      deviceId: body.deviceId,
      phoneId: body.phoneId,
      phoneName: body.phoneName,
      pairedAt: Date.now(),
    },
  };
}

/**
 * Step two: wait for the PIN the user is choosing on the phone. It arrives once, sealed with the
 * new session key, and is used here only to wrap that key — it is never stored.
 */
export async function awaitPin({ key, link }, { onWaiting, signal } = {}) {
  while (!signal?.aborted) {
    let status;
    try {
      status = await rawCall(link, key, 'status', null, 8000);
    } catch (err) {
      if (err instanceof LinkRevokedError) throw new Error('Pairing was cancelled on the phone.');
      status = null;
    }
    if (status && status.pin) {
      const wrapped = await wrapKey(key, status.pin);
      await local.remove(['cache', 'meta']);
      await local.set({ link: { ...link, wrapped }, security: { failed: 0, lockedUntil: 0 } });
      await session.set({ session: { key: b64(key), at: Date.now(), sections: [] } });
      return true;
    }
    onWaiting?.();
    await new Promise((r) => setTimeout(r, 1500));
  }
  return false;
}

/** Logs this browser out: tells the phone (if it can) and wipes everything local. */
export async function logout() {
  try {
    await call('logout', null, { timeout: 4000 });
  } catch {
    // Offline phones get told next time — the phone refuses this browser once it is unpaired there.
  }
  await wipeAll();
}
