package com.example.tejpphone.view;

import android.graphics.Bitmap;

import com.example.tejpphone.model.Picture;

/**
 * Turns a picture's pixels into a Bitmap for drawing, once.
 * Moving or resizing a picture keeps its pixels, so the bitmap is reused.
 */
class PictureBitmaps {

    private int[] pixelsFor;
    private Bitmap bitmap;

    Bitmap get(Picture p) {
        if (!p.hasPixels()) return null;
        if (bitmap == null || pixelsFor != p.getPixels()) {
            bitmap = Bitmap.createBitmap(p.getPixels(), p.getWidth(), p.getHeight(), Bitmap.Config.ARGB_8888);
            pixelsFor = p.getPixels();
        }
        return bitmap;
    }
}
