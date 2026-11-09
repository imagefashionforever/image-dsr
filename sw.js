// Image DSR launcher - keeps the small launcher page working, the DSR app itself always loads live
// v6: the launcher page opens at once from the phone copy and is refreshed in the background
//     (before, every open waited for GitHub first - slow on a weak network)
const CACHE = 'image-dsr-v6';
const SHELL = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png', './favicon-32.png', './favicon-64.png', './favicon-128.png', './favicon.ico'];
self.addEventListener('install', () => { caches.open(CACHE).then(c => c.addAll(SHELL)).catch(() => {}); self.skipWaiting(); });
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url);
  if (u.origin !== location.origin || e.request.method !== 'GET') return;     // Google app: never cached
  if (/\.(apk|json)$/.test(u.pathname) || u.search) return;                    // APK, app-version.json, ?t=...: always live
  const page = e.request.mode === 'navigate' || /\.(html|webmanifest|js)$/.test(u.pathname) || u.pathname.endsWith('/');
  const fresh = (key) => fetch(e.request.url, { cache: 'no-cache' }).then(r => {
    if (r.ok) { const copy = r.clone(); caches.open(CACHE).then(c => c.put(key, copy)); }
    return r;
  });
  if (page) {
    // page: phone copy at once (if there is one), newest copy saved for the next open; first open / no copy: from GitHub
    const key = e.request.mode === 'navigate' ? './index.html' : e.request;
    e.respondWith(caches.match(key).then(hit => {
      const net = fresh(key).catch(() => hit || caches.match('./index.html'));
      if (hit) { e.waitUntil(net.catch(() => {})); return hit; }
      return net;
    }));
  } else {
    // icons / pictures: phone copy at once, refreshed in the background
    e.respondWith(caches.match(e.request).then(hit => {
      const net = fresh(e.request);
      if (hit) { e.waitUntil(net.catch(() => {})); return hit; }
      return net;
    }));
  }
});
