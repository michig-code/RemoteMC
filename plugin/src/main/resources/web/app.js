// RemoteMC web app: served by the plugin itself, so it talks to the same address (no other server).
// Pair with the code from /remotemc pair; the token stays in this browser only.
'use strict';

const TEXT = {
  en: {
    pairTitle: 'RemoteMC',
    pairText: 'Type /remotemc pair on your server and enter the 6-digit code.',
    pair: 'Pair',
    pairHint: 'The code is valid for a few minutes. Tip: “Add to Home Screen” in the browser menu makes this a home-screen app.',
    wrongCode: 'Wrong or expired code. Type /remotemc pair again.',
    locked: 'Too many attempts. Please wait a few minutes.',
    unreachable: 'The server can’t be reached.',
    overview: 'Overview', players: 'Players', console: 'Console', chat: 'Chat',
    online: 'Online', offline: 'Offline', connecting: 'Connecting…',
    playersOnline: 'Players online', tps: 'TPS', mspt: 'ms per tick', memory: 'Memory', uptime: 'Uptime',
    version: 'Version', worlds: 'Worlds', worldMeta: '{0} · {1} players · {2} chunks',
    nobody: 'Nobody is online.', ping: '{0} ms',
    command: 'Command, e.g. say Hello', send: 'Send', message: 'Message to everyone',
    noCommands: 'Your access can’t run commands.', noConsole: 'Your access can’t see the console.',
    joined: '{0} joined', left: '{0} left',
    logout: 'Unpair', logoutAsk: 'Remove access for this browser? You need a new code to come back.',
    revoked: 'Access was revoked. Pair again.',
    done: 'Done (no output).',
  },
  de: {
    pairTitle: 'RemoteMC',
    pairText: 'Gib /remotemc pair auf deinem Server ein und trage den 6-stelligen Code ein.',
    pair: 'Koppeln',
    pairHint: 'Der Code gilt ein paar Minuten. Tipp: Mit „Zum Home-Bildschirm“ im Browser-Menü wird daraus eine App auf dem Startbildschirm.',
    wrongCode: 'Falscher oder abgelaufener Code. Gib /remotemc pair erneut ein.',
    locked: 'Zu viele Versuche. Bitte ein paar Minuten warten.',
    unreachable: 'Der Server ist nicht erreichbar.',
    overview: 'Übersicht', players: 'Spieler', console: 'Konsole', chat: 'Chat',
    online: 'Online', offline: 'Offline', connecting: 'Verbinde…',
    playersOnline: 'Spieler online', tps: 'TPS', mspt: 'ms pro Tick', memory: 'Arbeitsspeicher', uptime: 'Laufzeit',
    version: 'Version', worlds: 'Welten', worldMeta: '{0} · {1} Spieler · {2} Chunks',
    nobody: 'Niemand ist online.', ping: '{0} ms',
    command: 'Befehl, z. B. say Hallo', send: 'Senden', message: 'Nachricht an alle',
    noCommands: 'Dein Zugang darf keine Befehle ausführen.', noConsole: 'Dein Zugang darf die Konsole nicht sehen.',
    joined: '{0} ist beigetreten', left: '{0} hat das Spiel verlassen',
    logout: 'Abmelden', logoutAsk: 'Zugang für diesen Browser entfernen? Zum Zurückkommen brauchst du einen neuen Code.',
    revoked: 'Der Zugang wurde entzogen. Bitte neu koppeln.',
    done: 'Erledigt (keine Ausgabe).',
  },
};
const LANG = (navigator.language || 'en').toLowerCase().startsWith('de') ? 'de' : 'en';
document.documentElement.lang = LANG;
const t = (key, ...args) => (TEXT[LANG][key] ?? TEXT.en[key]).replace(/\{(\d)\}/g, (_, i) => args[i]);

const TOKEN = 'remotemc.token';
let token = localStorage.getItem(TOKEN);

/** Calls the plugin's API; throws an Error with .status for HTTP errors. */
async function api(method, path, body) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  if (method === 'POST') headers['Content-Type'] = 'application/json';
  let res;
  try {
    res = await fetch(path, { method, headers, cache: 'no-store', body: method === 'POST' ? JSON.stringify(body || {}) : undefined });
  } catch (e) {
    const err = new Error(t('unreachable'));
    err.status = 0;
    throw err;
  }
  let json = null;
  try { json = await res.json(); } catch (e) { /* not JSON */ }
  if (!res.ok) {
    const err = new Error((json && json.message) || 'HTTP ' + res.status);
    err.status = res.status;
    err.code = json && json.error;
    throw err;
  }
  return json || {};
}

/** Small element builder; text is always set as text, never as HTML. */
function el(tag, attrs, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') node.className = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c == null || c === false) continue;
    node.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
  return node;
}

const app = document.getElementById('app');
const stripColors = (s) => String(s || '').replace(/§./g, '');

// ── Pairing ───────────────────────────────────────────────────────

function showPair(message) {
  stopPolling();
  const fromLink = (location.hash.match(/code=(\d{6})/) || [])[1] || '';
  history.replaceState(null, '', location.pathname); // the code is single-use; keep it out of the history
  const input = el('input', {
    class: 'code', inputmode: 'numeric', autocomplete: 'one-time-code', maxlength: '6', placeholder: '••••••',
    'aria-label': 'Code', value: fromLink,
  });
  const error = el('div', { class: 'error', role: 'alert' }, message || '');
  const button = el('button', { class: 'btn wide' }, t('pair'));
  const go = async () => {
    const code = input.value.replace(/\D/g, '');
    if (code.length !== 6) { input.focus(); return; }
    button.disabled = true;
    error.textContent = '';
    try {
      const device = /iPhone|iPad/.test(navigator.userAgent) ? 'iPhone (Browser)'
        : /Android/.test(navigator.userAgent) ? 'Android (Browser)' : 'Browser';
      const o = await api('POST', '/api/v1/pair', { code, device });
      token = o.token;
      localStorage.setItem(TOKEN, token);
      showMain();
    } catch (e) {
      error.textContent = e.status === 429 ? t('locked') : e.status === 0 ? e.message : t('wrongCode');
      button.disabled = false;
    }
  };
  button.addEventListener('click', go);
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') go(); });
  app.replaceChildren(el('div', { class: 'pair' },
    el('img', { src: 'icon.png', alt: '' }),
    el('h1', null, t('pairTitle')),
    el('p', null, t('pairText')),
    input, button, error,
    el('div', { class: 'hint' }, t('pairHint'))));
  input.focus();
  if (fromLink) go();
}

// ── Main screen ───────────────────────────────────────────────────

const state = { tab: 'overview', role: 'viewer', commands: true, consoleSince: 0, chatSince: 0, status: null };
let timer = null;
const stopPolling = () => { clearTimeout(timer); timer = null; };

const header = {};
const content = el('main');

async function showMain() {
  stopPolling();
  state.consoleSince = 0;
  state.chatSince = 0;
  header.name = el('h1', null, 'RemoteMC');
  header.dot = el('span', { class: 'dot' });
  header.sub = el('span', null, t('connecting'));
  const nav = el('nav');
  app.replaceChildren(
    el('header', null,
      el('img', { src: 'icon.png', alt: '' }),
      el('div', { class: 'title' }, header.name, el('div', { class: 'sub' }, header.dot, header.sub)),
      el('button', { class: 'btn ghost', onclick: logout }, t('logout'))),
    nav, content);
  try {
    const me = await api('GET', '/api/v1/me');
    state.role = me.role || 'viewer';
    state.commands = me.commandsEnabled !== false;
  } catch (e) {
    if (e.status === 401) { forget(t('revoked')); return; }
  }
  const tabs = ['overview', 'players', 'chat'];
  if (state.role !== 'viewer') tabs.splice(2, 0, 'console');
  for (const id of tabs) {
    nav.append(el('button', { 'data-tab': id, onclick: () => openTab(id) }, t(id)));
  }
  openTab(tabs.includes(state.tab) ? state.tab : 'overview');
}

function openTab(id) {
  state.tab = id;
  document.querySelectorAll('nav button').forEach((b) => b.classList.toggle('active', b.dataset.tab === id));
  content.replaceChildren();
  views[id].init();
  stopPolling();
  poll();
}

async function poll() {
  try {
    const status = await api('GET', '/api/v1/status');
    state.status = status;
    header.name.textContent = stripColors(status.motd).split('\n')[0].trim() || 'RemoteMC';
    header.dot.className = 'dot on';
    header.sub.textContent = t('online') + ' · ' + status.onlinePlayers + '/' + status.maxPlayers;
    await views[state.tab].update(status);
  } catch (e) {
    if (e.status === 401) { forget(t('revoked')); return; }
    header.dot.className = 'dot off';
    header.sub.textContent = t('offline') + ' · ' + e.message;
  }
  timer = setTimeout(poll, state.tab === 'overview' || state.tab === 'players' ? 5000 : 2000);
}

function forget(message) {
  token = null;
  localStorage.removeItem(TOKEN);
  showPair(message);
}

function logout() {
  if (confirm(t('logoutAsk'))) forget('');
}

function fmtUptime(s) {
  const d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600), m = Math.floor((s % 3600) / 60);
  return d > 0 ? `${d} d ${h} h` : h > 0 ? `${h} h ${m} min` : `${m} min`;
}

const tpsClass = (tps) => (tps >= 18 ? 'good' : tps >= 15 ? 'warn' : 'bad');

function bar(fraction) {
  const inner = el('div');
  inner.style.width = Math.max(0, Math.min(1, fraction)) * 100 + '%';
  return el('div', { class: 'bar' }, inner);
}

const views = {
  overview: {
    init() {},
    update(s) {
      const tps = (s.tps || [])[0];
      const cards = [
        el('div', { class: 'card' },
          el('div', { class: 'label' }, t('playersOnline')),
          el('div', { class: 'big' }, String(s.onlinePlayers), el('small', null, ' / ' + s.maxPlayers)),
          bar(s.maxPlayers ? s.onlinePlayers / s.maxPlayers : 0)),
      ];
      if (tps != null) {
        cards.push(el('div', { class: 'card' },
          el('div', { class: 'label' }, t('tps')),
          el('div', { class: 'big ' + tpsClass(tps) }, tps.toFixed(1)),
          s.mspt >= 0 ? el('div', { class: 'label' }, s.mspt.toFixed(1) + ' ' + t('mspt')) : null));
      }
      if (s.memoryMaxMb > 0) {
        cards.push(el('div', { class: 'card' },
          el('div', { class: 'label' }, t('memory')),
          el('div', { class: 'big' }, (s.memoryUsedMb / 1024).toFixed(1), el('small', null, ' / ' + (s.memoryMaxMb / 1024).toFixed(1) + ' GB')),
          bar(s.memoryUsedMb / s.memoryMaxMb)));
      }
      cards.push(el('div', { class: 'card' },
        el('div', { class: 'label' }, t('uptime')),
        el('div', { class: 'big' }, fmtUptime(s.uptimeSeconds || 0)),
        el('div', { class: 'label' }, t('version') + ': ' + [s.platform, s.minecraftVersion].filter(Boolean).join(' '))));
      if ((s.worlds || []).length) {
        cards.push(el('div', { class: 'card full' },
          el('div', { class: 'label' }, t('worlds')),
          s.worlds.map((w) => el('div', { class: 'row' },
            el('div', { class: 'grow' },
              el('div', { class: 'name' }, w.name),
              el('div', { class: 'meta' }, t('worldMeta', w.environment, w.players, w.loadedChunks)))))));
      }
      if (s.motd) cards.unshift(el('div', { class: 'card full motd' }, stripColors(s.motd)));
      content.replaceChildren(el('div', { class: 'grid' }, cards));
    },
  },

  players: {
    init() {},
    async update() {
      const o = await api('GET', '/api/v1/players');
      const list = o.players || [];
      content.replaceChildren(el('div', { class: 'card' },
        list.length === 0 ? el('div', { class: 'empty' }, t('nobody'))
          : list.map((p) => el('div', { class: 'row' },
            el('img', { class: 'head', alt: '', src: 'https://minotar.net/helm/' + encodeURIComponent(p.name) + '/64.png' }),
            el('div', { class: 'grow' },
              el('div', { class: 'name' }, p.name),
              el('div', { class: 'meta' }, [p.world, p.gameMode && p.gameMode.toLowerCase()].filter(Boolean).join(' · '))),
            el('div', { class: 'meta' }, t('ping', p.ping))))));
    },
  },

  console: {
    init() {
      this.log = el('div', { class: 'log', 'aria-live': 'polite' });
      const input = el('input', { placeholder: t('command'), autocapitalize: 'off', autocorrect: 'off', spellcheck: 'false', enterkeyhint: 'send' });
      const button = el('button', { class: 'btn' }, t('send'));
      const send = async () => {
        const cmd = input.value.trim().replace(/^\//, '');
        if (!cmd) return;
        button.disabled = true;
        this.add('COMMAND', '> /' + cmd);
        try {
          const o = await api('POST', '/api/v1/command', { command: cmd });
          const out = o.output || [];
          if (out.length === 0) this.add('OUTPUT', t('done'));
          out.forEach((line) => this.add('OUTPUT', stripColors(line)));
          input.value = '';
        } catch (e) {
          this.add('ERROR', e.message);
        }
        button.disabled = false;
        input.focus();
      };
      button.addEventListener('click', send);
      input.addEventListener('keydown', (e) => { if (e.key === 'Enter') send(); });
      const canRun = state.role === 'admin' && state.commands;
      content.replaceChildren(this.log,
        canRun ? el('div', { class: 'send' }, input, button) : el('div', { class: 'note' }, t('noCommands')));
    },
    add(level, text, time) {
      const stick = this.log.scrollTop + this.log.clientHeight >= this.log.scrollHeight - 30;
      const line = el('div', { class: level },
        time ? el('span', { class: 'time' }, new Date(time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) + ' ') : null,
        text);
      this.log.append(line);
      while (this.log.childElementCount > 500) this.log.firstChild.remove();
      if (stick) this.log.scrollTop = this.log.scrollHeight;
    },
    async update() {
      try {
        const o = await api('GET', '/api/v1/console?since=' + state.consoleSince);
        for (const l of o.lines || []) this.add(l.level, stripColors(l.message), l.time);
        if (o.last != null) state.consoleSince = o.last;
      } catch (e) {
        if (e.status === 403) content.replaceChildren(el('div', { class: 'empty' }, t('noConsole')));
        else throw e;
      }
    },
  },

  chat: {
    init() {
      this.log = el('div', { class: 'log', 'aria-live': 'polite' });
      const input = el('input', { placeholder: t('message'), enterkeyhint: 'send', maxlength: '256' });
      const button = el('button', { class: 'btn' }, t('send'));
      const send = async () => {
        const message = input.value.trim();
        if (!message) return;
        button.disabled = true;
        try {
          await api('POST', '/api/v1/chat', { message });
          input.value = '';
          await this.update();
        } catch (e) {
          this.log.append(el('div', { class: 'ERROR' }, e.message));
        }
        button.disabled = false;
        input.focus();
      };
      button.addEventListener('click', send);
      input.addEventListener('keydown', (e) => { if (e.key === 'Enter') send(); });
      content.replaceChildren(this.log, state.role !== 'viewer' ? el('div', { class: 'send' }, input, button) : null);
    },
    async update() {
      const o = await api('GET', '/api/v1/chat?since=' + state.chatSince);
      const stick = this.log.scrollTop + this.log.clientHeight >= this.log.scrollHeight - 30;
      for (const m of o.messages || []) {
        const time = el('span', { class: 'time' }, new Date(m.time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) + ' ');
        const type = String(m.type || 'chat').toLowerCase();
        this.log.append(type === 'join' || type === 'quit'
          ? el('div', { class: 'event' }, time, t(type === 'join' ? 'joined' : 'left', m.player))
          : el('div', null, time, el('span', { class: 'who' }, (m.player || 'Server') + ': '), stripColors(m.message)));
      }
      while (this.log.childElementCount > 500) this.log.firstChild.remove();
      if (o.last != null) state.chatSince = o.last;
      if (stick) this.log.scrollTop = this.log.scrollHeight;
    },
  },
};

// A new pairing link opened while the page is already open (only the #code changes).
window.addEventListener('hashchange', () => { if (/code=\d{6}/.test(location.hash)) showPair(); });

if (token) showMain(); else showPair();
