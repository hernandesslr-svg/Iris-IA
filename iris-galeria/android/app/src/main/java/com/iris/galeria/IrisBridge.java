package com.iris.galeria;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Ponte entre a página local (assets) e o Android: ler assets e salvar fotos no celular. */
public class IrisBridge {
    private final Context context;

    public IrisBridge(Context context) {
        this.context = context.getApplicationContext();
    }

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
}
