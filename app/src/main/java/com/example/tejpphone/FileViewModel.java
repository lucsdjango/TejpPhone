package com.example.tejpphone;

import android.app.Application;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.TjpFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads and writes .tjp files off the main thread, so a slow cloud provider such as OneDrive
 * cannot freeze the app. Lives across screen rotation, so a result is not lost mid-rotation.
 */
public class FileViewModel extends AndroidViewModel {

    /** Larger than any real drawing. Guards against picking a huge unrelated file. */
    private static final int MAX_FILE_BYTES = 50 * 1024 * 1024;

    public static final class Result {
        public enum Kind { LOADED, SAVED, SAVE_NEEDS_NEW_FILE, ERROR }

        public final Kind kind;
        public final Fence fence;
        public final Uri uri;
        public final String name;
        public final String message;
        private boolean handled = false;

        Result(Kind kind, Fence fence, Uri uri, String name, String message) {
            this.kind = kind;
            this.fence = fence;
            this.uri = uri;
            this.name = name;
            this.message = message;
        }

        /** Returns false if this result was already shown, for example before a rotation. */
        public boolean markHandled() {
            if (handled) return false;
            handled = true;
            return true;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Result> results = new MutableLiveData<>();

    public FileViewModel(@NonNull Application app) {
        super(app);
    }

    public LiveData<Result> getResults() {
        return results;
    }

    public void load(Uri uri) {
        executor.execute(() -> {
            ContentResolver cr = getApplication().getContentResolver();
            String name = displayName(cr, uri);
            try (InputStream in = cr.openInputStream(uri)) {
                if (in == null) throw new IOException("Could not open the file");
                Fence fence = TjpFile.read(readAll(in));
                results.postValue(new Result(Result.Kind.LOADED, fence, uri, name, null));
            } catch (IOException | SecurityException e) {
                results.postValue(new Result(Result.Kind.ERROR, null, uri, name,
                        "Could not load " + name + ": " + e.getMessage()));
            }
        });
    }

    /**
     * Writes the data to uri. When overwriting the current file fails, for example because
     * the permission has expired, the result asks the activity to pick a new file instead.
     */
    public void save(Uri uri, byte[] data, boolean overwrite) {
        executor.execute(() -> {
            ContentResolver cr = getApplication().getContentResolver();
            String name = displayName(cr, uri);
            // "wt" truncates. Plain "w" may leave old bytes at the end on some providers,
            // which would make the file unreadable.
            try (OutputStream out = cr.openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Could not open the file");
                out.write(data);
                results.postValue(new Result(Result.Kind.SAVED, null, uri, name, null));
            } catch (IOException | SecurityException | IllegalArgumentException
                     | UnsupportedOperationException e) {
                if (overwrite) {
                    results.postValue(new Result(Result.Kind.SAVE_NEEDS_NEW_FILE, null, uri, name, null));
                } else {
                    results.postValue(new Result(Result.Kind.ERROR, null, uri, name,
                            "Could not save " + name + ": " + e.getMessage()));
                }
            }
        });
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[64 * 1024];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
            if (buf.size() > MAX_FILE_BYTES) throw new IOException("File is too large");
        }
        return buf.toByteArray();
    }

    private static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (RuntimeException ignored) {
            // Fall through to the last path segment.
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "file";
    }

    @Override
    protected void onCleared() {
        executor.shutdown();
    }
}
