package com.example.tejpphone;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.example.tejpphone.function.History;
import com.example.tejpphone.function.State;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Picture;
import com.example.tejpphone.model.TjpFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the drawing and settings when Android closes the app in the background.
 *
 * <p>The drawing is written to app storage as a .tjp file whenever the app leaves the screen.
 * A picture loaded from the gallery is also kept at full size, since .tjp files only hold a
 * 300 x 300 copy. Settings such as width, colour and custom colours are kept as preferences.
 */
final class Autosave {

    private Autosave() {}

    private static final String TAG = "Autosave";
    private static final String DRAWING = "autosave.tjp";
    private static final String PICTURE = "autosave_picture.png";
    private static final String PREFS = "tejp";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** Pixels last written to PICTURE, so an unchanged picture is not written again. */
    private static int[] savedPicturePixels;

    /** Writes the drawing and settings. Called when the app goes to the background. */
    static void save(Context context) {
        if (State.fence == null) return;
        Context app = context.getApplicationContext();
        SharedPreferences.Editor prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        saveSettings(prefs);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            TjpFile.write(State.fence, out);
            writeAtomically(new File(app.getFilesDir(), DRAWING), out.toByteArray());
        } catch (IOException e) {
            Log.w(TAG, "Could not autosave the drawing", e);
            return;
        }

        prefs.putString("fileUri", State.fileUri);
        prefs.putString("fileName", State.fileName);
        prefs.putBoolean("dirty", History.isDirty());

        // A full-size picture from the gallery, written in the background as it can be large.
        Picture p = State.fence.getPicture();
        boolean fullPicture = p != null && p.hasPixels() && !p.isFromTjp();
        prefs.putBoolean("fullPicture", fullPicture);
        File pictureFile = new File(app.getFilesDir(), PICTURE);
        if (fullPicture && savedPicturePixels != p.getPixels()) {
            int[] px = p.getPixels();
            int w = p.getWidth(), h = p.getHeight();
            savedPicturePixels = px;
            executor.execute(() -> {
                Bitmap bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
                try (FileOutputStream fos = new FileOutputStream(pictureFile)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                } catch (IOException e) {
                    Log.w(TAG, "Could not autosave the picture", e);
                } finally {
                    bmp.recycle();
                }
            });
        } else if (!fullPicture) {
            savedPicturePixels = null;
            //noinspection ResultOfMethodCallIgnored
            pictureFile.delete();
        }
        prefs.apply();
    }

    /**
     * Restores settings, and the drawing if there is one. Call only when the app process starts,
     * that is when State.fence is still null.
     * @return true if a drawing was restored
     */
    static boolean restore(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        restoreSettings(prefs);

        File drawing = new File(app.getFilesDir(), DRAWING);
        if (!drawing.exists()) return false;
        Fence fence;
        try (InputStream in = new FileInputStream(drawing)) {
            byte[] data = new byte[(int) drawing.length()];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
            fence = TjpFile.read(data);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not restore the autosave", e);
            return false;
        }

        Picture p = fence.getPicture();
        File pictureFile = new File(app.getFilesDir(), PICTURE);
        if (p != null && prefs.getBoolean("fullPicture", false) && pictureFile.exists()) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bmp = BitmapFactory.decodeFile(pictureFile.getPath(), o);
            if (bmp != null) {
                int w = bmp.getWidth(), h = bmp.getHeight();
                int[] px = new int[w * h];
                bmp.getPixels(px, 0, w, 0, 0, w, h);
                bmp.recycle();
                double[] c = p.getCorners();
                fence.setPicture(Picture.fromPixels(w, h, px, c[0], c[1], c[2], c[3]));
                savedPicturePixels = px;
            }
        }

        State.fence = fence;
        State.fileUri = prefs.getString("fileUri", null);
        State.fileName = prefs.getString("fileName", null);
        History.clear();
        if (prefs.getBoolean("dirty", false)) History.markDirty();
        return true;
    }

    /** The see-through background for Meta Quest passthrough. Read before the activity is created. */
    static boolean transparentBackground(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("transparentBackground", false);
    }

    static void setTransparentBackground(Context context, boolean transparent) {
        context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("transparentBackground", transparent).apply();
    }

    private static void saveSettings(SharedPreferences.Editor p) {
        p.putFloat("width", State.currentStripWidth);
        p.putInt("color", State.currentColor);
        StringBuilder custom = new StringBuilder();
        for (int c : State.customColors) {
            if (custom.length() > 0) custom.append(',');
            custom.append(Integer.toHexString(c));
        }
        p.putString("customColors", custom.toString());
        p.putString("mode", State.getMode().name());
        p.putBoolean("inverted", State.inverted);
        p.putBoolean("showingPic", State.showingPic);
        p.putFloat("pictureOpacity", State.pictureOpacity);
        p.putBoolean("keepAspect", State.keepPictureAspect);
        p.putBoolean("boxSelect", State.boxSelect);
        p.putBoolean("autoPanelOpen", State.autoPanelOpen);
        p.putString("autoDirection", State.autoDirection.name());
        for (int i = 0; i < 3; i++) {
            p.putFloat("threshold" + i, State.autoThreshold[i]);
            p.putFloat("contrast" + i, State.autoContrast[i]);
        }
    }

    private static void restoreSettings(SharedPreferences p) {
        State.currentStripWidth = p.getFloat("width", State.currentStripWidth);
        State.currentColor = p.getInt("color", State.currentColor);
        State.customColors.clear();
        for (String hex : p.getString("customColors", "").split(",")) {
            if (hex.isEmpty()) continue;
            try {
                State.customColors.add((int) Long.parseLong(hex, 16));
            } catch (NumberFormatException ignored) {
                // Skip a damaged entry.
            }
        }
        try {
            State.setMode(State.Mode.valueOf(p.getString("mode", State.getMode().name())));
            State.autoDirection = State.AutoDirection.valueOf(
                    p.getString("autoDirection", State.autoDirection.name()));
        } catch (IllegalArgumentException ignored) {
            // Keep the defaults.
        }
        State.inverted = p.getBoolean("inverted", State.inverted);
        State.showingPic = p.getBoolean("showingPic", State.showingPic);
        State.pictureOpacity = p.getFloat("pictureOpacity", State.pictureOpacity);
        State.keepPictureAspect = p.getBoolean("keepAspect", State.keepPictureAspect);
        State.boxSelect = p.getBoolean("boxSelect", State.boxSelect);
        State.autoPanelOpen = p.getBoolean("autoPanelOpen", State.autoPanelOpen);
        for (int i = 0; i < 3; i++) {
            State.autoThreshold[i] = p.getFloat("threshold" + i, State.autoThreshold[i]);
            State.autoContrast[i] = p.getFloat("contrast" + i, State.autoContrast[i]);
        }
    }

    private static void writeAtomically(File target, byte[] data) throws IOException {
        File tmp = new File(target.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.getFD().sync();
        }
        if (!tmp.renameTo(target)) throw new IOException("Could not replace " + target);
    }
}
