package com.example.tejpphone.model;

/** Replacements for the java.awt.geom helpers the desktop app used. */
public final class Geometry {

    private Geometry() {}

    /** Same algorithm as java.awt.geom.Line2D.linesIntersect. */
    public static boolean linesIntersect(double x1, double y1, double x2, double y2,
                                         double x3, double y3, double x4, double y4) {
        return relativeCCW(x1, y1, x2, y2, x3, y3) * relativeCCW(x1, y1, x2, y2, x4, y4) <= 0
                && relativeCCW(x3, y3, x4, y4, x1, y1) * relativeCCW(x3, y3, x4, y4, x2, y2) <= 0;
    }

    /** Same algorithm as java.awt.geom.Line2D.relativeCCW. */
    static int relativeCCW(double x1, double y1, double x2, double y2, double px, double py) {
        x2 -= x1;
        y2 -= y1;
        px -= x1;
        py -= y1;
        double ccw = px * y2 - py * x2;
        if (ccw == 0.0) {
            ccw = px * x2 + py * y2;
            if (ccw > 0.0) {
                px -= x2;
                py -= y2;
                ccw = px * x2 + py * y2;
                if (ccw < 0.0) ccw = 0.0;
            }
        }
        return (ccw < 0.0) ? -1 : ((ccw > 0.0) ? 1 : 0);
    }

    /**
     * Even-odd point-in-polygon test, like java.awt.Polygon.contains.
     * @param pts x0, y0, x1, y1, ... of the polygon corners
     */
    public static boolean polygonContains(int[] pts, double px, double py) {
        boolean inside = false;
        int n = pts.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = pts[2 * i], yi = pts[2 * i + 1];
            double xj = pts[2 * j], yj = pts[2 * j + 1];
            if ((yi > py) != (yj > py)) {
                double xCross = xi + (py - yi) * (xj - xi) / (yj - yi);
                if (px < xCross) inside = !inside;
            }
        }
        return inside;
    }

    /** Bounding box of a polygon as {minX, minY, width, height}. */
    public static int[] bounds(int[] pts) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < pts.length; i += 2) {
            minX = Math.min(minX, pts[i]);
            maxX = Math.max(maxX, pts[i]);
            minY = Math.min(minY, pts[i + 1]);
            maxY = Math.max(maxY, pts[i + 1]);
        }
        return new int[]{minX, minY, maxX - minX, maxY - minY};
    }
}
