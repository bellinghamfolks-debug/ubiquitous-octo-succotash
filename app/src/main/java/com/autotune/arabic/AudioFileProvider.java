package com.autotune.arabic;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * ContentProvider بسيط لمشاركة ملفات WAV مع تطبيقات أخرى (WhatsApp، إلخ).
 * مطلوب على Android 7+ لأن file:// URIs محظورة في الـ intents.
 */
public class AudioFileProvider extends ContentProvider {

    static final String AUTHORITY = "com.autotune.arabic.files";

    static Uri uriForFile(File f) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .encodedPath(Uri.encode(f.getAbsolutePath(), "/"))
                .build();
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = new File(uri.getPath());
        if (!f.exists()) throw new FileNotFoundException(uri.getPath());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        File f = new File(uri.getPath());
        MatrixCursor c = new MatrixCursor(
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override public String getType(Uri uri) { return "audio/wav"; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
