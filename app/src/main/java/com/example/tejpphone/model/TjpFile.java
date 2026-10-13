package com.example.tejpphone.model;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Reads and writes the desktop Tejp .tjp format.
 *
 * <p>Layout, all big-endian as written by java.io.DataOutputStream:
 * <pre>
 *   UTF     fence type: NORMAL, VERTICAL or ABNORMAL
 *   int     width
 *   int     height
 *   boolean has picture
 *     [BMP image, then 4 doubles for its corners]   only if has picture
 *   byte    number of frames                         missing in the oldest files
 *   per frame:
 *     int   number of strips                         a single byte in 2016-2017 files
 *     per strip:
 *       5 ints  start edge: left x, left y, right x, right y, slope
 *       5 ints  end edge
 *       float   width
 *       int     colour as ARGB                       missing in files from before about May 2024
 * </pre>
 *
 * <p>The desktop app guessed the colour variant from the file's modification date. That date is
 * not reliable once a file has been copied to a phone, so this reader instead tries each variant
 * and keeps the one that uses up the file exactly. The variants can never all fit the same length.
 */
public final class TjpFile {

    private TjpFile() {}

    /** Thrown when the data is not a .tjp file this reader understands. */
    public static class FormatException extends IOException {
        public FormatException(String message) {
            super(message);
        }
    }

    private enum Variant {
        /** Frames and colours. Written by the desktop app since about May 2024, and by this app. */
        FRAMES_WITH_COLOUR,
        /** Frames but no colours. */
        FRAMES,
        /** Frames whose strip count is a single byte, no colours. Seen in files from 2016 and 2017. */
        FRAMES_BYTE_COUNT,
        /** Frames, no colours, and a line break after each strip count and each strip. Seen in 2020. */
        FRAMES_LINE_BREAKS,
        /** No frame count and no colours: strips until the end of the file. */
        LEGACY
    }

    private static final int MAX_CELLS = 4_000_000;
    private static final int EDGE_BYTES = 5 * 4;

    public static Fence read(byte[] data) throws IOException {
        FormatException first = null;
        for (Variant v : Variant.values()) {
            try {
                return parse(data, v);
            } catch (FormatException e) {
                if (first == null) first = e;
            } catch (IOException | RuntimeException e) {
                if (first == null) first = new FormatException("Not a valid .tjp file");
            }
        }
        throw first;
    }

    private static Fence parse(byte[] data, Variant variant) throws IOException {
        ByteArrayInputStream bytes = new ByteArrayInputStream(data);
        DataInputStream in = new DataInputStream(bytes);

        String type = in.readUTF();
        int width = in.readInt();
        int height = in.readInt();
        if (width <= 0 || height <= 0 || (long) width * height > MAX_CELLS) {
            throw new FormatException("Unsupported fence size " + width + " x " + height);
        }

        Fence fence;
        switch (type) {
            case NormalFence.TYPE: fence = new NormalFence(width, height); break;
            case VerticalFence.TYPE: fence = new VerticalFence(width, height); break;
            case AbnormalFence.TYPE: fence = new AbnormalFence(width, height); break;
            default: throw new FormatException("Unknown fence type \"" + type + "\"");
        }

        if (in.readBoolean()) {
            fence.setPicture(readPicture(data, bytes, in));
        }

        boolean colour = variant == Variant.FRAMES_WITH_COLOUR;
        int stripBytes = 2 * EDGE_BYTES + 4 + (colour ? 4 : 0);

        if (variant == Variant.LEGACY) {
            if (bytes.available() % stripBytes != 0) throw new FormatException("Unexpected file length");
            while (bytes.available() > 0) {
                fence.addStrip(readStrip(in, fence, type, false));
            }
            return fence;
        }

        int frames = in.readUnsignedByte();
        for (int f = 0; f < frames; f++) {
            int n = variant == Variant.FRAMES_BYTE_COUNT ? in.readUnsignedByte() : in.readInt();
            if (variant == Variant.FRAMES_BYTE_COUNT && f == frames - 1) {
                // A one-byte count wraps at 256. The last frame's real count follows from the file length.
                int fits = bytes.available() / stripBytes;
                if (bytes.available() % stripBytes == 0 && fits % 256 == n) n = fits;
            }
            if (n < 0 || (long) n * stripBytes > bytes.available()) {
                throw new FormatException("Unexpected strip count");
            }
            boolean lineBreaks = variant == Variant.FRAMES_LINE_BREAKS;
            if (lineBreaks) readLineBreak(in);
            LinkedList<Strip> strips = new LinkedList<>();
            for (int i = 0; i < n; i++) {
                strips.add(readStrip(in, fence, type, colour));
                if (lineBreaks) readLineBreak(in);
            }
            if (f == 0) {
                fence.setStrips(strips);
            } else {
                fence.getExtraFrames().add(strips);
            }
        }
        if (bytes.available() != 0) throw new FormatException("Unexpected data at end of file");
        return fence;
    }

    /**
     * The picture is a BMP image followed by four doubles. The desktop app read it with ImageIO,
     * which stops at the end of the BMP. Here the BMP's own size field tells where it ends.
     * The BMP bytes are kept as they are, so an unchanged picture is saved back identically.
     */
    private static Picture readPicture(byte[] data, ByteArrayInputStream bytes, DataInputStream in)
            throws IOException {
        int start = data.length - bytes.available();
        if (bytes.available() < 14 || data[start] != 'B' || data[start + 1] != 'M') {
            throw new FormatException("Unreadable picture");
        }
        int bmpSize = (data[start + 2] & 0xFF)
                | (data[start + 3] & 0xFF) << 8
                | (data[start + 4] & 0xFF) << 16
                | (data[start + 5] & 0xFF) << 24;
        if (bmpSize < 14 || (long) bmpSize + 4 * 8 > bytes.available()) {
            throw new FormatException("Unreadable picture");
        }

        byte[] bmp = new byte[bmpSize];
        in.readFully(bmp);
        double x0 = in.readDouble(), y0 = in.readDouble(), x3 = in.readDouble(), y3 = in.readDouble();
        return Picture.fromTjp(bmp, x0, y0, x3, y3);
    }

    /** Accepts a Windows or Unix line break. */
    private static void readLineBreak(DataInputStream in) throws IOException {
        int b = in.readUnsignedByte();
        if (b == '\r') b = in.readUnsignedByte();
        if (b != '\n') throw new FormatException("Unexpected data between strips");
    }

    private static Strip readStrip(DataInputStream in, Fence fence, String type, boolean colour)
            throws IOException {
        Edge start = readEdge(in, fence);
        Edge end = readEdge(in, fence);
        float width = in.readFloat();
        if (Float.isNaN(width) || Float.isInfinite(width) || width < 0 || width > 1000) {
            throw new FormatException("Unexpected strip width");
        }
        // java.awt.Color(int) ignores the alpha byte, so colours always load opaque.
        int color = colour ? in.readInt() | 0xFF000000 : Strip.WHITE;
        return new Strip(start, end, width, type, color);
    }

    /** Uses the fence's own edge object when the stored edge matches one, as drawn strips do. */
    private static Edge readEdge(DataInputStream in, Fence fence) throws IOException {
        int lx = in.readInt(), ly = in.readInt(), rx = in.readInt(), ry = in.readInt();
        int slope = in.readInt();
        if (slope < 0 || slope > 3) throw new FormatException("Unexpected edge slope");

        Edge e = new Edge(slope, new Coord(lx, ly), new Coord(rx, ry));
        // Matched by position only: desktop Mirror stored some edges with the wrong slope,
        // and using the grid's edge repairs them.
        Edge onGrid = fence.edgeAtOrNull(e.cellX(), e.cellY());
        return onGrid != null && onGrid.equals(e) ? onGrid : e;
    }

    /** Writes the newest desktop variant, with colours, so the desktop app can open it. */
    public static void write(Fence fence, OutputStream os) throws IOException {
        DataOutputStream out = new DataOutputStream(os);
        out.writeUTF(fence.getType());
        out.writeInt(fence.width);
        out.writeInt(fence.height);

        Picture picture = fence.getPicture();
        out.writeBoolean(picture != null);
        if (picture != null) {
            out.write(picture.bmpForTjp());
            for (double d : picture.getCorners()) out.writeDouble(d);
        }

        List<List<Strip>> frames = new ArrayList<>();
        frames.add(fence.getStrips());
        frames.addAll(fence.getExtraFrames());
        int count = Math.min(frames.size(), 255);   // The count is a single byte.
        out.writeByte(count);

        for (int f = 0; f < count; f++) {
            List<Strip> strips = frames.get(f);
            out.writeInt(strips.size());
            for (Strip s : strips) {
                writeEdge(out, s.getStart());
                writeEdge(out, s.getEnd());
                out.writeFloat(s.getWidth());
                out.writeInt(s.getColor());
            }
        }
        out.flush();
    }

    private static void writeEdge(DataOutputStream out, Edge e) throws IOException {
        out.writeInt(e.getLeft().getX());
        out.writeInt(e.getLeft().getY());
        out.writeInt(e.getRight().getX());
        out.writeInt(e.getRight().getY());
        out.writeInt(e.getSlope());
    }
}
