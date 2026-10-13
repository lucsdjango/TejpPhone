package com.example.tejpphone.model;

/**
 * The reference picture under the grid. Port of the desktop TejpImage, without the Swing parts.
 *
 * <p>Its position is given by two corners in grid cells: (x0, y0) top left and (x3, y3) bottom right.
 * The image is stretched to fill that rectangle.
 */
public final class Picture {

    /** The desktop app stored pictures in .tjp files at this size. */
    public static final int TJP_SIZE = 300;

    /** Pictures are shrunk to at most this size on load, to keep memory use sensible. */
    public static final int MAX_SIZE = 2048;

    /** Smallest width or height in cells, so a corner drag cannot turn the picture inside out. */
    public static final double MIN_CELLS = 0.5;

    private final int width, height;
    private final int[] argb;
    /** The BMP exactly as read from a .tjp file, written back unchanged. Null for new pictures. */
    private final byte[] storedBmp;

    private double x0, y0, x3, y3;
    private int version = 0;

    private int[] scaled;
    private int scaledW = -1, scaledH = -1;

    private Picture(int width, int height, int[] argb, byte[] storedBmp,
                    double x0, double y0, double x3, double y3) {
        this.width = width;
        this.height = height;
        this.argb = argb;
        this.storedBmp = storedBmp;
        setCorners(x0, y0, x3, y3);
    }

    /** A newly loaded picture. */
    public static Picture fromPixels(int width, int height, int[] argb,
                                     double x0, double y0, double x3, double y3) {
        return new Picture(width, height, argb, null, x0, y0, x3, y3);
    }

    /** A picture read from a .tjp file. If the BMP cannot be decoded, it is still kept for saving. */
    public static Picture fromTjp(byte[] bmp, double x0, double y0, double x3, double y3) {
        Bmp.Image img = Bmp.decode(bmp);
        if (img == null) return new Picture(0, 0, null, bmp, x0, y0, x3, y3);
        return new Picture(img.width, img.height, img.argb, bmp, x0, y0, x3, y3);
    }

    public boolean hasPixels() {
        return argb != null;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int[] getPixels() {
        return argb;
    }

    /** Changes whenever the pixels or the position change. */
    public int getVersion() {
        return version;
    }

    public double[] getCorners() {
        return new double[]{x0, y0, x3, y3};
    }

    public void setCorners(double x0, double y0, double x3, double y3) {
        this.x0 = x0;
        this.y0 = y0;
        this.x3 = x3;
        this.y3 = y3;
        version++;
    }

    /**
     * The BMP to store in a .tjp file. A picture that came from a .tjp file is written back unchanged.
     * A new one is shrunk to 300 x 300 over black, as the desktop app did.
     */
    public byte[] bmpForTjp() {
        if (storedBmp != null) return storedBmp;
        int[] small = scale(argb, width, height, TJP_SIZE, TJP_SIZE);
        for (int i = 0; i < small.length; i++) {
            int c = small[i];
            int a = c >>> 24;
            int r = ((c >> 16) & 0xFF) * a / 255, g = ((c >> 8) & 0xFF) * a / 255, b = (c & 0xFF) * a / 255;
            small[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
        return Bmp.encode(TJP_SIZE, TJP_SIZE, small);
    }

    /**
     * The picture stretched to w x h pixels, as the desktop TejpImage.getScaledImage.
     * Cached, since auto strips read it many times.
     */
    public int[] scaledPixels(int w, int h) {
        if (argb == null || w <= 0 || h <= 0) return null;
        if (scaled == null || scaledW != w || scaledH != h) {
            scaled = scale(argb, width, height, w, h);
            scaledW = w;
            scaledH = h;
        }
        return scaled;
    }

    /** Bilinear scaling, like the desktop's AffineTransformOp.TYPE_BILINEAR. */
    static int[] scale(int[] src, int sw, int sh, int dw, int dh) {
        int[] out = new int[dw * dh];
        double fx = (double) sw / dw, fy = (double) sh / dh;
        for (int y = 0; y < dh; y++) {
            double sy = Math.max(0, (y + 0.5) * fy - 0.5);
            int y1 = Math.min((int) sy, sh - 1), y2 = Math.min(y1 + 1, sh - 1);
            double ty = sy - y1;
            for (int x = 0; x < dw; x++) {
                double sx = Math.max(0, (x + 0.5) * fx - 0.5);
                int x1 = Math.min((int) sx, sw - 1), x2 = Math.min(x1 + 1, sw - 1);
                double tx = sx - x1;
                int c11 = src[y1 * sw + x1], c21 = src[y1 * sw + x2];
                int c12 = src[y2 * sw + x1], c22 = src[y2 * sw + x2];
                int c = 0;
                for (int shift = 0; shift < 32; shift += 8) {
                    double top = ((c11 >>> shift) & 0xFF) * (1 - tx) + ((c21 >>> shift) & 0xFF) * tx;
                    double bot = ((c12 >>> shift) & 0xFF) * (1 - tx) + ((c22 >>> shift) & 0xFF) * tx;
                    c |= ((int) Math.round(top * (1 - ty) + bot * ty) & 0xFF) << shift;
                }
                out[y * dw + x] = c;
            }
        }
        return out;
    }
}
