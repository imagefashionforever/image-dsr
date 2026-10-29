IMAGE DSR - HOME SCREEN LAUNCHER
================================
These files make the "Add to Home Screen" button appear by itself when the app is opened.

1. Open index.html and replace  https://script.google.com/macros/s/AKfycbzPmnCgugVUtT0TlRGaT_A9yEuz4H2claaL_iFXx-XmUjPmNkwRomZ031KHk3tKIFtb-g/exec
   with your web app link (Apps Script > Deploy > Manage deployments > Web app URL).
2. Apps Script deployment must be:  Execute as = Me,  Who has access = Anyone.
3. Put ALL these files in one folder on a free HTTPS host (GitHub Pages / Netlify / Firebase Hosting).
4. Share the NEW link (e.g. https://yourname.github.io/image-dsr/) with the staff instead of the script.google.com link.

Files: index.html, manifest.webmanifest, sw.js, icon-192.png, icon-512.png, icon-maskable-512.png, apple-touch-icon.png
