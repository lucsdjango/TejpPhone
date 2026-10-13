package com.example.tejpphone.view;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;

import com.example.tejpphone.model.Geometry;
import com.example.tejpphone.model.Strip;

import java.util.WeakHashMap;

/**
 * Draws strips the way the desktop Strip.paintStrip did.
 * The desktop drew a thick square-capped line clipped to the strip's two polygons.
 * Here the same shape is computed once with Path.op and cached until the strip changes.
 * Selected strips also get an orange outline and a drop shadow, which the desktop app did not have.
 */
class StripRenderer {

    /** Selection look, in dp so it stays the same size on screen at any zoom. */
    private static final float OUTLINE_DP = 2f;
    private static final float SHADOW_OFFSET_DP = 3f;
    private static final float SHADOW_BLUR_DP = 4f;
    private static final int OUTLINE_COLOR = 0xFFFF9100;   // bright orange
    private static final int SHADOW_COLOR = 0xB0000000;    // dark, mostly opaque

    private static final class Entry {
        int version;
        Path fill;
    }

    private final WeakHashMap<Strip, Entry> cache = new WeakHashMap<>();

    private final float density;
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path screenPath = new Path();
    private final Matrix zoom = new Matrix();

    StripRenderer(float density) {
        this.density = density;
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeJoin(Paint.Join.ROUND);
        outlinePaint.setColor(OUTLINE_COLOR);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(SHADOW_COLOR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Shadow layers work on any shape with hardware drawing only from Android 9.
            float px = density;
            shadowPaint.setShadowLayer(SHADOW_BLUR_DP * px, SHADOW_OFFSET_DP * px,
                    SHADOW_OFFSET_DP * px, SHADOW_COLOR);
        }
    }

    void draw(Canvas canvas, Strip strip, Paint paint) {
        Path p = path(strip);
        if (p != null) canvas.drawPath(p, paint);
    }

    /**
     * Draws a selected strip: shadow first, then the strip, then the outline on top.
     * @param scale the view's zoom, content units to screen pixels
     */
    void drawSelected(Canvas canvas, Strip strip, Paint paint, float scale) {
        Path p = path(strip);
        if (p == null) return;

        drawShadow(canvas, p, scale);
        canvas.drawPath(p, paint);

        // A stroke is centred on the edge. Clipping out the strip keeps only the outer half,
        // so the outline never covers the strip's own colour, even on thin strips.
        canvas.save();
        canvas.clipOutPath(p);
        outlinePaint.setStrokeWidth(2 * OUTLINE_DP * density / scale);
        canvas.drawPath(p, outlinePaint);
        canvas.restore();
    }

    /**
     * The shadow is drawn in screen pixels, so its offset and softness do not change with zoom.
     * The strip's own area is clipped out, so the shadow only shows around it and does not
     * darken the half-transparent strip itself.
     */
    private void drawShadow(Canvas canvas, Path p, float scale) {
        zoom.setScale(scale, scale);
        p.transform(zoom, screenPath);

        canvas.save();
        canvas.scale(1f / scale, 1f / scale);
        canvas.clipOutPath(screenPath);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Only the shadow survives the clip; the shape itself is clipped away.
            canvas.drawPath(screenPath, shadowPaint);
        } else {
            // Android 8.1: a sharp shadow instead of a soft one.
            float off = SHADOW_OFFSET_DP * density;
            canvas.translate(off, off);
            canvas.drawPath(screenPath, shadowPaint);
        }
        canvas.restore();
    }

    private Path path(Strip strip) {
        Entry e = cache.get(strip);
        if (e == null || e.version != strip.getVersion()) {
            e = new Entry();
            e.version = strip.getVersion();
            e.fill = buildPath(strip);
            cache.put(strip, e);
        }
        return e.fill;
    }

    private static Path buildPath(Strip strip) {
        int[][] pols = strip.getPolygons();
        if (pols[0] == null) return null;

        int[] r = Geometry.bounds(pols[0]);
        if (r[2] <= 0 || r[3] <= 0) return null;

        float sw = strip.getStrokeWidth();
        if (sw <= 0) return null;

        float[] ln = strip.getCentreLine();
        float dx = ln[2] - ln[0], dy = ln[3] - ln[1];
        float len = (float) Math.hypot(dx, dy);
        if (len == 0) return null;

        // Thick line with square caps, like java.awt.BasicStroke's default.
        float hw = sw / 2f;
        float ux = dx / len * hw, uy = dy / len * hw;
        float nx = -uy, ny = ux;
        Path line = new Path();
        line.moveTo(ln[0] - ux + nx, ln[1] - uy + ny);
        line.lineTo(ln[0] - ux - nx, ln[1] - uy - ny);
        line.lineTo(ln[2] + ux - nx, ln[3] + uy - ny);
        line.lineTo(ln[2] + ux + nx, ln[3] + uy + ny);
        line.close();

        // The desktop clipped to each polygon in turn, using java.awt.Polygon's even-odd rule.
        Path clip = new Path();
        Path p1 = polygon(pols[0]);
        Path p2 = polygon(pols[1]);
        if (!clip.op(p1, p2, Path.Op.UNION)) clip = p1;

        Path fill = new Path();
        if (!fill.op(line, clip, Path.Op.INTERSECT)) return null;
        return fill;
    }

    private static Path polygon(int[] pts) {
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) {
            p.lineTo(pts[i], pts[i + 1]);
        }
        p.close();
        return p;
    }
}
