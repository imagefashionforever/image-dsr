// Upload next to index.html and sw.js on GitHub (…/image-dsr/firebase-messaging-sw.js).
// It is registered with its own scope (./fcm/), so your sw.js stays as it is.

// tap on the notification -> open / bring up Image DSR (before Firebase loads)
self.addEventListener('notificationclick', e => {
  e.notification.close();
  const url = (e.notification.data && e.notification.data.link) || new URL('../', self.registration.scope).href;
  e.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then(list => {
    for (const c of list) if (c.url.indexOf(url) === 0 && 'focus' in c) return c.focus();
    return clients.openWindow(url);
  }));
});

importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js');

firebase.initializeApp({ apiKey: 'AIzaSyAXvHFyNafSzYSpdyM8A18GHCekbyH9-WU', authDomain: 'image-dsr.firebaseapp.com', projectId: 'image-dsr', messagingSenderId: '327455366476', appId: '1:327455366476:web:852362a269bfc5538dc3e9' });   // SAME values as FIREBASE in index.html

// normal phone notification (like WhatsApp / Gmail), shown even when the app is closed
firebase.messaging().onBackgroundMessage(p => {
  const d = p.data || {};
  return self.registration.showNotification(d.title || 'Image DSR', {
    body: d.body || 'Please fill your sale report.',
    icon: '../icon-192.png',
    tag: d.tag || 'dsr-pending', renotify: true,
    vibrate: [300, 100, 300],
    data: { link: d.link }
  });
});
