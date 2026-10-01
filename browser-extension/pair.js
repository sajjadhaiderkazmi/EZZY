// Pairing page: the user types the address and code their phone shows; the phone then asks
// them for a PIN, which arrives here sealed and is used only to lock this browser's copy.

import { icon } from './lib/icons.js';
import { getPrefs, getLink, pair, awaitPin, PhoneOfflineError } from './lib/link.js';
import { normalizeCode } from './lib/crypto.js';

const $ = (id) => document.getElementById(id);

async function applyTheme() {
  const { theme } = await getPrefs();
  const dark = theme === 'dark' || (theme === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
}

function setStep(n) {
  [...$('steps').children].forEach((li, i) => {
    li.classList.toggle('on', i === n);
    li.classList.toggle('done', i < n);
  });
  $('step-enter').classList.toggle('hidden', n !== 0);
  $('step-pin').classList.toggle('hidden', n !== 1);
  $('step-done').classList.toggle('hidden', n !== 2);
}

function showError(text) {
  const el = $('error');
  el.textContent = text;
  el.classList.toggle('hidden', !text);
}

function parseAddress(raw) {
  const text = raw.trim().replace(/^https?:\/\//i, '').replace(/\/.*$/, '');
  const m = /^((?:\d{1,3}\.){3}\d{1,3})(?::(\d{2,5}))?$/.exec(text);
  if (!m) return null;
  if (m[1].split('.').some((part) => Number(part) > 255)) return null;
  return { host: m[1], port: m[2] ? Number(m[2]) : 47821 };
}

async function start() {
  await applyTheme();
  $('brand').innerHTML = icon('bolt', 42);
  $('phone-icon').innerHTML = icon('smartphone', 30);
  $('done-icon').innerHTML = icon('check', 34);

  const previous = await getLink();
  if (previous?.host) $('address').value = `${previous.host}:${previous.port}`;
  ($('address').value ? $('code') : $('address')).focus();

  // Typing "abcdefgh" shows as "ABCD-EFGH", the way the phone displays it.
  $('code').addEventListener('input', () => {
    const clean = normalizeCode($('code').value).slice(0, 8);
    $('code').value = clean.length > 4 ? `${clean.slice(0, 4)}-${clean.slice(4)}` : clean;
    showError('');
  });
  $('address').addEventListener('input', () => showError(''));

  let controller = null;
  $('form').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const address = parseAddress($('address').value);
    const code = normalizeCode($('code').value);
    if (!address) return showError('Type the address exactly as your phone shows it, e.g. 192.168.1.5:47821');
    if (code.length !== 8) return showError('The pairing code has 8 letters and numbers, e.g. ABCD-EFGH');

    const button = $('connect');
    button.disabled = true;
    button.innerHTML = `${icon('sync', 18, 'spin')} Connecting…`;
    showError('');
    try {
      const result = await pair({ ...address, code });
      setStep(1);
      controller = new AbortController();
      let dots = 0;
      await awaitPin(result, {
        signal: controller.signal,
        onWaiting: () => { $('pin-wait').textContent = `Waiting for your phone${'.'.repeat((dots++ % 3) + 1)}`; },
      });
      $('phone-name').textContent = result.link.phoneName || 'your phone';
      chrome.runtime.sendMessage({ type: 'unlocked' }).catch(() => {});
      setStep(2);
      // Connected: close this pairing tab on its own after a moment.
      setTimeout(() => {
        chrome.tabs.getCurrent((tab) => (tab ? chrome.tabs.remove(tab.id) : window.close()));
      }, 1500);
    } catch (err) {
      setStep(0);
      showError(err instanceof PhoneOfflineError
        ? 'Could not reach your phone. Check that both are on the same Wi-Fi, the address is right, and the phone is still showing the code.'
        : err.message || 'Pairing failed. Try again.');
    } finally {
      button.disabled = false;
      button.textContent = 'Connect';
    }
  });

  $('open-vault').addEventListener('click', () => {
    location.href = chrome.runtime.getURL('app.html?tab=1');
  });
}

start();
