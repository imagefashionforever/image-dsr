package com.imagefashion.dsr;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.firebase.messaging.FirebaseMessaging;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Image DSR app: opens the launcher page (GitHub Pages) in a WebView.
 *  - FLAG_SECURE: screenshots and screen recording of the app are blocked (black screen in recent apps too)
 *  - camera, GPS, file upload (photo / Excel), file download and share work like in Chrome
 *  - sale-report reminders come as normal phone notifications (Firebase, see PushService)
 */
public class MainActivity extends Activity {

    static final String START_URL = "https://imagefashionforever.github.io/image-dsr/";
    static final String UA_TAG = " ImageDSRApp/" + BuildConfig.VERSION_NAME;   // the web app checks this

    private static final int REQ_FILE = 11, REQ_CAMERA_PAGE = 12, REQ_CAMERA_FILE = 13, REQ_GEO = 14, REQ_NOTIF = 15;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private WebChromeClient.FileChooserParams fileParams;
    private Uri cameraPhoto;
    private PermissionRequest pagePermission;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        // No screenshots, no screen recording, hidden in the recent-apps preview
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#f5f3f8"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);                 // target="_blank" links -> onCreateWindow -> outside browser
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setUserAgentString(s.getUserAgentString() + UA_TAG);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "DSRNative");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());

        if (saved != null) web.restoreState(saved);
        if (web.getUrl() == null) web.loadUrl(START_URL);

        PushService.ensureChannel(this);
        askNotificationPermission();
        refreshPushToken();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    // ---------------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------------
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private void refreshPushToken() {
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(t -> {
                if (t.isSuccessful() && t.getResult() != null) PushService.saveToken(this, t.getResult());
            });
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------------
    // Links: our pages stay in the app, everything else opens outside (WhatsApp, maps, PDFs ...)
    // ---------------------------------------------------------------------
    static boolean ownPage(Uri u) {
        String scheme = u.getScheme(), host = u.getHost();
        if (scheme == null) return false;
        if (scheme.equals("about") || scheme.equals("blob") || scheme.equals("data")) return true;
        if (!scheme.equals("https") || host == null) return false;
        if (host.equals("script.google.com") || host.endsWith(".googleusercontent.com")) return true;
        String path = u.getPath() == null ? "" : u.getPath().toLowerCase();
        return host.equals("imagefashionforever.github.io") && !path.endsWith(".pdf") && !path.endsWith(".apk");
    }

    void openOutside(Uri u) {
        try {
            Intent i;
            if ("intent".equals(u.getScheme())) i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
            else i = new Intent(Intent.ACTION_VIEW, u);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            i.setComponent(null);
            i.setSelector(null);
            startActivity(i);
        } catch (Exception e) {
            toast("No app found to open this link.");
        }
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (ownPage(u)) return false;
            openOutside(u);
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            if (!req.isForMainFrame()) return;
            String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                    + "<body style='font-family:sans-serif;text-align:center;padding:60px 20px;color:#1f1a2b;background:#f5f3f8'>"
                    + "<h2 style='color:#9c1b74'>No internet</h2><p>Please check your mobile data / Wi-Fi.</p>"
                    + "<p><a href='" + START_URL + "' style='display:inline-block;padding:12px 28px;border-radius:10px;"
                    + "background:#9c1b74;color:#fff;text-decoration:none;font-weight:bold'>Try again</a></p></body></html>";
            view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", START_URL);
        }
    }

    // ---------------------------------------------------------------------
    // Camera, location, file upload, new windows
    // ---------------------------------------------------------------------
    private boolean has(String perm) { return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED; }

    private class Chrome extends WebChromeClient {
        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
            if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                cb.invoke(origin, true, false);
                return;
            }
            geoCallback = cb;
            geoOrigin = origin;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_GEO);
        }

        @Override
        public void onPermissionRequest(final PermissionRequest r) {
            runOnUiThread(() -> {
                boolean wantsCamera = false;
                for (String res : r.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) wantsCamera = true;
                if (!wantsCamera) { r.deny(); return; }
                if (has(Manifest.permission.CAMERA)) { r.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE}); return; }
                pagePermission = r;
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PAGE);
            });
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = cb;
            fileParams = params;
            if (params.isCaptureEnabled() && acceptsImage(params)) {
                if (has(Manifest.permission.CAMERA)) openCamera();
                else requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_FILE);
            } else {
                openPicker(params);
            }
            return true;
        }

        // target="_blank" / window.open: catch the address and open it outside the app
        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean userGesture, Message resultMsg) {
            WebView temp = new WebView(MainActivity.this);
            temp.setWebViewClient(new WebViewClient() {
                private boolean done;

                private void take(WebView v, Uri u) {
                    if (done || u == null || "about".equals(u.getScheme())) return;
                    done = true;
                    handleNewWindow(u);
                    v.stopLoading();
                    v.post(v::destroy);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                    take(v, req.getUrl());
                    return true;
                }

                @Override
                public void onPageStarted(WebView v, String url, Bitmap favicon) {
                    if (url != null) take(v, Uri.parse(url));
                }
            });
            ((WebView.WebViewTransport) resultMsg.obj).setWebView(temp);
            resultMsg.sendToTarget();
            return true;
        }
    }

    private void handleNewWindow(Uri u) {
        String h = u.getHost();
        // the app's own address in a new window: just keep using this window
        if ("https".equals(u.getScheme()) && h != null && (h.equals("script.google.com") || h.endsWith(".googleusercontent.com"))) return;
        openOutside(u);
    }

    private static boolean acceptsImage(WebChromeClient.FileChooserParams p) {
        for (String t : p.getAcceptTypes()) if (t != null && t.startsWith("image")) return true;
        return false;
    }

    // ".xlsx" / "image/*" -> MIME types for the Android file picker
    private static String[] mimeTypes(WebChromeClient.FileChooserParams p) {
        List<String> out = new ArrayList<>();
        for (String raw : p.getAcceptTypes()) {
            if (raw == null) continue;
            for (String t : raw.split(",")) {
                t = t.trim().toLowerCase();
                if (t.isEmpty()) continue;
                if (t.equals(".xlsx")) out.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                else if (t.equals(".xls")) out.add("application/vnd.ms-excel");
                else if (t.equals(".csv")) { out.add("text/csv"); out.add("text/comma-separated-values"); }
                else if (t.contains("/")) out.add(t);
            }
        }
        if (out.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) out.add("application/octet-stream");  // some phones save Excel like this
        return out.toArray(new String[0]);
    }

    private void openPicker(WebChromeClient.FileChooserParams params) {
        String[] types = mimeTypes(params);
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if (types.length == 1) i.setType(types[0]);
        else {
            i.setType("*/*");
            if (types.length > 1) i.putExtra(Intent.EXTRA_MIME_TYPES, types);
        }
        try {
            startActivityForResult(Intent.createChooser(i, "Choose file"), REQ_FILE);
        } catch (ActivityNotFoundException e) {
            finishFile(null);
            toast("No file manager found.");
        }
    }

    private void openCamera() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "photo_" + System.currentTimeMillis() + ".jpg");
            cameraPhoto = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhoto);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_FILE);
        } catch (Exception e) {
            cameraPhoto = null;
            if (fileParams != null) openPicker(fileParams); else finishFile(null);
        }
    }

    private void finishFile(Uri[] result) {
        if (fileCallback != null) fileCallback.onReceiveValue(result);
        fileCallback = null;
        fileParams = null;
        cameraPhoto = null;
    }

    @Override
    protected void onActivityResult(int req, int code, Intent data) {
        super.onActivityResult(req, code, data);
        if (req != REQ_FILE) return;
        if (code != RESULT_OK) { finishFile(null); return; }
        if (cameraPhoto != null) { finishFile(new Uri[]{cameraPhoto}); return; }
        Uri[] r = WebChromeClient.FileChooserParams.parseResult(code, data);
        if ((r == null || r.length == 0) && data != null && data.getData() != null) r = new Uri[]{data.getData()};
        finishFile(r);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        boolean ok = false;
        for (int r : res) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
        if (req == REQ_GEO && geoCallback != null) {
            geoCallback.invoke(geoOrigin, ok, false);
            geoCallback = null;
            if (!ok) toast("Location is required. Please allow Location for Image DSR in phone Settings.");
        } else if (req == REQ_CAMERA_PAGE && pagePermission != null) {
            if (ok) pagePermission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else pagePermission.deny();
            pagePermission = null;
        } else if (req == REQ_CAMERA_FILE) {
            if (ok) openCamera();
            else if (fileParams != null) openPicker(fileParams);
            else finishFile(null);
        }
    }

    // ---------------------------------------------------------------------
    // Save / share files (Excel, DSR image)
    // ---------------------------------------------------------------------
    private static String safeName(String name) {
        String n = (name == null ? "" : name).replaceAll("[\\\\/:*?\"<>|\\n\\r]+", "_").trim();
        return n.isEmpty() ? "file" : n;
    }

    private static byte[] decode(String b64) {
        String s = b64 == null ? "" : b64;
        int comma = s.indexOf(',');
        if (s.startsWith("data:") && comma > 0) s = s.substring(comma + 1);
        return Base64.decode(s, Base64.DEFAULT);
    }

    void saveFile(String b64, String name, String mime) {
        String n = safeName(name);
        String m = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
        try {
            byte[] bytes = decode(b64);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, n);
                v.put(MediaStore.MediaColumns.MIME_TYPE, m);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Image DSR");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new Exception("Could not save");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new Exception("Could not save");
                    os.write(bytes);
                }
                toast("Saved in Downloads > Image DSR: " + n);
                openFile(uri, m);
            } else {
                File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Image DSR");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, n);
                try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
                share(uri, m, n, "Save / send " + n);         // older Android: let the person keep it where he wants
            }
        } catch (Exception e) {
            toast("Could not save the file: " + e.getMessage());
        }
    }

    private void openFile(Uri uri, String mime) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { }                 // no viewer installed: file is still in Downloads
    }

    void shareFile(String b64, String name, String mime, String text) {
        String n = safeName(name);
        try {
            File dir = new File(getCacheDir(), "share");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, n);
            try (FileOutputStream os = new FileOutputStream(f)) { os.write(decode(b64)); }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            share(uri, mime == null || mime.isEmpty() ? "application/octet-stream" : mime, text, "Share " + n);
        } catch (Exception e) {
            toast("Could not share: " + e.getMessage());
        }
    }

    private void share(Uri uri, String mime, String text, String title) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        if (text != null && !text.isEmpty()) i.putExtra(Intent.EXTRA_TEXT, text);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, title));
    }

    void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    // ---------------------------------------------------------------------
    // window.DSRNative.* for the web app (only small, safe actions)
    // ---------------------------------------------------------------------
    private class Bridge {
        @JavascriptInterface
        public String getPushToken() {
            String t = PushService.token(MainActivity.this);
            if (t.isEmpty()) runOnUiThread(MainActivity.this::refreshPushToken);
            return t;
        }

        @JavascriptInterface
        public String appVersion() { return BuildConfig.VERSION_NAME; }

        @JavascriptInterface
        public void saveFile(String b64, String name, String mime) {
            if (b64 == null || b64.length() > 40_000_000) { toast("File is too large."); return; }
            runOnUiThread(() -> MainActivity.this.saveFile(b64, name, mime));
        }

        @JavascriptInterface
        public void shareFile(String b64, String name, String mime, String text) {
            if (b64 == null || b64.length() > 40_000_000) { toast("File is too large."); return; }
            runOnUiThread(() -> MainActivity.this.shareFile(b64, name, mime, text));
        }

        @JavascriptInterface
        public void openUrl(String url) {
            if (url == null) return;
            Uri u = Uri.parse(url);
            String sc = u.getScheme();
            if ("https".equals(sc) || "http".equals(sc) || "whatsapp".equals(sc) || "tel".equals(sc) || "mailto".equals(sc)) {
                runOnUiThread(() -> openOutside(u));
            }
        }
    }
}
