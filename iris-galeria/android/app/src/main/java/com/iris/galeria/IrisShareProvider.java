package com.iris.galeria;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Entrega ao seletor de compartilhamento do Android as fotos copiadas para a pasta temporária. */
public class IrisShareProvider extends ContentProvider {
    public static final String AUTHORITY = "com.iris.galeria.share";

    public static File dir(Context context) {
        File d = new File(context.getCacheDir(), "share");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        try {
            File base = dir(getContext()).getCanonicalFile();
            File f = new File(base, uri.getLastPathSegment()).getCanonicalFile();
            if (!f.getPath().startsWith(base.getPath() + File.separator) || !f.exists()) {
                throw new FileNotFoundException();
            }
            return f;
        } catch (java.io.IOException e) {
            throw new FileNotFoundException();
        }
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File f = fileFor(uri);
            String[] cols = projection != null ? projection
                    : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor c = new MatrixCursor(cols);
            Object[] row = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                row[i] = OpenableColumns.DISPLAY_NAME.equals(cols[i]) ? f.getName()
                        : OpenableColumns.SIZE.equals(cols[i]) ? (Object) f.length() : null;
            }
            c.addRow(row);
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override public String getType(Uri uri) {
        String n = String.valueOf(uri.getLastPathSegment()).toLowerCase();
        return n.endsWith(".webp") ? "image/webp" : n.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
