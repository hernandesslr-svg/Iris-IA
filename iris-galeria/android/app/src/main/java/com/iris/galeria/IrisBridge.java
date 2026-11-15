package com.iris.galeria;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/** Ponte entre a página local (assets) e o Android: fotos, senha e atualização do app. */
public class IrisBridge {
    private static final String RELEASE_API =
            "https://api.github.com/repos/hernandesslr-svg/Iris-IA/releases/tags/galeria-latest";
    private static final String ALLOWED_PREFIX = "https://github.com/hernandesslr-svg/";

    private final MainActivity activity;
    private final Context context;
    private final WebView webView;
    private volatile String updateUrl = "";

    public IrisBridge(MainActivity activity, WebView webView) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
        this.webView = webView;
    }

    /* ------------------------------ fotos ------------------------------ */

    /** Devolve o conteúdo de um arquivo de assets em Base64, ou "" se falhar. */
    @JavascriptInterface
    public String readAsset(String path) {
        byte[] data = readAssetBytes(path);
        return data == null ? "" : Base64.encodeToString(data, Base64.NO_WRAP);
    }

    /** Salva uma foto que vem dos assets no celular. Devolve o local, ou "" se falhar. */
    @JavascriptInterface
    public String saveAsset(String path, String name) {
        byte[] data = readAssetBytes(path);
        if (data == null) return "";
        String lower = path.toLowerCase();
        String ext = lower.endsWith(".webp") ? ".webp" : lower.endsWith(".png") ? ".png" : ".jpg";
        String mime = ext.equals(".webp") ? "image/webp" : ext.equals(".png") ? "image/png" : "image/jpeg";
        return write(data, cleanName(name) + ext, mime);
    }

    /** Salva uma foto enviada em Base64 (fotos importadas ou editadas). */
    @JavascriptInterface
    public String saveImage(String base64, String name, String mime) {
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            String type = mime == null || mime.isEmpty() ? "image/jpeg" : mime;
            String ext = type.equals("image/webp") ? ".webp" : type.equals("image/png") ? ".png" : ".jpg";
            return write(data, cleanName(name) + ext, type);
        } catch (Exception e) {
            return "";
        }
    }

    private byte[] readAssetBytes(String path) {
        try (InputStream in = context.getAssets().open(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", " ").trim();
        return n.isEmpty() ? "Iris Galeria" : n;
    }

    private String write(byte[] data, String fileName, String mime) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver resolver = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, mime);
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Iris Galeria");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return "";
                try (OutputStream out = resolver.openOutputStream(uri)) {
                    if (out == null) return "";
                    out.write(data);
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
                return "Imagens/Iris Galeria";
            }
            File dir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Iris Galeria");
            if (!dir.exists() && !dir.mkdirs()) return "";
            File file = new File(dir, fileName);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(data);
            }
            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, new String[]{mime}, null);
            return file.getParent();
        } catch (Exception e) {
            return "";
        }
    }

    /* ------------------------------ bloqueio ------------------------------ */

    @JavascriptInterface
    public boolean lockSupported() { return activity.isDeviceSecure(); }

    @JavascriptInterface
    public boolean lockEnabled() { return activity.isLockEnabled(); }

    @JavascriptInterface
    public boolean isLocked() { return activity.isLockedNow(); }

    /** purpose: "unlock", "enable" ou "disable"; usa a senha, padrão ou digital do celular. */
    @JavascriptInterface
    public void requestAuth(String purpose) { activity.requestAuth(purpose); }

    /* ---------------------------- atualização ---------------------------- */

    @JavascriptInterface
    public String versionName() {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private long versionCode() {
        try {
            android.content.pm.PackageInfo info =
                    context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private void notifyJs(String status, String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("status", status);
            o.put("msg", msg == null ? "" : msg);
            final String js = "window.irisUpdate&&window.irisUpdate(" + o.toString() + ")";
            webView.post(() -> webView.evaluateJavascript(js, null));
        } catch (Exception ignored) {
        }
    }

    /** Procura uma versão nova no GitHub. O resultado volta em window.irisUpdate(...). */
    @JavascriptInterface
    public void checkUpdate() {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "IrisGaleria");
                int code = c.getResponseCode();
                if (code != 200) {
                    notifyJs(code == 404 ? "none" : "error", "HTTP " + code);
                    return;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }
                JSONObject json = new JSONObject(out.toString("UTF-8"));
                Matcher m = Pattern.compile("versionCode=(\\d+)").matcher(json.optString("body"));
                long remote = m.find() ? Long.parseLong(m.group(1)) : 0;
                String url = "";
                JSONArray assets = json.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.optString("name").endsWith(".apk")) {
                            url = a.optString("browser_download_url");
                            break;
                        }
                    }
                }
                if (remote > versionCode() && url.startsWith(ALLOWED_PREFIX)) {
                    updateUrl = url;
                    String name = json.optString("name");
                    notifyJs("available", name.isEmpty() ? String.valueOf(remote) : name);
                } else {
                    notifyJs("none", "");
                }
            } catch (Exception e) {
                notifyJs("error", e.getClass().getSimpleName());
            }
        }).start();
    }

    /** Baixa o APK novo e abre o instalador do Android por cima do app atual. */
    @JavascriptInterface
    public void installUpdate() {
        if (updateUrl.isEmpty()) {
            checkUpdate();
            return;
        }
        activity.runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= 26 && !context.getPackageManager().canRequestPackageInstalls()) {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.getPackageName()));
                activity.startActivity(i);
                notifyJs("permission", "");
                return;
            }
            startDownload();
        });
    }

    private void startDownload() {
        try {
            File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File old = new File(dir, "Iris-Galeria-update.apk");
            if (old.exists()) old.delete();
            final DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(updateUrl));
            r.setTitle("Iris Galeria");
            r.setDescription("Baixando atualização");
            r.setMimeType("application/vnd.android.package-archive");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "Iris-Galeria-update.apk");
            final long id = dm.enqueue(r);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                    try {
                        context.unregisterReceiver(this);
                    } catch (Exception ignored) {
                    }
                    Uri uri = dm.getUriForDownloadedFile(id);
                    if (uri == null) {
                        notifyJs("error", "download");
                        return;
                    }
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(uri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(install);
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            notifyJs("downloading", "");
        } catch (Exception e) {
            notifyJs("error", e.getClass().getSimpleName());
        }
    }
}
