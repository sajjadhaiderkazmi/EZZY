// EZZY for Chrome. One small single-page app shown both as the toolbar popup and in a full tab.

import { icon } from './lib/icons.js';
import {
  getLink, getPrefs, setPrefs, getSessionKey, getSessionState, setUnlockedSections, unlock, verifyPin,
  lockNow, readCache, sync, call, getRecent, pushRecent, logout, securityState,
  LinkRevokedError, PhoneOfflineError,
} from './lib/link.js';

const isTab = new URLSearchParams(location.search).has('tab');
document.body.classList.add(isTab ? 'tab' : 'popup');
const root = document.getElementById('app');

const FIELD_TYPES = [
  ['TEXT', 'Text'], ['NUMBER', 'Number'], ['SECRET', 'Secret'], ['PHONE', 'Phone'],
  ['EMAIL', 'Email'], ['URL', 'Website'], ['DATE', 'Date'], ['MULTILINE', 'Long text'],
];
const QUICK_FIELDS = [
  ['PHONE', 'Phone', 'phone'], ['EMAIL', 'Email', 'email'], ['NUMBER', 'Number', 'qr'],
  ['SECRET', 'PIN', 'lock'], ['DATE', 'Date', 'document'], ['URL', 'Website', 'web'], ['MULTILINE', 'Note', 'note'],
];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const S = {
  view: 'loading',
  params: {},
  stack: [],
  link: null,
  prefs: null,
  snapshot: null,
  syncedAt: 0,
  online: null,
  recent: [],
  revealed: new Set(),
  unlockedSections: new Set(),
  query: '',
  editor: null,
  pin: '',
  pinMessage: '',
  busy: false,
  menuOpen: false,
  fileUrls: new Map(),
  flash: '',
};

// ---- Utilities --------------------------------------------------------------------------------

const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function toast(text, iconName = 'check') {
  const el = document.getElementById('toast');
  el.innerHTML = `${icon(iconName, 16)}<span>${esc(text)}</span>`;
  el.classList.add('show');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => el.classList.remove('show'), 2200);
}

function timeLabel(ms) {
  if (!ms) return 'never';
  const d = new Date(ms);
  const sameDay = d.toDateString() === new Date().toDateString();
  const t = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  return sameDay ? t : `${d.toLocaleDateString([], { day: 'numeric', month: 'short' })}, ${t}`;
}

function applyTheme(theme) {
  const dark = theme === 'dark' || (theme === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
}

let lastActivity = 0;
function noteActivity() {
  const now = Date.now();
  if (now - lastActivity < 15000) return;
  lastActivity = now;
  chrome.runtime.sendMessage({ type: 'activity' }).catch(() => {});
}

const snap = () => S.snapshot || { categories: [], groups: [], templates: [], items: [] };
const category = (id) => snap().categories.find((c) => c.id === id);
const template = (id) => snap().templates.find((t) => t.id === id);
const item = (id) => snap().items.find((i) => i.id === id);
const sortedCategories = () => [...snap().categories].sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
const isLockedSection = (id) => !!category(id)?.locked && !S.unlockedSections.has(id);

function subtitleOf(it) {
  const url = it.fields.find((f) => f.type === 'URL' && f.value.trim());
  if (url) return url.value;
  const masked = it.fields.find((f) => f.type === 'SECRET' && f.value.trim() && !/password/i.test(f.label));
  if (masked) return `•••• ${masked.value.slice(-4)}`;
  return it.subtitle || it.fields.find((f) => f.type !== 'SECRET' && f.value.trim())?.value || '';
}

function itemIconKey(it) {
  return template(it.templateId)?.iconKey || category(it.categoryId)?.iconKey || 'folder';
}

function toInputDate(stored) {
  const m = /^(\d{1,2}) ([A-Za-z]{3}) (\d{4})$/.exec(String(stored || '').trim());
  if (!m) return '';
  const month = MONTHS.findIndex((x) => x.toLowerCase() === m[2].toLowerCase());
  if (month < 0) return '';
  return `${m[3]}-${String(month + 1).padStart(2, '0')}-${m[1].padStart(2, '0')}`;
}

function fromInputDate(value) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || '');
  if (!m) return '';
  return `${m[3]} ${MONTHS[Number(m[2]) - 1]} ${m[1]}`;
}

// ---- Navigation -------------------------------------------------------------------------------

function go(view, params = {}) {
  S.stack.push({ view: S.view, params: S.params });
  S.view = view;
  S.params = params;
  S.menuOpen = false;
  render();
}

function back() {
  const prev = S.stack.pop();
  S.menuOpen = false;
  if (!prev || prev.view === 'loading' || prev.view === 'locked') {
    S.view = 'home';
    S.params = {};
  } else {
    S.view = prev.view;
    S.params = prev.params;
  }
  render();
}

// ---- Sync ------------------------------------------------------------------------------------

async function loadCache() {
  const cached = await readCache();
  if (cached) {
    S.snapshot = cached.snapshot;
    S.syncedAt = cached.syncedAt;
  }
  S.recent = await getRecent();
  const session = await getSessionState();
  S.unlockedSections = new Set(session?.sections || []);
}

async function refresh({ force = false, quiet = true } = {}) {
  if (S.busy && !force) return;
  try {
    const result = await sync({ force });
    S.online = true;
    S.snapshot = result.snapshot;
    S.syncedAt = result.syncedAt;
    if (result.changed || !quiet) render();
    else updateStatusOnly();
    if (!quiet) toast('Synced with your phone', 'sync');
  } catch (err) {
    if (err instanceof LinkRevokedError) {
      S.link = null;
      S.flash = 'This browser was logged out from your phone. Pair again to continue.';
      S.view = 'welcome';
      render();
      return;
    }
    if (err?.message === 'Locked') {
      S.view = 'locked';
      render();
      return;
    }
    const wasOnline = S.online;
    S.online = false;
    if (wasOnline !== false || !quiet) render();
    if (!quiet) toast('Phone not reachable', 'wifi_off');
  }
}

function updateStatusOnly() {
  const el = document.getElementById('status-line');
  if (el) el.innerHTML = statusLine();
}

function statusLine() {
  if (S.online === false) return `<span class="status-dot"></span> Offline · last synced ${esc(timeLabel(S.syncedAt))}`;
  if (S.online === null) return `<span class="status-dot"></span> Connecting…`;
  return `<span class="status-dot on"></span> Synced ${esc(timeLabel(S.syncedAt))}`;
}

let poller = null;
function startPolling() {
  clearInterval(poller);
  poller = setInterval(() => {
    if (['home', 'section', 'item', 'settings', 'search'].includes(S.view)) refresh();
  }, 4000);
}

// ---- Copy ------------------------------------------------------------------------------------

async function copyText(text, label = 'Copied') {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    const ta = document.createElement('textarea');
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    ta.remove();
  }
  const seconds = snap().clipboardClearSeconds || 45;
  if (seconds > 0) chrome.runtime.sendMessage({ type: 'clear-clipboard-later', seconds }).catch(() => {});
  toast(seconds > 0 ? `${label} · clears in ${seconds}s` : label, 'copy');
}

function allDetailsText(it) {
  const lines = [it.title];
  for (const f of it.fields) if (f.value.trim()) lines.push(`${f.label}: ${f.value}`);
  if (it.note.trim()) lines.push('', it.note);
  return lines.join('\n');
}

// ---- Rendering ---------------------------------------------------------------------------------

function render() {
  const views = {
    loading: viewLoading, welcome: viewWelcome, locked: viewLocked, home: viewHome, section: viewSection,
    item: viewItem, edit: viewEditor, settings: viewSettings, search: viewSearch,
  };
  root.innerHTML = (views[S.view] || viewHome)();
  afterRender();
}

function viewLoading() {
  return `<div class="welcome"><div class="brand big">${icon('bolt', 40)}</div></div>`;
}

function viewWelcome() {
  return `
    <div class="welcome">
      <div class="brand big">${icon('bolt', 42)}</div>
      <h2>Connect your phone</h2>
      <p>Open your EZZY vault right here in Chrome — synced over your own Wi-Fi and locked with your PIN.</p>
      ${S.flash ? `<div class="banner warn" style="margin:10px 0 0">${icon('logout', 16)}${esc(S.flash)}</div>` : ''}
      <div class="steps">
        <div><b>1</b><span>On your phone, open EZZY › Settings › <strong>Connect Browser</strong>.</span></div>
        <div><b>2</b><span>Tap <strong>Proceed</strong>, then <strong>Confirm</strong>. Your phone shows an address and a code.</span></div>
        <div><b>3</b><span>Click below and type them in. Then pick a PIN on your phone.</span></div>
      </div>
      <button class="btn primary block" data-action="pair">${icon('smartphone', 20)} Connect phone</button>
    </div>`;
}

function viewLocked() {
  const dots = Array.from({ length: 6 }, (_, i) => `<span class="dot ${i < S.pin.length ? 'on' : ''}"></span>`).join('');
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((k) => `<button class="key" data-key="${k}">${k}</button>`).join('');
  return `
    <div class="lock">
      <div class="brand big">${icon('lock', 34)}</div>
      <h2>EZZY is locked</h2>
      <p>Enter the 6-digit PIN you set on your phone</p>
      <div class="dots" id="dots">${dots}</div>
      <div class="msg" id="pin-msg">${esc(S.pinMessage)}</div>
      <div class="keypad">
        ${keys}
        <span></span>
        <button class="key" data-key="0">0</button>
        <button class="key ghost" data-key="del" aria-label="Delete">${icon('backspace', 24)}</button>
      </div>
    </div>`;
}

function topbar({ title, sub = '', backBtn = true, actions = '', live = false }) {
  return `
    <div class="topbar">
      ${backBtn ? `<button class="circle-btn" data-action="back" aria-label="Back">${icon('back', 20)}</button>` : `<div class="brand">${icon('bolt', 22)}</div>`}
      <div class="title"><h1>${esc(title)}</h1>${sub ? `<div class="sub"${live ? ' id="status-line"' : ''}>${sub}</div>` : ''}</div>
      ${actions}
    </div>
    ${S.online === false ? `<div class="banner">${icon('wifi_off', 16)} Phone not reachable. Showing your last synced copy; changes need the phone.</div>` : ''}`;
}

function rowHtml(it) {
  const files = it.attachments.length ? `<span class="chip">${icon('attach', 13)}${it.attachments.length}</span>` : '';
  const pinned = it.isPinned ? `<span class="muted">${icon('pin', 15)}</span>` : '';
  return `
    <button class="row" data-open-item="${esc(it.id)}">
      <span class="disc">${icon(itemIconKey(it), 20)}</span>
      <span class="main"><div class="t">${esc(it.title)}</div><div class="s">${esc(subtitleOf(it)) || '&nbsp;'}</div></span>
      ${pinned}${files}
      <span class="chev">${icon('chevron', 20)}</span>
    </button>`;
}

function viewHome() {
  const s = snap();
  const cats = sortedCategories();
  const counts = new Map();
  for (const it of s.items) counts.set(it.categoryId, (counts.get(it.categoryId) || 0) + 1);
  const pinned = s.items.filter((it) => it.isPinned && !isLockedSection(it.categoryId)).sort((a, b) => a.title.localeCompare(b.title));
  const recent = S.recent.map(item).filter((it) => it && !isLockedSection(it.categoryId));
  const greeting = s.ownerName ? `Hi, ${s.ownerName}` : 'My Vault';

  const tiles = cats.map((c) => `
    <button class="tile" data-open-section="${esc(c.id)}">
      ${c.locked ? `<span class="lock">${icon(S.unlockedSections.has(c.id) ? 'lock_open' : 'lock', 16)}</span>` : ''}
      <span class="disc">${icon(c.iconKey, 20)}</span>
      <span><div class="name">${esc(c.name)}</div><div class="count">${counts.get(c.id) || 0} ${counts.get(c.id) === 1 ? 'entry' : 'entries'}</div></span>
    </button>`).join('');

  return `
    <div class="page">
      ${topbar({
        title: greeting, sub: statusLine(), backBtn: false, live: true,
        actions: `
          <button class="circle-btn" data-action="settings" aria-label="Settings">${icon('settings', 20)}</button>
          <button class="circle-btn green" data-action="add" aria-label="Add entry">${icon('add', 22)}</button>`,
      })}
      <div class="scroll">
        <label class="search">${icon('search', 20)}<input id="search" placeholder="Search your vault" value="${esc(S.query)}" autocomplete="off" /></label>
        <div class="hero">
          <div>
            <div class="badge">${icon('shield', 14)} End-to-end encrypted</div>
            <div class="count" style="margin-top:10px">${s.items.length}</div>
            <div class="label">${s.items.length === 1 ? 'entry' : 'entries'} safe</div>
            <div class="meta">${cats.length} sections · from ${esc(s.phoneName || 'your phone')}</div>
          </div>
          <button class="circle-btn" data-action="lock" aria-label="Lock now" title="Lock now">${icon('lock', 18)}</button>
        </div>
        ${pinned.length ? `<div class="section-label">Pinned</div><div class="list">${pinned.map(rowHtml).join('')}</div>` : ''}
        <div class="section-label"><span>My sections</span></div>
        <div class="grid">${tiles || '<div class="empty">No sections yet</div>'}</div>
        ${recent.length ? `<div class="section-label">Recently opened</div><div class="list">${recent.map(rowHtml).join('')}</div>` : ''}
      </div>
    </div>`;
}

function searchResults() {
  const q = S.query.trim().toLowerCase();
  if (!q) return [];
  return snap().items
    .filter((it) => !isLockedSection(it.categoryId))
    .filter((it) => it.title.toLowerCase().includes(q) || it.note.toLowerCase().includes(q) ||
      it.fields.some((f) => f.label.toLowerCase().includes(q) || (f.type !== 'SECRET' && f.value.toLowerCase().includes(q))))
    .slice(0, 50);
}

function viewSearch() {
  const results = searchResults();
  const hiddenLocked = snap().categories.some((c) => isLockedSection(c.id));
  return `
    <div class="page">
      ${topbar({ title: 'Search' })}
      <div class="scroll">
        <label class="search">${icon('search', 20)}<input id="search" placeholder="Search your vault" value="${esc(S.query)}" autocomplete="off" /></label>
        ${results.length ? `<div class="list">${results.map(rowHtml).join('')}</div>` : `<div class="empty">${icon('search', 40)}<div>No matches for “${esc(S.query)}”</div></div>`}
        ${hiddenLocked ? `<p class="muted" style="font-size:12px;text-align:center">Locked sections are not searched until you open them.</p>` : ''}
      </div>
    </div>`;
}

function viewSection() {
  const c = category(S.params.id);
  if (!c) return viewHome();
  const items = snap().items.filter((it) => it.categoryId === c.id)
    .sort((a, b) => (b.isPinned - a.isPinned) || a.title.localeCompare(b.title));
  const groups = snap().groups.filter((g) => g.categoryId === c.id).sort((a, b) => a.sortOrder - b.sortOrder);
  const loose = items.filter((it) => !it.groupId || !groups.some((g) => g.id === it.groupId));
  const grouped = groups.map((g) => {
    const inside = items.filter((it) => it.groupId === g.id);
    return inside.length ? `<div class="group-title">${icon('folder', 16)}${esc(g.name)} · ${inside.length}</div><div class="list">${inside.map(rowHtml).join('')}</div>` : '';
  }).join('');
  const menu = S.menuOpen ? `
    <div class="menu">
      <button data-action="add-here">${icon('add', 18)} Add entry here</button>
      <button data-action="toggle-section-lock">${icon(c.locked ? 'lock_open' : 'lock', 18)} ${c.locked ? 'Remove section lock' : 'Lock this section'}</button>
    </div>` : '';
  return `
    <div class="page">
      ${topbar({
        title: c.name, sub: `${items.length} ${items.length === 1 ? 'entry' : 'entries'}${c.locked ? ' · locked section' : ''}`,
        actions: `<button class="circle-btn" data-action="menu" aria-label="More">${icon('more', 20)}</button>`,
      })}
      ${menu}
      <div class="scroll">
        ${items.length ? `<div class="list">${loose.map(rowHtml).join('')}</div>${grouped}` : `<div class="empty">${icon(c.iconKey, 44)}<div>Nothing in ${esc(c.name)} yet</div><br/><button class="btn primary small" data-action="add-here">${icon('add', 18)} Add entry</button></div>`}
      </div>
    </div>`;
}

function fieldHtml(f) {
  const masked = f.type === 'SECRET' && snap().maskSecrets !== false && !S.revealed.has(f.id);
  const shown = masked ? '•'.repeat(Math.min(Math.max(f.value.length, 6), 14)) : f.value;
  const isUrl = f.type === 'URL';
  return `
    <div class="field">
      <div class="main" data-copy-field="${esc(f.id)}" title="Click to copy">
        <div class="label">${esc(f.label)}</div>
        <div class="value ${masked ? 'masked' : ''} ${['NUMBER', 'SECRET', 'PHONE'].includes(f.type) ? 'mono' : ''}">${esc(shown)}</div>
      </div>
      ${f.type === 'SECRET' ? `<button class="icon-btn" data-reveal="${esc(f.id)}" aria-label="Show or hide">${icon(masked ? 'eye' : 'eye_off', 20)}</button>` : ''}
      ${isUrl ? `<button class="icon-btn" data-open-url="${esc(f.id)}" aria-label="Open website">${icon('open_new', 19)}</button>` : ''}
      <button class="icon-btn accent" data-copy-field="${esc(f.id)}" aria-label="Copy ${esc(f.label)}">${icon('copy', 18)}</button>
    </div>`;
}

function viewItem() {
  const it = item(S.params.id);
  if (!it) return viewHome();
  const c = category(it.categoryId);
  const t = template(it.templateId);
  const fields = it.fields.filter((f) => f.value.trim());
  const files = it.attachments.map((a) => {
    const isImage = a.mime.startsWith('image/');
    const url = S.fileUrls.get(a.id);
    return `
      <button class="file" data-open-file="${esc(a.id)}" title="${esc(a.name)}">
        ${isImage && url ? `<img src="${url}" alt="${esc(a.caption || a.name)}" />` : icon(isImage ? 'image' : 'document', 30)}
        <span class="name">${esc(a.name)}</span>
      </button>`;
  }).join('');
  const menu = S.menuOpen ? `
    <div class="menu">
      <button data-action="toggle-pin">${icon('pin', 18)} ${it.isPinned ? 'Unpin' : 'Pin to Home'}</button>
      <button class="danger" data-action="delete-item">${icon('delete', 18)} Delete entry</button>
    </div>` : '';
  return `
    <div class="page">
      ${topbar({
        title: '',
        actions: `
          <button class="circle-btn" data-action="edit-item" aria-label="Edit">${icon('edit', 19)}</button>
          <button class="circle-btn" data-action="menu" aria-label="More">${icon('more', 20)}</button>`,
      })}
      ${menu}
      <div class="scroll">
        <div class="entry-hero">
          <span class="disc large">${icon(itemIconKey(it), 32)}</span>
          <h2>${esc(it.title)}</h2>
          <div class="chips">
            ${c ? `<span class="chip">${icon(c.iconKey, 13)}${esc(c.name)}</span>` : ''}
            ${t ? `<span class="chip">${esc(t.name)}</span>` : ''}
            ${it.isPinned ? `<span class="chip">${icon('pin', 13)}Pinned</span>` : ''}
          </div>
        </div>
        ${fields.length || it.note.trim() ? `<button class="btn primary block" style="margin-top:12px" data-action="copy-all">${icon('copy', 18)} Copy all details</button>` : ''}
        ${fields.length ? `<div class="section-label">Details</div><div class="list">${fields.map(fieldHtml).join('')}</div>` : `<div class="empty">No details yet — click Edit to add some.</div>`}
        ${it.note.trim() ? `<div class="section-label">Note</div><div class="note">${esc(it.note)}</div>` : ''}
        ${it.attachments.length ? `<div class="section-label">Files (${it.attachments.length})</div><div class="files">${files}</div>` : ''}
      </div>
    </div>`;
}

// ---- Editor ------------------------------------------------------------------------------------

function openEditor(existing, categoryId) {
  const it = existing ? item(existing) : null;
  S.editor = {
    id: it?.id || null,
    title: it?.title || '',
    categoryId: it?.categoryId || categoryId || sortedCategories()[0]?.id || '',
    templateId: it?.templateId || '',
    note: it?.note || '',
    isPinned: it?.isPinned || false,
    fields: (it?.fields || []).map((f) => ({ key: Math.random().toString(36).slice(2), label: f.label, value: f.value, type: f.type })),
    titleError: false,
    dirty: false,
    saving: false,
  };
  go('edit');
}

function editorFieldHtml(f, i, total) {
  const typeOptions = FIELD_TYPES.map(([v, l]) => `<option value="${v}" ${v === f.type ? 'selected' : ''}>${l}</option>`).join('');
  let input;
  if (f.type === 'MULTILINE') {
    input = `<textarea class="textarea" data-f="${i}" data-k="value" placeholder="Type here">${esc(f.value)}</textarea>`;
  } else if (f.type === 'DATE') {
    input = `<input class="input" type="date" data-f="${i}" data-k="date" value="${esc(toInputDate(f.value))}" />`;
  } else {
    const kind = { NUMBER: 'text', SECRET: 'text', PHONE: 'tel', EMAIL: 'email', URL: 'url' }[f.type] || 'text';
    input = `<input class="input" type="${kind}" data-f="${i}" data-k="value" value="${esc(f.value)}" placeholder="Type here" autocomplete="off" />`;
  }
  return `
    <div class="field-edit ${f.value.trim() ? 'filled' : ''}">
      <div class="head">
        <span class="num">${f.value.trim() ? icon('check', 14) : i + 1}</span>
        <input class="label-input" data-f="${i}" data-k="label" value="${esc(f.label)}" placeholder="Field name" />
        <select class="type" data-f="${i}" data-k="type">${typeOptions}</select>
        <button class="icon-btn" data-move="${i}:-1" ${i === 0 ? 'disabled' : ''} aria-label="Move up">${icon('up', 17)}</button>
        <button class="icon-btn" data-move="${i}:1" ${i === total - 1 ? 'disabled' : ''} aria-label="Move down">${icon('down', 17)}</button>
        <button class="icon-btn" data-remove="${i}" aria-label="Remove field">${icon('close', 18)}</button>
      </div>
      ${input}
    </div>`;
}

function viewEditor() {
  const e = S.editor;
  const cats = sortedCategories().map((c) => `<option value="${esc(c.id)}" ${c.id === e.categoryId ? 'selected' : ''}>${esc(c.name)}</option>`).join('');
  const types = [`<option value="">Custom</option>`, ...snap().templates.map((t) => `<option value="${esc(t.id)}" ${t.id === e.templateId ? 'selected' : ''}>${esc(t.name)}</option>`)].join('');
  const hint = template(e.templateId)?.titleHint || 'e.g. HBL Current Account';
  const filled = e.fields.filter((f) => f.value.trim()).length;
  return `
    <div class="page">
      ${topbar({ title: e.id ? 'Edit entry' : 'New entry', sub: e.fields.length ? `${filled} of ${e.fields.length} filled` : '' })}
      <div class="scroll">
        <div class="form-card">
          <div>
            <label class="input-label" for="title">Title</label>
            <input id="title" class="input title-input ${e.titleError ? 'error' : ''}" value="${esc(e.title)}" placeholder="Put Title Here" autocomplete="off" />
            ${e.titleError ? `<div class="error-text">Please add a title to continue</div>` : `<div class="muted" style="font-size:12px;margin-top:4px">${esc(hint)}</div>`}
          </div>
          <div class="two">
            <div><label class="input-label">Section</label><select id="section" class="select">${cats}</select></div>
            <div><label class="input-label">Type</label><select id="type" class="select">${types}</select></div>
          </div>
        </div>
        <div class="section-label"><span>Details</span><span class="muted" style="letter-spacing:0;text-transform:none;font-weight:500">Use ↑ ↓ to reorder</span></div>
        <div class="list" id="fields">${e.fields.map((f, i) => editorFieldHtml(f, i, e.fields.length)).join('') || `<div class="empty" style="padding:16px">Add the details you want to keep.</div>`}</div>
        <div class="chips-row" style="margin-top:12px">
          <button class="add-chip" data-add-field="TEXT">${icon('add', 16)} Field</button>
          ${QUICK_FIELDS.map(([type, label, ic]) => `<button class="add-chip" data-add-field="${type}" data-label="${label}">${icon(ic, 15)} ${label}</button>`).join('')}
        </div>
        <div class="section-label">Note</div>
        <textarea id="note" class="textarea" placeholder="Add a note (optional)">${esc(e.note)}</textarea>
      </div>
      <div class="sticky-actions">
        <button class="btn soft" data-action="cancel-edit">Cancel</button>
        <button class="btn primary" data-action="save-edit" ${e.saving ? 'disabled' : ''}>${e.saving ? icon('sync', 18, 'spin') : icon('check', 18)} Save</button>
      </div>
    </div>`;
}

function applyTemplate(templateId) {
  const e = S.editor;
  const t = template(templateId);
  e.templateId = templateId;
  if (!t) return;
  const have = new Set(e.fields.map((f) => f.label.trim().toLowerCase()));
  const extra = t.fields.filter((f) => !have.has(f.label.trim().toLowerCase()))
    .map((f) => ({ key: Math.random().toString(36).slice(2), label: f.label, value: '', type: f.type }));
  // Template fields lead, in the template's own order; anything the user already typed stays below.
  const blankOnly = e.fields.every((f) => !f.value.trim());
  e.fields = blankOnly ? [...t.fields.map((f) => ({ key: Math.random().toString(36).slice(2), label: f.label, value: '', type: f.type }))] : [...e.fields, ...extra];
}

async function saveEditor() {
  const e = S.editor;
  if (!e.title.trim()) {
    e.titleError = true;
    render();
    document.getElementById('title')?.focus();
    toast('Please add a title to continue', 'edit');
    return;
  }
  if (!e.categoryId) {
    toast('Pick a section first', 'folder');
    return;
  }
  e.saving = true;
  render();
  try {
    const saved = await call('saveItem', {
      id: e.id,
      categoryId: e.categoryId,
      templateId: e.templateId || null,
      title: e.title.trim(),
      note: e.note,
      isPinned: e.isPinned,
      fields: e.fields.filter((f) => f.label.trim() || f.value.trim()).map((f) => ({ label: f.label.trim() || 'Field', value: f.value, type: f.type })),
    });
    await refresh({ force: true });
    S.editor = null;
    S.stack.pop();
    S.view = 'item';
    S.params = { id: saved.id };
    await pushRecent(saved.id);
    S.recent = await getRecent();
    render();
    toast(e.id ? 'Changes saved' : 'Entry saved');
  } catch (err) {
    e.saving = false;
    render();
    toast(err instanceof PhoneOfflineError ? 'Phone not reachable — not saved' : `Not saved: ${err.message}`, 'close');
  }
}

// ---- Settings ----------------------------------------------------------------------------------

function viewSettings() {
  const link = S.link || {};
  const p = S.prefs;
  const seg = (name, options, current) => `<div class="seg">${options.map(([v, l]) => `<button data-pref="${name}" data-value="${v}" class="${String(v) === String(current) ? 'on' : ''}">${l}</button>`).join('')}</div>`;
  return `
    <div class="page">
      ${topbar({ title: 'Settings' })}
      <div class="scroll">
        <div class="section-label">Connection</div>
        <div class="settings-card">
          <div class="settings-row">
            <span class="disc small">${icon('smartphone', 18)}</span>
            <div class="main"><div class="k">${esc(link.phoneName || 'Your phone')}</div><div class="v" id="status-line">${statusLine()}</div></div>
            <button class="btn soft small" data-action="sync-now">${icon('sync', 16)} Sync</button>
          </div>
          <div class="settings-row"><div class="main"><div class="k">Phone address</div><div class="v">${esc(link.host)}:${esc(link.port)}</div></div></div>
          <div class="settings-row"><div class="main"><div class="k">Paired on</div><div class="v">${esc(new Date(link.pairedAt || Date.now()).toLocaleString())}</div></div></div>
          <div class="settings-row"><div class="main"><div class="k">Last sync</div><div class="v">${esc(timeLabel(S.syncedAt))}</div></div></div>
        </div>
        <div class="section-label">Security</div>
        <div class="settings-card">
          <div class="settings-row"><div class="main"><div class="k">Lock after</div><div class="v">Idle time before the PIN is asked again</div></div>
            ${seg('autoLockMinutes', [[1, '1m'], [5, '5m'], [15, '15m'], [30, '30m']], p.autoLockMinutes)}</div>
          <div class="settings-row"><div class="main"><div class="k">Clipboard</div><div class="v">Copied values clear after ${snap().clipboardClearSeconds || 45}s (set on your phone)</div></div></div>
          <div class="settings-row"><div class="main"><div class="k">Lock now</div><div class="v">Ask for the PIN again right away</div></div><button class="btn soft small" data-action="lock">${icon('lock', 16)} Lock</button></div>
        </div>
        <div class="section-label">Appearance</div>
        <div class="settings-card">
          <div class="settings-row"><div class="main"><div class="k">Theme</div></div>${seg('theme', [['system', 'Auto'], ['light', 'Light'], ['dark', 'Dark']], p.theme)}</div>
          ${isTab ? '' : `<div class="settings-row"><div class="main"><div class="k">Open in a tab</div><div class="v">A bigger window for long entries</div></div><button class="btn soft small" data-action="open-tab">${icon('open_new', 16)} Open</button></div>`}
        </div>
        <div style="margin-top:18px"><button class="btn danger block" data-action="logout">${icon('logout', 18)} Log out of this browser</button></div>
        <p class="muted" style="font-size:12px;text-align:center;margin-top:12px">EZZY for Chrome ${esc(chrome.runtime.getManifest().version)} · No account, no cloud.</p>
      </div>
    </div>`;
}

// ---- Dialogs --------------------------------------------------------------------------------

function confirmDialog({ title, text, ok = 'OK', danger = false }) {
  return new Promise((resolve) => {
    const wrap = document.createElement('div');
    wrap.className = 'scrim';
    wrap.innerHTML = `
      <div class="dialog" role="dialog" aria-modal="true">
        <h3>${esc(title)}</h3><p>${esc(text)}</p>
        <div class="btn-row"><button class="btn soft" data-r="0">Cancel</button><button class="btn ${danger ? 'danger' : 'primary'}" data-r="1">${esc(ok)}</button></div>
      </div>`;
    wrap.addEventListener('click', (ev) => {
      const r = ev.target.closest('[data-r]')?.dataset.r;
      if (ev.target === wrap || r !== undefined) {
        wrap.remove();
        resolve(r === '1');
      }
    });
    document.body.appendChild(wrap);
    wrap.querySelector('[data-r="1"]').focus();
  });
}

function pinDialog(title) {
  return new Promise((resolve) => {
    const wrap = document.createElement('div');
    wrap.className = 'scrim';
    wrap.innerHTML = `
      <div class="dialog" role="dialog" aria-modal="true">
        <h3>${esc(title)}</h3><p>Enter your PIN to continue.</p>
        <input class="input" type="password" inputmode="numeric" maxlength="6" autocomplete="off" placeholder="••••••" style="text-align:center;letter-spacing:8px;font-size:20px" />
        <div class="error-text hidden">Wrong PIN</div>
        <div class="btn-row" style="margin-top:16px"><button class="btn soft" data-r="0">Cancel</button><button class="btn primary" data-r="1">Open</button></div>
      </div>`;
    const input = wrap.querySelector('input');
    const error = wrap.querySelector('.error-text');
    const finish = async (ok) => {
      if (!ok) { wrap.remove(); resolve(false); return; }
      if (await verifyPin(input.value)) { wrap.remove(); resolve(true); } else {
        error.classList.remove('hidden');
        input.value = '';
        input.focus();
      }
    };
    wrap.addEventListener('click', (ev) => {
      const r = ev.target.closest('[data-r]')?.dataset.r;
      if (ev.target === wrap) finish(false);
      else if (r !== undefined) finish(r === '1');
    });
    input.addEventListener('keydown', (ev) => { if (ev.key === 'Enter') finish(true); });
    document.body.appendChild(wrap);
    input.focus();
  });
}

async function openViewer(attachment) {
  let url = S.fileUrls.get(attachment.id);
  if (!url) {
    toast('Loading file…', 'sync');
    url = await loadFile(attachment);
    if (!url) return;
  }
  if (!attachment.mime.startsWith('image/')) {
    window.open(url, '_blank');
    return;
  }
  const wrap = document.createElement('div');
  wrap.className = 'viewer';
  wrap.innerHTML = `
    <div class="bar"><span class="t">${esc(attachment.name)}</span>
      <button class="circle-btn" data-v="close" aria-label="Close">${icon('close', 20)}</button></div>
    <div class="stage"><img src="${url}" alt="${esc(attachment.caption || attachment.name)}" /></div>`;
  wrap.addEventListener('click', (ev) => {
    if (ev.target.closest('[data-v="close"]') || ev.target.classList.contains('stage')) wrap.remove();
  });
  document.body.appendChild(wrap);
}

async function loadFile(attachment) {
  try {
    const result = await call('attachment', { id: attachment.id }, { timeout: 30000 });
    const bytes = Uint8Array.from(atob(result.data), (c) => c.charCodeAt(0));
    const url = URL.createObjectURL(new Blob([bytes], { type: result.mime }));
    S.fileUrls.set(attachment.id, url);
    return url;
  } catch (err) {
    toast(err instanceof PhoneOfflineError ? 'Files need the phone nearby' : 'Could not open the file', 'close');
    return null;
  }
}

async function loadThumbnails(it) {
  for (const a of it.attachments) {
    if (!a.mime.startsWith('image/') || S.fileUrls.has(a.id) || a.size > 4 * 1024 * 1024) continue;
    const url = await loadFile(a);
    if (!url || S.view !== 'item' || S.params.id !== it.id) return;
    const el = root.querySelector(`[data-open-file="${CSS.escape(a.id)}"]`);
    if (el) el.innerHTML = `<img src="${url}" alt="" /><span class="name">${esc(a.name)}</span>`;
  }
}

// ---- Events ----------------------------------------------------------------------------------

function afterRender() {
  const search = document.getElementById('search');
  if (search) {
    search.addEventListener('input', () => {
      S.query = search.value;
      if (S.query.trim() && S.view !== 'search') {
        go('search');
      } else if (S.view === 'search') {
        if (!S.query.trim()) { back(); return; }
        const pos = search.selectionStart;
        render();
        const again = document.getElementById('search');
        again.focus();
        again.setSelectionRange(pos, pos);
      }
    });
    if (S.view === 'search') {
      search.focus();
      search.setSelectionRange(search.value.length, search.value.length);
    }
  }
  if (S.view === 'item') {
    const it = item(S.params.id);
    if (it && S.online !== false) loadThumbnails(it);
  }
  if (S.view === 'edit') bindEditorInputs();
}

function bindEditorInputs() {
  const e = S.editor;
  const title = document.getElementById('title');
  title.addEventListener('input', () => {
    e.title = title.value;
    e.dirty = true;
    if (e.titleError && e.title.trim()) {
      e.titleError = false;
      title.classList.remove('error');
      const err = title.parentElement.querySelector('.error-text');
      if (err) err.remove();
    }
  });
  if (!e.id && !e.title) title.focus();
  document.getElementById('section').addEventListener('change', (ev) => { e.categoryId = ev.target.value; e.dirty = true; });
  document.getElementById('type').addEventListener('change', (ev) => { applyTemplate(ev.target.value); e.dirty = true; render(); });
  document.getElementById('note').addEventListener('input', (ev) => { e.note = ev.target.value; e.dirty = true; });
  root.querySelectorAll('[data-f]').forEach((el) => {
    const i = Number(el.dataset.f);
    const k = el.dataset.k;
    const handler = () => {
      const f = e.fields[i];
      e.dirty = true;
      if (k === 'type') { f.type = el.value; render(); return; }
      if (k === 'date') f.value = fromInputDate(el.value);
      else f[k] = el.value;
      if (k !== 'label') {
        const card = el.closest('.field-edit');
        card.classList.toggle('filled', !!f.value.trim());
        card.querySelector('.num').innerHTML = f.value.trim() ? icon('check', 14) : String(i + 1);
      }
    };
    el.addEventListener(k === 'type' || k === 'date' ? 'change' : 'input', handler);
  });
}

async function onKey(key) {
  if (S.busy) return;
  if (key === 'del') S.pin = S.pin.slice(0, -1);
  else if (S.pin.length < 6) S.pin += key;
  S.pinMessage = '';
  const dots = document.getElementById('dots');
  if (dots) dots.querySelectorAll('.dot').forEach((d, i) => d.classList.toggle('on', i < S.pin.length));
  if (S.pin.length < 6) return;
  S.busy = true;
  document.getElementById('pin-msg').textContent = 'Checking…';
  const result = await unlock(S.pin);
  S.busy = false;
  S.pin = '';
  if (result.ok) {
    chrome.runtime.sendMessage({ type: 'unlocked' }).catch(() => {});
    S.pinMessage = '';
    await openVault();
    return;
  }
  if (result.wiped) {
    S.link = null;
    S.flash = 'Too many wrong PINs. This browser was wiped — pair again from your phone.';
    S.view = 'welcome';
    render();
    return;
  }
  S.pinMessage = result.wait ? `Too many tries. Wait ${result.wait}s.` : `Wrong PIN · ${result.left} tries left`;
  render();
  const d = document.getElementById('dots');
  d?.classList.add('shake');
}

root.addEventListener('click', async (ev) => {
  noteActivity();
  const t = ev.target.closest('button, [data-copy-field]');
  if (!t) {
    if (S.menuOpen && !ev.target.closest('.menu')) { S.menuOpen = false; render(); }
    return;
  }
  const d = t.dataset;

  if (d.key) return onKey(d.key);
  if (d.openSection) {
    const id = d.openSection;
    if (isLockedSection(id)) {
      if (!(await pinDialog(`Open ${category(id)?.name || 'section'}`))) return;
      S.unlockedSections.add(id);
      await setUnlockedSections([...S.unlockedSections]);
    }
    return go('section', { id });
  }
  if (d.openItem) {
    await pushRecent(d.openItem);
    S.recent = await getRecent();
    call('touch', { id: d.openItem }).catch(() => {});
    S.revealed.clear();
    return go('item', { id: d.openItem });
  }
  if (d.copyField) {
    const it = item(S.params.id);
    const f = it?.fields.find((x) => x.id === d.copyField);
    if (f) copyText(f.value, `${f.label} copied`);
    return;
  }
  if (d.reveal) {
    S.revealed.has(d.reveal) ? S.revealed.delete(d.reveal) : S.revealed.add(d.reveal);
    return render();
  }
  if (d.openUrl) {
    const f = item(S.params.id)?.fields.find((x) => x.id === d.openUrl);
    if (f) {
      const url = /^https?:\/\//i.test(f.value) ? f.value : `https://${f.value}`;
      chrome.tabs.create({ url });
    }
    return;
  }
  if (d.openFile) {
    const a = item(S.params.id)?.attachments.find((x) => x.id === d.openFile);
    if (a) openViewer(a);
    return;
  }
  if (d.addField) {
    S.editor.fields.push({ key: Math.random().toString(36).slice(2), label: d.label || '', value: '', type: d.addField });
    S.editor.dirty = true;
    render();
    const inputs = root.querySelectorAll(`[data-f="${S.editor.fields.length - 1}"]`);
    inputs[d.label ? inputs.length - 1 : 0]?.focus();
    return;
  }
  if (d.move) {
    const [i, dir] = d.move.split(':').map(Number);
    const fields = S.editor.fields;
    const j = i + dir;
    if (j < 0 || j >= fields.length) return;
    [fields[i], fields[j]] = [fields[j], fields[i]];
    S.editor.dirty = true;
    return render();
  }
  if (d.remove !== undefined) {
    S.editor.fields.splice(Number(d.remove), 1);
    S.editor.dirty = true;
    return render();
  }
  if (d.pref) {
    const value = d.pref === 'autoLockMinutes' ? Number(d.value) : d.value;
    S.prefs = await setPrefs({ [d.pref]: value });
    if (d.pref === 'theme') applyTheme(value);
    if (d.pref === 'autoLockMinutes') chrome.runtime.sendMessage({ type: 'activity' }).catch(() => {});
    return render();
  }

  switch (d.action) {
    case 'back':
      if (S.view === 'edit' && S.editor?.dirty && !(await confirmDialog({ title: 'Discard changes?', text: 'What you typed here has not been saved.', ok: 'Discard', danger: true }))) return;
      if (S.view === 'search') S.query = '';
      return back();
    case 'pair':
      chrome.tabs.create({ url: chrome.runtime.getURL('pair.html') });
      if (!isTab) window.close();
      return;
    case 'settings':
      S.prefs = await getPrefs();
      return go('settings');
    case 'add':
      return openEditor(null, null);
    case 'add-here':
      return openEditor(null, S.params.id);
    case 'menu':
      S.menuOpen = !S.menuOpen;
      return render();
    case 'lock':
      await lockNow();
      S.view = 'locked';
      S.stack = [];
      return render();
    case 'copy-all': {
      const it = item(S.params.id);
      if (it) copyText(allDetailsText(it), 'All details copied');
      return;
    }
    case 'edit-item':
      return openEditor(S.params.id);
    case 'toggle-pin': {
      const it = item(S.params.id);
      S.menuOpen = false;
      try {
        await call('setPinned', { id: it.id, pinned: !it.isPinned });
        await refresh({ force: true });
        render();
        toast(it.isPinned ? 'Unpinned' : 'Pinned to Home', 'pin');
      } catch {
        toast('Phone not reachable', 'wifi_off');
      }
      return;
    }
    case 'delete-item': {
      const it = item(S.params.id);
      S.menuOpen = false;
      render();
      if (!(await confirmDialog({ title: `Delete “${it.title}”?`, text: 'It is removed from your phone too. This cannot be undone.', ok: 'Delete', danger: true }))) return;
      try {
        await call('deleteItem', { id: it.id });
        await refresh({ force: true });
        back();
        toast('Entry deleted', 'delete');
      } catch {
        toast('Phone not reachable — not deleted', 'wifi_off');
      }
      return;
    }
    case 'toggle-section-lock': {
      const c = category(S.params.id);
      S.menuOpen = false;
      if (c.locked && !(await pinDialog('Remove section lock'))) return render();
      try {
        await call('setSectionLock', { categoryId: c.id, locked: !c.locked });
        await refresh({ force: true });
        render();
        toast(c.locked ? 'Section lock removed' : 'Section locked', 'lock');
      } catch {
        toast('Phone not reachable', 'wifi_off');
        render();
      }
      return;
    }
    case 'cancel-edit':
      if (S.editor?.dirty && !(await confirmDialog({ title: 'Discard changes?', text: 'What you typed here has not been saved.', ok: 'Discard', danger: true }))) return;
      S.editor = null;
      return back();
    case 'save-edit':
      return saveEditor();
    case 'sync-now':
      return refresh({ force: true, quiet: false });
    case 'open-tab':
      chrome.tabs.create({ url: chrome.runtime.getURL('app.html?tab=1') });
      window.close();
      return;
    case 'logout':
      if (!(await confirmDialog({ title: 'Log out of this browser?', text: 'Your vault is wiped from Chrome and the phone stops syncing with it. Everything stays safe on your phone.', ok: 'Log out', danger: true }))) return;
      await logout();
      S.link = null;
      S.snapshot = null;
      S.flash = '';
      S.view = 'welcome';
      S.stack = [];
      return render();
    default:
  }
});

document.addEventListener('keydown', (ev) => {
  noteActivity();
  if (S.view === 'locked') {
    if (/^\d$/.test(ev.key)) onKey(ev.key);
    else if (ev.key === 'Backspace') onKey('del');
  } else if (ev.key === 'Escape' && S.view !== 'home' && !document.querySelector('.scrim, .viewer')) {
    ev.preventDefault();
    root.querySelector('[data-action="back"]')?.click();
  } else if (ev.key === 'Escape') {
    document.querySelector('.viewer')?.remove();
  }
});

chrome.runtime.onMessage.addListener((message) => {
  if (message?.type === 'locked' && !['welcome', 'locked'].includes(S.view)) {
    S.view = 'locked';
    S.stack = [];
    render();
  } else if (message?.type === 'synced' && S.view !== 'edit') {
    loadCache().then(render);
  } else if (message?.type === 'revoked') {
    S.link = null;
    S.flash = 'This browser was logged out from your phone. Pair again to continue.';
    S.view = 'welcome';
    render();
  }
});

// ---- Start -------------------------------------------------------------------------------------

async function openVault() {
  await loadCache();
  S.view = 'home';
  S.stack = [];
  render();
  startPolling();
  refresh();
}

async function start() {
  S.prefs = await getPrefs();
  applyTheme(S.prefs.theme);
  matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => applyTheme(S.prefs.theme));
  S.link = await getLink();
  if (!S.link) {
    S.view = 'welcome';
    render();
    return;
  }
  if (!(await getSessionKey())) {
    const sec = await securityState();
    if (Date.now() < sec.lockedUntil) S.pinMessage = `Too many tries. Wait ${Math.ceil((sec.lockedUntil - Date.now()) / 1000)}s.`;
    S.view = 'locked';
    render();
    return;
  }
  chrome.runtime.sendMessage({ type: 'activity' }).catch(() => {});
  await openVault();
}

render();
start();
