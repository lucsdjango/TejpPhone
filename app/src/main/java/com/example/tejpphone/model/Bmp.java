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
     * A 32-bit BMP keeps its alpha only if its header declares an alpha mask.
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

        // Colour masks. With BI_BITFIELDS they follow the 40-byte header, or are part of a longer one.
        int rMask = 0x00FF0000, gMask = 0x0000FF00, bMask = 0x000000FF, aMask = 0;
        if (compression == 3) {
            if (d.length < 66) return null;
            rMask = le32(d, 40 + 14);
            gMask = le32(d, 44 + 14);
            bMask = le32(d, 48 + 14);
            if (headerSize >= 56 && d.length >= 70) aMask = le32(d, 52 + 14);
        }

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
                if (bpp == 24) {
                    int b = d[p] & 0xFF, g = d[p + 1] & 0xFF, r = d[p + 2] & 0xFF;
                    argb[y * width + x] = 0xFF000000 | r << 16 | g << 8 | b;
                } else {
                    int v = le32(d, p);
                    int a = aMask == 0 ? 0xFF : channel(v, aMask);
                    argb[y * width + x] = a << 24 | channel(v, rMask) << 16 | channel(v, gMask) << 8 | channel(v, bMask);
                }
            }
        }
        return new Image(width, height, argb);
    }

    /** One 8-bit channel of a pixel, given its mask. */
    private static int channel(int v, int mask) {
        if (mask == 0) return 0;
        int shift = Integer.numberOfTrailingZeros(mask);
        int bits = Integer.bitCount(mask);
        int c = (v & mask) >>> shift;
        if (bits == 8) return c;
        int max = (1 << bits) - 1;   // bits < 32, as some channel bits are left for the others
        return Math.round(c * 255f / max);
    }

    /**
     * Encodes a bottom-up BMP. Opaque pictures become the 24-bit variant the desktop app wrote.
     * Pictures with transparency become 32-bit with a BITMAPV4HEADER and an alpha mask,
     * which keeps the alpha and which javax.imageio also reads.
     */
    public static byte[] encode(int width, int height, int[] argb) {
        for (int c : argb) {
            if (c >>> 24 != 0xFF) return encodeWithAlpha(width, height, argb);
        }
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

    private static final int V4_HEADER = 108;

    private static byte[] encodeWithAlpha(int width, int height, int[] argb) {
        int offset = 14 + V4_HEADER;
        int imageSize = width * 4 * height;
        byte[] d = new byte[offset + imageSize];
        d[0] = 'B';
        d[1] = 'M';
        put32(d, 2, d.length);
        put32(d, 10, offset);
        put32(d, 14, V4_HEADER);
        put32(d, 18, width);
        put32(d, 22, height);
        d[26] = 1;     // planes
        d[28] = 32;    // bits per pixel
        put32(d, 30, 3);             // BI_BITFIELDS
        put32(d, 34, imageSize);
        put32(d, 54, 0x00FF0000);    // red mask
        put32(d, 58, 0x0000FF00);    // green mask
        put32(d, 62, 0x000000FF);    // blue mask
        put32(d, 66, 0xFF000000);    // alpha mask
        put32(d, 70, 0x73524742);    // colour space "sRGB"
        for (int y = 0; y < height; y++) {
            int row = offset + width * 4 * (height - 1 - y);
            for (int x = 0; x < width; x++) put32(d, row + x * 4, argb[y * width + x]);
        }
        return d;
    }
}
