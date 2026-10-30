// Image DSR launcher - keeps the small launcher page working, the DSR app itself always loads live
const CACHE = 'image-dsr-v5';
const SHELL = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png', './favicon-32.png', './favicon-64.png', './favicon-128.png', './favicon.ico'];
// install at once (files are cached in the background), so Chrome can offer "Install" sooner
self.addEventListener('install', () => { caches.open(CACHE).then(c => c.addAll(SHELL)).catch(() => {}); self.skipWaiting(); });
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url);
  if (u.origin !== location.origin || e.request.method !== 'GET') return;     // Google app: never cached
  const page = e.request.mode === 'navigate' || /\.(html|webmanifest|js)$/.test(u.pathname) || u.pathname.endsWith('/');
  if (page) {
    // page: always the newest from GitHub; phone copy only when offline
    e.respondWith(fetch(e.request.url, { cache: 'no-cache' }).then(r => {
      const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); return r;
    }).catch(() => caches.match(e.request).then(r => r || caches.match('./index.html'))));
  } else {
    // icons / pictures / manuals: phone copy at once, refreshed in the background
    e.respondWith(caches.match(e.request).then(hit => {
      const net = fetch(e.request).then(r => { if (r.ok && !/\.pdf$/.test(u.pathname)) { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); } return r; });
      return hit || net;
    }));
  }
});
