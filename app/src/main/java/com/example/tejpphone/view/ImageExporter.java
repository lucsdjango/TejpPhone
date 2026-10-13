package com.example.tejpphone.view;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.tejpphone.function.State;
import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Picture;
import com.example.tejpphone.model.Strip;

/**
 * Renders the drawing for a JPG export. Port of the desktop FencePanel.saveImg:
 * <ul>
 *   <li>white background,</li>
 *   <li>the picture, if shown, and the grid lines at half strength,</li>
 *   <li>strips in their own colour, inverted if Invert is on, at 67% for 2.0 cm strips
 *       and 55% for all others.</li>
 * </ul>
 * With white strips, turn on Invert to get black strips on the white background.
 *
 * <p>The desktop rendered at the current zoom. Here the image is rendered larger, so it stays sharp.
 */
public final class ImageExporter {

    private ImageExporter() {}

    private static final int S = Fence.CELL_SIZE;
    /** Long side of the exported image, in pixels. Never fewer pixels per cell than the desktop's 12. */
    private static final int TARGET_LONG_SIDE = 2400;

    public static Bitmap render(Fence fence) {
        int cells = Math.max(fence.width, fence.height);
        float k = Math.max(S, (float) TARGET_LONG_SIDE / cells) / S;   // pixels per content unit
        int w = Math.round(fence.width * S * k), h = Math.round(fence.height * S * k);

        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);
        canvas.scale(k, k);

        // The desktop painted its child panels, the picture and the edges, at 50%.
        Picture p = fence.getPicture();
        if (State.showingPic && p != null && p.hasPixels()) {
            Bitmap bmp = Bitmap.createBitmap(p.getPixels(), p.getWidth(), p.getHeight(), Bitmap.Config.ARGB_8888);
            double[] c = p.getCorners();
            Paint pp = new Paint(Paint.FILTER_BITMAP_FLAG);
            pp.setAlpha(128);
            canvas.drawBitmap(bmp, null,
                    new RectF((float) c[0] * S, (float) c[1] * S, (float) c[2] * S, (float) c[3] * S), pp);
            bmp.recycle();
        }

        // One content unit is one pixel at the desktop's default zoom, like its 1 px lines.
        Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        grid.setColor(Color.BLACK);
        grid.setAlpha(128);
        grid.setStrokeWidth(1f);
        for (int j = 0; j < fence.width; j++) {
            for (int i = 0; i < fence.height; i++) {
                Edge e = fence.getEdgeAt(j, i);
                if (e == null) continue;
                float x0 = j * S, y0 = i * S, x1 = x0 + S, y1 = y0 + S, mx = x0 + S / 2f, my = y0 + S / 2f;
                switch (e.getSlope()) {
                    case 0: canvas.drawLine(x0, y1, x1, y0, grid); break;
                    case 1: canvas.drawLine(x0, y0, x1, y1, grid); break;
                    case 2: canvas.drawLine(x0, my, x1, my, grid); break;
                    default: canvas.drawLine(mx, y0, mx, y1, grid); break;
                }
            }
        }

        StripRenderer renderer = new StripRenderer(1f);
        Paint strip = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (Strip s : fence.getStrips()) {
            int col = s.getColor();
            if (State.inverted) {
                col = Color.argb(Color.alpha(col), 255 - Color.red(col), 255 - Color.green(col), 255 - Color.blue(col));
            }
            float alpha = s.getWidth() == 2.0f ? 0.67f : 0.55f;
            strip.setColor(col);
            strip.setAlpha(Math.round(Color.alpha(col) * alpha));
            renderer.draw(canvas, s, strip);
        }
        return out;
    }
}
