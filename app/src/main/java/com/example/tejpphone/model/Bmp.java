package com.example.tejpphone.model;

/**
 * Minimal BMP reader and writer for the picture stored inside .tjp files.
 * The desktop app wrote 24-bit uncompressed bottom-up BMPs with javax.imageio.
 * Pure Java, so it works in unit tests as well as on the phone.
 */
public final class Bmp {

    private Bmp() {}

    /** Decoded pixels as ARGB, row by row from the top. */
    public static final class Image {
        public final int width, height;
        public final int[] argb;

        public Image(int width, int height, int[] argb) {
            this.width = width;
            this.height = height;
            this.argb = argb;
        }
    }

    private static int le32(byte[] d, int p) {
        return (d[p] & 0xFF) | (d[p + 1] & 0xFF) << 8 | (d[p + 2] & 0xFF) << 16 | (d[p + 3] & 0xFF) << 24;
    }

    private static int le16(byte[] d, int p) {
        return (d[p] & 0xFF) | (d[p + 1] & 0xFF) << 8;
    }

    private static void put32(byte[] d, int p, int v) {
        d[p] = (byte) v;
        d[p + 1] = (byte) (v >> 8);
        d[p + 2] = (byte) (v >> 16);
        d[p + 3] = (byte) (v >> 24);
    }

    /**
     * Decodes an uncompressed 24- or 32-bit BMP.
     * @return null for anything else, such as compressed or palette images
     */
    public static Image decode(byte[] d) {
        if (d.length < 54 || d[0] != 'B' || d[1] != 'M') return null;
        int offset = le32(d, 10);
        int headerSize = le32(d, 14);
        if (headerSize < 40) return null;
        int width = le32(d, 18);
        int rawHeight = le32(d, 22);
        int bpp = le16(d, 28);
        int compression = le32(d, 30);
        if (width <= 0 || rawHeight == 0 || width > 16384 || Math.abs(rawHeight) > 16384) return null;
        if ((bpp != 24 && bpp != 32) || (compression != 0 && !(compression == 3 && bpp == 32))) return null;

        boolean bottomUp = rawHeight > 0;
        int height = Math.abs(rawHeight);
        int bytesPerPixel = bpp / 8;
        int rowSize = (width * bytesPerPixel + 3) & ~3;
        if (offset < 0 || (long) offset + (long) rowSize * height > d.length) return null;

        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            int row = offset + rowSize * (bottomUp ? height - 1 - y : y);
            for (int x = 0; x < width; x++) {
                int p = row + x * bytesPerPixel;
                int b = d[p] & 0xFF, g = d[p + 1] & 0xFF, r = d[p + 2] & 0xFF;
                argb[y * width + x] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        return new Image(width, height, argb);
    }

    /** Encodes opaque 24-bit bottom-up BMP, the variant the desktop app wrote. */
    public static byte[] encode(int width, int height, int[] argb) {
        int rowSize = (width * 3 + 3) & ~3;
        int imageSize = rowSize * height;
        byte[] d = new byte[54 + imageSize];
        d[0] = 'B';
        d[1] = 'M';
        put32(d, 2, d.length);
        put32(d, 10, 54);
        put32(d, 14, 40);
        put32(d, 18, width);
        put32(d, 22, height);
        d[26] = 1;     // planes
        d[28] = 24;    // bits per pixel
        put32(d, 34, imageSize);
        for (int y = 0; y < height; y++) {
            int row = 54 + rowSize * (height - 1 - y);
            for (int x = 0; x < width; x++) {
                int c = argb[y * width + x];
                int p = row + x * 3;
                d[p] = (byte) c;
                d[p + 1] = (byte) (c >> 8);
                d[p + 2] = (byte) (c >> 16);
            }
        }
        return d;
    }
}
