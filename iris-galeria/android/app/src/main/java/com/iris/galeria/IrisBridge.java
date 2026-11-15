package com.iris.galeria;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
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
import java.util.ArrayList;
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
        try (InputStream in = new java.io.ByteArrayInputStream(data)) {
            return writeStream(in, fileName, mime);
        } catch (Exception e) {
            return "";
        }
    }

    /** Grava foto ou vídeo na galeria do celular (Imagens ou Filmes / Iris Galeria). */
    private String writeStream(InputStream in, String fileName, String mime) {
        boolean video = mime != null && mime.startsWith("video/");
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver resolver = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES) + "/Iris Galeria");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = resolver.insert(video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return "";
                try (OutputStream out = resolver.openOutputStream(uri)) {
                    if (out == null) return "";
                    copy(in, out);
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, values, null, null);
                return video ? "Vídeos/Iris Galeria" : "Imagens/Iris Galeria";
            }
            File dir = new File(context.getExternalFilesDir(video ? Environment.DIRECTORY_MOVIES
                    : Environment.DIRECTORY_PICTURES), "Iris Galeria");
            if (!dir.exists() && !dir.mkdirs()) return "";
            File file = new File(dir, fileName);
            try (FileOutputStream out = new FileOutputStream(file)) {
                copy(in, out);
            }
            MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, new String[]{mime}, null);
            return file.getParent();
        } catch (Exception e) {
            return "";
        }
    }

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    /* ------------- arquivos grandes (vídeos) enviados em pedaços ------------- */

    private File streamFile;
    private OutputStream streamOut;

    private void streamClose() {
        try {
            if (streamOut != null) streamOut.close();
        } catch (Exception ignored) {
        }
        streamOut = null;
    }

    @JavascriptInterface
    public synchronized boolean streamBegin() {
        try {
            streamClose();
            if (streamFile != null) streamFile.delete();
            streamFile = File.createTempFile("up", ".bin", context.getCacheDir());
            streamOut = new FileOutputStream(streamFile);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public synchronized boolean streamChunk(String base64) {
        try {
            if (streamOut == null) return false;
            streamOut.write(Base64.decode(base64, Base64.DEFAULT));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String extForMime(String mime) {
        if (mime == null) return ".jpg";
        switch (mime) {
            case "image/webp": return ".webp";
            case "image/png": return ".png";
            case "image/jpeg": return ".jpg";
            case "video/webm": return ".webm";
            case "video/3gpp": return ".3gp";
            case "video/quicktime": return ".mov";
            case "video/x-matroska": return ".mkv";
            default: return mime.startsWith("video/") ? ".mp4" : ".jpg";
        }
    }

    /** Salva no celular o arquivo enviado em pedaços. Devolve o local, ou "" se falhar. */
    @JavascriptInterface
    public synchronized String streamSave(String name, String mime) {
        try {
            streamClose();
            if (streamFile == null) return "";
            try (InputStream in = new java.io.FileInputStream(streamFile)) {
                return writeStream(in, cleanName(name) + extForMime(mime), mime);
            }
        } catch (Exception e) {
            return "";
        } finally {
            if (streamFile != null) streamFile.delete();
            streamFile = null;
        }
    }

    /** Coloca o arquivo enviado em pedaços na lista de compartilhamento. */
    @JavascriptInterface
    public synchronized boolean streamShare(String name, String mime) {
        try {
            streamClose();
            if (streamFile == null) return false;
            File dir = IrisShareProvider.dir(context);
            String fileName = cleanName(name) + extForMime(mime);
            File f = new File(dir, fileName);
            int k = 2;
            while (f.exists()) {
                int dot = fileName.lastIndexOf('.');
                f = new File(dir, fileName.substring(0, dot) + " (" + k++ + ")" + fileName.substring(dot));
            }
            try (InputStream in = new java.io.FileInputStream(streamFile); FileOutputStream out = new FileOutputStream(f)) {
                copy(in, out);
            }
            shareUris.add(new Uri.Builder().scheme("content")
                    .authority(IrisShareProvider.AUTHORITY).appendPath(f.getName()).build());
            if (mime != null && mime.startsWith("video/")) hasVid = true; else hasImg = true;
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (streamFile != null) streamFile.delete();
            streamFile = null;
        }
    }

    /* ---------------------------- compartilhar ---------------------------- */

    private final ArrayList<Uri> shareUris = new ArrayList<>();
    private boolean hasVid = false, hasImg = false;

    /** Começa um novo compartilhamento: limpa a pasta temporária. */
    @JavascriptInterface
    public synchronized void shareBegin() {
        shareUris.clear();
        hasVid = false;
        hasImg = false;
        File[] old = IrisShareProvider.dir(context).listFiles();
        if (old != null) for (File f : old) f.delete();
    }

    @JavascriptInterface
    public synchronized boolean shareAsset(String path, String name) {
        byte[] data = readAssetBytes(path);
        if (data == null) return false;
        String lower = path.toLowerCase();
        String ext = lower.endsWith(".webp") ? ".webp" : lower.endsWith(".png") ? ".png" : ".jpg";
        hasImg = true;
        return addShare(data, cleanName(name) + ext);
    }

    @JavascriptInterface
    public synchronized boolean shareImage(String base64, String name, String mime) {
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            String ext = "image/webp".equals(mime) ? ".webp" : "image/png".equals(mime) ? ".png" : ".jpg";
            hasImg = true;
            return addShare(data, cleanName(name) + ext);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean addShare(byte[] data, String fileName) {
        try {
            File dir = IrisShareProvider.dir(context);
            File f = new File(dir, fileName);
            int k = 2;
            while (f.exists()) {
                int dot = fileName.lastIndexOf('.');
                f = new File(dir, fileName.substring(0, dot) + " (" + k++ + ")" + fileName.substring(dot));
            }
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(data);
            }
            shareUris.add(new Uri.Builder().scheme("content")
                    .authority(IrisShareProvider.AUTHORITY).appendPath(f.getName()).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Abre o seletor de compartilhamento do Android com as fotos adicionadas. */
    @JavascriptInterface
    public synchronized boolean shareSend() {
        if (shareUris.isEmpty()) return false;
        final ArrayList<Uri> uris = new ArrayList<>(shareUris);
        final String type = hasVid && hasImg ? "*/*" : hasVid ? "video/*" : "image/*";
        activity.runOnUiThread(() -> {
            try {
                Intent i = uris.size() == 1 ? new Intent(Intent.ACTION_SEND) : new Intent(Intent.ACTION_SEND_MULTIPLE);
                i.setType(type);
                if (uris.size() == 1) i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                else i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                ClipData clip = ClipData.newRawUri("", uris.get(0));
                for (int n = 1; n < uris.size(); n++) clip.addItem(new ClipData.Item(uris.get(n)));
                i.setClipData(clip);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser = Intent.createChooser(i, "Compartilhar");
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.markExternal();
                activity.startActivity(chooser);
            } catch (Exception ignored) {
            }
        });
        return true;
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
