package com.example.tejpphone.function;

import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Geometry;
import com.example.tejpphone.model.NormalFence;
import com.example.tejpphone.model.Picture;
import com.example.tejpphone.model.Strip;
import com.example.tejpphone.model.VerticalFence;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Port of the desktop auto* functions in FenceListener: autoStrips, autoVerticalStrips and
 * autoDiagStrips for normal fences, and autoStrips and autoDiagStrips for vertical fences.
 *
 * <p>Each possible one-cell strip is laid over the picture, and the average brightness under it
 * decides its width: thick above the threshold, thin above threshold x contrast, none otherwise.
 * Neighbouring cells of the same width are then joined into longer strips.
 *
 * <p>The brightness under each cell does not depend on the sliders, so it is computed once
 * and reused while the sliders move. The desktop recomputed everything on every slider tick.
 */
public class AutoStrips {

    private static final int S = Fence.CELL_SIZE;

    /** The desktop variants, each with its own widths and rules. */
    enum Kind {
        NORMAL_HOR(4.51f, 1.51f, Alpha.FRACTION),
        NORMAL_VERT(4.5f, 1.5f, Alpha.FRACTION),
        NORMAL_DIAG(5f, 1.5f, Alpha.ALL_OR_NOTHING),
        VERT_HOR(3f, 1.5f, Alpha.IGNORED),
        VERT_DIAG(3f, 1.1875f, Alpha.IGNORED);

        final float thick, thin;
        final Alpha alpha;

        Kind(float thick, float thin, Alpha alpha) {
            this.thick = thick;
            this.thin = thin;
            this.alpha = alpha;
        }
    }

    /** How each desktop variant weighed transparent pixels. */
    enum Alpha {
        /** alpha / 255 as a fraction. */
        FRACTION,
        /** alpha / 255 in integer maths, so only fully opaque pixels count. */
        ALL_OR_NOTHING,
        /** Not used. */
        IGNORED
    }

    static Kind kindFor(Fence f, State.AutoDirection d) {
        if (f instanceof NormalFence) {
            switch (d) {
                case HORIZONTAL: return Kind.NORMAL_HOR;
                case VERTICAL: return Kind.NORMAL_VERT;
                default: return Kind.NORMAL_DIAG;
            }
        }
        if (f instanceof VerticalFence) {
            // The desktop had no vertical strips for vertical fences: "vert" ran the horizontal variant.
            return d == State.AutoDirection.DIAGONAL ? Kind.VERT_DIAG : Kind.VERT_HOR;
        }
        return null;   // The desktop had no auto strips for abnormal fences.
    }

    // Brightness cache. NaN marks a cell with no picture under it.
    private float[][] averages;
    private Fence cachedFence;
    private Picture cachedPicture;
    private int cachedVersion;
    private Kind cachedKind;
    private boolean cachedInverted;

    /**
     * Replaces the fence's strips with base plus the auto strips for these settings.
     * @return false if this fence type or picture cannot be used
     */
    public boolean apply(Fence f, List<Strip> base, Picture p, State.AutoDirection d,
                         float threshold, float contrast, boolean inverted) {
        Kind kind = kindFor(f, d);
        if (kind == null || p == null || !p.hasPixels()) return false;

        float[][] avg = averages(f, p, kind, inverted);

        LinkedList<Strip> strips = f.getStrips();
        strips.clear();
        strips.addAll(base);
        clear(strips, kind);

        for (int row = 0; row < avg.length; row++) {
            if (kind == Kind.VERT_DIAG && row == 0) continue;   // The desktop skipped the first row here.
            float[] widths = classify(avg[row], kind, threshold, contrast);
            unite(f, kind, widths, kind.thin, row, strips);
            unite(f, kind, widths, kind.thick, row, strips);
        }
        return true;
    }

    // ------------------------------------------------------------------ clearing old auto strips

    /** Same rules as the desktop clearHorizontalStrips, clearVerticalStrips and clearDiagStrips. */
    static void clear(List<Strip> strips, Kind kind) {
        Iterator<Strip> i = strips.iterator();
        while (i.hasNext()) {
            Strip s = i.next();
            Edge a = s.getStart(), b = s.getEnd();
            switch (kind) {
                case NORMAL_HOR:
                case VERT_HOR: {
                    int startY = Math.min(a.getLeft().getY(), a.getRight().getY());
                    int endY = Math.min(b.getLeft().getY(), b.getRight().getY());
                    if (startY == endY && s.getColor() == Strip.WHITE) i.remove();
                    break;
                }
                case NORMAL_VERT: {
                    int startX = Math.min(a.getLeft().getX(), a.getRight().getX());
                    int endX = Math.min(b.getLeft().getX(), b.getRight().getX());
                    if (startX == endX && s.getColor() == Strip.WHITE) i.remove();
                    break;
                }
                case VERT_DIAG: {
                    int dX = a.getLeft().getX() - b.getLeft().getX();
                    int dY = a.getLeft().getY() - b.getLeft().getY();
                    if (dY == dX) i.remove();
                    break;
                }
                case NORMAL_DIAG:
                    // The desktop had this clearing commented out.
                    break;
            }
        }
    }

    // ------------------------------------------------------------------ brightness per cell

    private float[][] averages(Fence f, Picture p, Kind kind, boolean inverted) {
        if (averages != null && cachedFence == f && cachedPicture == p && cachedVersion == p.getVersion()
                && cachedKind == kind && cachedInverted == inverted) {
            return averages;
        }
        Sampler sampler = new Sampler(p, kind.alpha, inverted);
        float[][] avg;
        int w = f.width, h = f.height;

        switch (kind) {
            case NORMAL_HOR:
            case VERT_HOR: {
                int lastX = kind == Kind.NORMAL_HOR ? w - 1 : w - 2;
                avg = new float[h][w];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        avg[y][x] = x < lastX ? sampler.sample(f, x, y, x + 1, y) : Float.NaN;
                    }
                }
                break;
            }
            case NORMAL_VERT: {
                avg = new float[w][h];
                for (int x = 0; x < w; x++) {
                    for (int y = 0; y < h; y++) {
                        avg[x][y] = y < h - 1 ? sampler.sample(f, x, y, x, y + 1) : Float.NaN;
                    }
                }
                break;
            }
            case NORMAL_DIAG: {
                int rows = Math.max(0, normalDiagRows(f) - 1);
                avg = new float[rows][];
                for (int r = 0; r < rows; r++) {
                    int n = Math.max(0, normalDiagEdges(f, r));
                    avg[r] = new float[n];
                    for (int c = 0; c < n; c++) {
                        // The desktop skipped the middle row to avoid doubled strips through the centre.
                        boolean skip = r == f.height / 2 || c >= n - 1;
                        avg[r][c] = skip ? Float.NaN : sampler.sample(f,
                                normalDiagX(f, r, c), normalDiagY(f, r, c),
                                normalDiagX(f, r, c + 1), normalDiagY(f, r, c + 1));
                    }
                }
                break;
            }
            default: {   // VERT_DIAG
                int rows = Math.max(0, w + h - 2);
                avg = new float[rows][];
                for (int r = 0; r < rows; r++) {
                    int n = Math.max(0, verticalDiagEdges(f, r) - 1);
                    avg[r] = new float[n];
                    for (int c = 0; c < n; c++) {
                        int x0 = verticalDiagX(f, r, c), y0 = verticalDiagY(f, r, c);
                        avg[r][c] = sampler.sample(f, x0, y0, x0 + 1, y0 + 1);
                    }
                }
                break;
            }
        }

        averages = avg;
        cachedFence = f;
        cachedPicture = p;
        cachedVersion = p.getVersion();
        cachedKind = kind;
        cachedInverted = inverted;
        return avg;
    }

    /** Measures the average brightness of the picture under a one-cell strip. */
    private static final class Sampler {
        private final Alpha alpha;
        private final boolean inverted;
        private final int ix, iy, iw, ih;
        private final int[] px;

        Sampler(Picture p, Alpha alpha, boolean inverted) {
            this.alpha = alpha;
            this.inverted = inverted;
            double[] c = p.getCorners();
            // Same rounding as the desktop TejpImage.updateBounds at the default cell size.
            ix = (int) (c[0] * S);
            iy = (int) (c[1] * S);
            iw = (int) ((c[2] - c[0]) * S);
            ih = (int) ((c[3] - c[1]) * S);
            px = p.scaledPixels(iw, ih);
        }

        float sample(Fence f, int x0, int y0, int x1, int y1) {
            Edge a = f.edgeAtOrNull(x0, y0), b = f.edgeAtOrNull(x1, y1);
            if (a == null || b == null || px == null) return Float.NaN;

            Strip s = new Strip(a, b, 1f, f.getType());
            int[][] pols = s.getPolygons();
            int[] r = Geometry.bounds(pols[0]);
            // java.awt.Rectangle.intersects
            if (r[2] <= 0 || r[3] <= 0 || iw <= 0 || ih <= 0
                    || r[0] >= ix + iw || ix >= r[0] + r[2] || r[1] >= iy + ih || iy >= r[1] + r[3]) {
                return Float.NaN;
            }

            double sum = 0;
            int count = 0;
            for (int ty = r[1]; ty < r[1] + r[3]; ty++) {
                for (int tx = r[0]; tx < r[0] + r[2]; tx++) {
                    if (tx > ix && ty > iy && tx < ix + iw && ty < iy + ih
                            && (Geometry.polygonContains(pols[0], tx, ty)
                            || Geometry.polygonContains(pols[1], tx, ty))) {
                        int pixel = px[(ty - iy) * iw + (tx - ix)];
                        int red = (pixel >> 16) & 0xFF, green = (pixel >> 8) & 0xFF, blue = pixel & 0xFF;
                        int a8 = (pixel >>> 24) & 0xFF;
                        double weight;
                        switch (alpha) {
                            case FRACTION: weight = a8 / 255f; break;
                            case ALL_OR_NOTHING: weight = a8 / 255; break;
                            default: weight = 1; break;
                        }
                        if (!inverted) {
                            sum += (red + green + blue) / 3f / 255f * weight;
                        } else {
                            sum += ((255 - red) + (255 - green) + (255 - blue)) / 3f / 255f * weight;
                        }
                        count++;
                    }
                }
            }
            return count > 0 ? (float) (sum / count) : Float.NaN;
        }
    }

    // ------------------------------------------------------------------ widths and joining

    private static float[] classify(float[] avg, Kind kind, float threshold, float contrast) {
        float[] widths = new float[avg.length];
        for (int i = 0; i < avg.length; i++) {
            float a = avg[i];
            if (Float.isNaN(a)) continue;
            if (a > threshold) {
                widths[i] = kind.thick;
            } else if (a > threshold * contrast) {
                widths[i] = kind.thin;
            }
        }
        return widths;
    }

    /**
     * The desktop uniteStrips, uniteVertStrips and uniteDiagStrips. A run of cells at least as wide
     * as the limit becomes one strip, but only if some cell in it is exactly the limit. So thin
     * strips run underneath thick ones, and thick strips are added on top.
     */
    private static void unite(Fence f, Kind kind, float[] widths, float limit, int row, List<Strip> out) {
        boolean diag = kind == Kind.NORMAL_DIAG || kind == Kind.VERT_DIAG;
        boolean started = false, alwaysWider = true;
        int start = 0, end = 0;

        for (int n = 0; n < widths.length; n++) {
            float w = widths[n];
            if (w >= limit && !(diag && n == widths.length - 1)) {
                if (!started) {
                    start = n;
                    started = true;
                }
                end = n + 1;
                alwaysWider = alwaysWider && w > limit;
            } else {
                if (started && !alwaysWider) {
                    Edge a = edge(f, kind, row, start), b = edge(f, kind, row, end);
                    if (a != null && b != null) out.add(new Strip(a, b, limit, f.getType()));
                }
                started = false;
                alwaysWider = true;
            }
        }
    }

    /** The edge at position n along a row, in the row layout each variant uses. */
    private static Edge edge(Fence f, Kind kind, int row, int n) {
        switch (kind) {
            case NORMAL_HOR:
            case VERT_HOR: return f.edgeAtOrNull(n, row);
            case NORMAL_VERT: return f.edgeAtOrNull(row, n);
            case NORMAL_DIAG: return f.edgeAtOrNull(normalDiagX(f, row, n), normalDiagY(f, row, n));
            default: return f.edgeAtOrNull(verticalDiagX(f, row, n), verticalDiagY(f, row, n));
        }
    }

    // ------------------------------------------------------------------ diagonal rows, as on the desktop

    static int normalDiagRows(Fence f) {
        return f.height / 2 + f.width / 2 - 2;
    }

    static int normalDiagEdges(Fence f, int r) {
        if (r < f.height / 2) return 1 + 2 * r;
        if (r < f.width / 2) return f.height - 1;
        return f.height - 1 - 2 * (r - f.width / 2);
    }

    static int normalDiagX(Fence f, int r, int c) {
        return c + Math.max(0, r * 2 - f.height);
    }

    static int normalDiagY(Fence f, int r, int c) {
        return Math.max(0, f.height - r * 2 - 2) + c;
    }

    static int verticalDiagEdges(Fence f, int r) {
        if (r <= f.height - 1) return r + 1;
        if (r >= f.width) return f.height - (r - f.width) - 1;
        return f.height;
    }

    static int verticalDiagX(Fence f, int r, int c) {
        return (r < f.height ? 0 : r - f.height + 1) + c;
    }

    static int verticalDiagY(Fence f, int r, int c) {
        return Math.max(f.height - r - 1, 0) + c;
    }
}
