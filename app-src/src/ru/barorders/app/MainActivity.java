package ru.barorders.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Handler;
import android.os.Looper;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_EXPORT = 1, REQ_IMPORT = 2;
    private WebView web;
    private SharedPreferences prefs;
    private String pendingExport;
    private boolean night;
    private static final String UPDATE_BASE = "https://raw.githubusercontent.com/pzrk8/bar-orders/main/";
    private static final int APP_CODE = 3;
    private int loadedBuild = 0;
    private boolean uiConfirmed = false;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("store", MODE_PRIVATE);
        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int bg = Color.parseColor(night ? "#0F1512" : "#F2F4F1");
        Window w = getWindow();
        w.setStatusBarColor(bg);
        w.setNavigationBarColor(Color.parseColor(night ? "#18201C" : "#FFFFFF"));
        if (!night) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
        web = new WebView(this);
        web.setBackgroundColor(bg);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHorizontalScrollBarEnabled(false);
        web.addJavascriptInterface(new Bridge(), "AppStore");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        loadUi();
        checkForUpdate();
    }

    /* ---------- interface updates from GitHub ---------- */
    private File uiDir() { File d = new File(getFilesDir(), "ui"); d.mkdirs(); return d; }
    private int readInt(File f) { try { return Integer.parseInt(readAll(new FileInputStream(f)).trim()); } catch (Exception e) { return 0; } }
    private int bundledBuild() { try { return Integer.parseInt(readAll(getAssets().open("ui_build.txt")).trim()); } catch (Exception e) { return 0; } }
    private static String readAll(InputStream is) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        try { while ((n = is.read(buf)) > 0) bo.write(buf, 0, n); } finally { is.close(); }
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }
    private void writeText(File f, String text) throws java.io.IOException {
        File tmp = new File(f.getPath() + ".tmp");
        FileOutputStream fo = new FileOutputStream(tmp);
        fo.write(text.getBytes(StandardCharsets.UTF_8)); fo.getFD().sync(); fo.close();
        if (!tmp.renameTo(f)) throw new java.io.IOException("rename");
    }

    private void loadUi() {
        int bundled = bundledBuild();
        File html = new File(uiDir(), "index.html");
        int local = html.exists() ? readInt(new File(uiDir(), "build.txt")) : 0;
        int bad = readInt(new File(uiDir(), "bad.txt"));
        uiConfirmed = false;
        if (local > bundled && local != bad) {
            try {
                String text = readAll(new FileInputStream(html));
                loadedBuild = local;
                web.loadDataWithBaseURL("file:///android_asset/", text, "text/html", "utf-8", null);
                main.postDelayed(new Runnable() { public void run() {
                    if (!uiConfirmed && loadedBuild > bundledBuild()) {
                        try { writeText(new File(uiDir(), "bad.txt"), String.valueOf(loadedBuild)); } catch (Exception ignored) { }
                        loadedBuild = bundledBuild();
                        web.loadUrl("file:///android_asset/index.html");
                    }
                }}, 10000);
                return;
            } catch (Exception ignored) { }
        }
        loadedBuild = bundled;
        web.loadUrl("file:///android_asset/index.html");
    }

    private void checkForUpdate() {
        new Thread(new Runnable() { public void run() {
            try {
                JSONObject v = new JSONObject(fetch(UPDATE_BASE + "version.json?t=" + System.currentTimeMillis()));
                int build = v.optInt("build", 0);
                int minApp = v.optInt("min_app", 0);
                File dir = uiDir();
                int have = Math.max(bundledBuild(), new File(dir, "index.html").exists() ? readInt(new File(dir, "build.txt")) : 0);
                if (build <= have || minApp > APP_CODE || build == readInt(new File(dir, "bad.txt"))) return;
                String html = fetch(UPDATE_BASE + "index.html?t=" + System.currentTimeMillis());
                if (html.length() < 20000 || !html.contains("BARORDERS_UI") || !html.contains("ui-build:" + build)) return;
                writeText(new File(dir, "index.html"), html);
                writeText(new File(dir, "build.txt"), String.valueOf(build));
                final String notes = v.optString("notes", "");
                js("window.__updateReady&&window.__updateReady(" + JSONObject.quote(notes) + ")");
            } catch (Exception ignored) { }
        }}).start();
    }

    private static String fetch(String url) throws java.io.IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache");
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("http " + c.getResponseCode());
            return readAll(c.getInputStream());
        } finally { c.disconnect(); }
    }

    private void js(String code) { runOnUiThread(() -> web.evaluateJavascript(code, null)); }

    @Override
    protected void onPause() {
        if (web != null) web.evaluateJavascript("window.__flushAll&&window.__flushAll()", null);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__back?String(window.__back()):'false'", v -> {
            if (v == null || !v.contains("true")) finish();
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_EXPORT) {
            if (res != RESULT_OK || data == null || data.getData() == null || pendingExport == null) { pendingExport = null; return; }
            try (OutputStream os = getContentResolver().openOutputStream(data.getData(), "wt")) {
                os.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                js("window.__backupDone&&window.__backupDone(true)");
            } catch (Exception e) {
                js("window.__backupDone&&window.__backupDone(false)");
            }
            pendingExport = null;
        } else if (req == REQ_IMPORT) {
            if (res != RESULT_OK || data == null || data.getData() == null) return;
            try (InputStream is = getContentResolver().openInputStream(data.getData())) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                String text = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                js("window.__importBackup&&window.__importBackup(" + JSONObject.quote(text) + ")");
            } catch (Exception e) {
                js("window.__importBackup&&window.__importBackup('')");
            }
        }
    }

    class Bridge {
        @JavascriptInterface public String get(String key) { return prefs.getString(key, null); }
        @JavascriptInterface public void set(String key, String value) { prefs.edit().putString(key, value).commit(); }
        @JavascriptInterface public boolean isNight() { return night; }
        @JavascriptInterface public void uiReady() { uiConfirmed = true; }
        @JavascriptInterface public void applyUpdate() {
            runOnUiThread(new Runnable() { public void run() { web.evaluateJavascript("window.__flushAll&&window.__flushAll()", null); loadUi(); } });
        }
        @JavascriptInterface public boolean copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("order", text));
            return true;
        }
        @JavascriptInterface public void exportBackup(String json, String name) {
            pendingExport = json;
            final String fname = name;
            runOnUiThread(new Runnable() { public void run() {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, fname);
                try { startActivityForResult(i, REQ_EXPORT); } catch (Exception e) { js("window.__backupDone&&window.__backupDone(false)"); }
            }});
        }
        @JavascriptInterface public void shareBackup(String json, String name) {
            try {
                File dir = new File(getCacheDir(), "share");
                dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) for (File f : old) f.delete();
                File f = new File(dir, name);
                FileOutputStream fo = new FileOutputStream(f);
                fo.write(json.getBytes(StandardCharsets.UTF_8));
                fo.close();
                final Uri uri = Uri.parse("content://" + ShareProvider.AUTHORITY + "/" + name);
                runOnUiThread(new Runnable() { public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("application/json");
                    i.putExtra(Intent.EXTRA_STREAM, uri);
                    i.setClipData(ClipData.newRawUri("", uri));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent c = Intent.createChooser(i, "Отправить файл настроек");
                    c.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try { startActivity(c); } catch (Exception e) { js("window.__backupDone&&window.__backupDone(false)"); }
                }});
            } catch (Exception e) {
                js("window.__backupDone&&window.__backupDone(false)");
            }
        }
        @JavascriptInterface public void importBackup() {
            runOnUiThread(new Runnable() { public void run() {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(i, REQ_IMPORT); } catch (Exception ignored) { }
            }});
        }
    }
}
