// EZZY background worker: locks the vault after the idle time the user picked, keeps the local
// copy fresh while unlocked, and clears copied values off the clipboard.

import { getPrefs, getSessionKey, lockNow, sync, LinkRevokedError } from './lib/link.js';

const AUTOLOCK = 'ezzy-autolock';
const SYNC = 'ezzy-sync';

async function armAutoLock() {
  const { autoLockMinutes } = await getPrefs();
  await chrome.alarms.clear(AUTOLOCK);
  if (autoLockMinutes > 0) chrome.alarms.create(AUTOLOCK, { delayInMinutes: autoLockMinutes });
}

async function ensureSyncAlarm() {
  const existing = await chrome.alarms.get(SYNC);
  if (!existing) chrome.alarms.create(SYNC, { periodInMinutes: 1 });
}

chrome.runtime.onInstalled.addListener(() => {
  ensureSyncAlarm();
});

chrome.runtime.onStartup.addListener(() => {
  ensureSyncAlarm();
});

chrome.alarms.onAlarm.addListener(async (alarm) => {
  if (alarm.name === AUTOLOCK) {
    await lockNow();
    chrome.runtime.sendMessage({ type: 'locked' }).catch(() => {});
  } else if (alarm.name === SYNC) {
    if (!(await getSessionKey())) return;
    try {
      const result = await sync();
      if (result.changed) chrome.runtime.sendMessage({ type: 'synced' }).catch(() => {});
    } catch (err) {
      if (err instanceof LinkRevokedError) chrome.runtime.sendMessage({ type: 'revoked' }).catch(() => {});
    }
  }
});

// ---- Clipboard clearing, done from an offscreen page since a worker has no clipboard -------

let offscreenReady = null;

async function ensureOffscreen() {
  if (offscreenReady) return offscreenReady;
  offscreenReady = (async () => {
    const contexts = await chrome.runtime.getContexts({ contextTypes: ['OFFSCREEN_DOCUMENT'] });
    if (contexts.length === 0) {
      await chrome.offscreen.createDocument({
        url: 'offscreen.html',
        reasons: ['CLIPBOARD'],
        justification: 'Clears a copied password or account number from the clipboard after a few seconds.',
      });
    }
  })();
  try {
    await offscreenReady;
  } catch (err) {
    offscreenReady = null;
    throw err;
  }
  return offscreenReady;
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === 'activity') {
    armAutoLock().then(() => sendResponse({ ok: true }));
    return true;
  }
  if (message?.type === 'unlocked') {
    ensureSyncAlarm();
    armAutoLock().then(() => sendResponse({ ok: true }));
    return true;
  }
  if (message?.type === 'clear-clipboard-later') {
    ensureOffscreen()
      .then(() => chrome.runtime.sendMessage({ type: 'offscreen-clear', seconds: message.seconds, token: message.token }))
      .then(() => sendResponse({ ok: true }))
      .catch(() => sendResponse({ ok: false }));
    return true;
  }
  return false;
});
