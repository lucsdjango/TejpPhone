package com.example.tejpphone;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.NormalFence;
import com.example.tejpphone.model.Strip;
import com.example.tejpphone.model.TjpFile;
import com.example.tejpphone.model.VerticalFence;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedList;

public class TjpFileTest {

    private static byte[] write(Fence f) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TjpFile.write(f, out);
        return out.toByteArray();
    }

    private static NormalFence sample() {
        NormalFence f = new NormalFence(100, 44);
        f.addStrip(new Strip(f.getEdgeAt(2, 4), f.getEdgeAt(8, 4), 1.5f, NormalFence.TYPE, 0xFFFF0000));
        f.addStrip(new Strip(f.getEdgeAt(3, 3), f.getEdgeAt(9, 9), 4.5f, NormalFence.TYPE));
        return f;
    }

    /** Header as the desktop wrote it, without a picture. */
    private static DataOutputStream header(ByteArrayOutputStream bytes, String type, int w, int h)
            throws IOException {
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeUTF(type);
        out.writeInt(w);
        out.writeInt(h);
        out.writeBoolean(false);
        return out;
    }

    private static void edge(DataOutputStream out, int lx, int ly, int rx, int ry, int slope) throws IOException {
        out.writeInt(lx);
        out.writeInt(ly);
        out.writeInt(rx);
        out.writeInt(ry);
        out.writeInt(slope);
    }

    /** Edge (2,4) of a NormalFence is "/" from (2,5) to (3,4). Edge (8,4) is "/" from (8,5) to (9,4). */
    private static void colourlessStrip(DataOutputStream out) throws IOException {
        edge(out, 2, 5, 3, 4, 0);
        edge(out, 8, 5, 9, 4, 0);
        out.writeFloat(3f);
    }

    @Test
    public void roundTripKeepsEverything() throws IOException {
        NormalFence f = sample();
        Fence g = TjpFile.read(write(f));

        assertEquals(NormalFence.TYPE, g.getType());
        assertEquals(100, g.width);
        assertEquals(44, g.height);
        assertEquals(2, g.getStrips().size());
        Strip s = g.getStrips().getFirst();
        assertEquals(0xFFFF0000, s.getColor());
        assertEquals(1.5f, s.getWidth(), 0f);
        // Loaded strips use the fence's own edges, so editing them works like on fresh strips.
        assertSame(g.getEdgeAt(2, 4), s.getStart());
        assertSame(g.getEdgeAt(8, 4), s.getEnd());
        assertArrayEquals(write(f), write(g));
    }

    @Test
    public void pictureAndExtraFramesAreKeptForTheDesktop() throws IOException {
        NormalFence f = sample();
        // A minimal stand-in for the desktop BMP: "BM", little-endian size 20, then padding,
        // followed by the four corner doubles.
        byte[] picture = new byte[20 + 32];
        picture[0] = 'B';
        picture[1] = 'M';
        picture[2] = 20;
        picture[30] = 7;
        f.setPictureData(picture);
        LinkedList<Strip> frame = new LinkedList<>();
        frame.add(new Strip(f.getEdgeAt(1, 1), f.getEdgeAt(5, 1), 2f, NormalFence.TYPE));
        f.getExtraFrames().add(frame);

        byte[] data = write(f);
        Fence g = TjpFile.read(data);
        assertArrayEquals(picture, g.getPictureData());
        assertEquals(1, g.getExtraFrames().size());
        assertEquals(2, g.getStrips().size());
        assertArrayEquals(data, write(g));
    }

    @Test
    public void readsFramesWithoutColour() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = header(bytes, NormalFence.TYPE, 20, 20);
        out.writeByte(1);
        out.writeInt(1);
        colourlessStrip(out);

        Fence g = TjpFile.read(bytes.toByteArray());
        assertEquals(1, g.getStrips().size());
        assertEquals(Strip.WHITE, g.getStrips().getFirst().getColor());
        assertSame(g.getEdgeAt(8, 4), g.getStrips().getFirst().getEnd());
    }

    @Test
    public void readsOneByteStripCountsIncludingWrappedOnes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = header(bytes, NormalFence.TYPE, 20, 20);
        out.writeByte(1);
        out.writeByte(300 % 256);
        for (int i = 0; i < 300; i++) colourlessStrip(out);

        assertEquals(300, TjpFile.read(bytes.toByteArray()).getStrips().size());
    }

    @Test
    public void readsLineBreaksBetweenStrips() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = header(bytes, NormalFence.TYPE, 20, 20);
        out.writeByte(1);
        out.writeInt(2);
        out.writeBytes("\r\n");
        colourlessStrip(out);
        out.writeBytes("\r\n");
        colourlessStrip(out);
        out.writeBytes("\r\n");

        assertEquals(2, TjpFile.read(bytes.toByteArray()).getStrips().size());
    }

    @Test
    public void readsOldestFilesWithoutFrames() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = header(bytes, VerticalFence.TYPE, 33, 21);
        // Vertical edges: (x, y) to (x, y + 1), slope 3.
        edge(out, 1, 2, 1, 3, 3);
        edge(out, 6, 2, 6, 3, 3);
        out.writeFloat(1f);

        Fence g = TjpFile.read(bytes.toByteArray());
        assertTrue(g instanceof VerticalFence);
        assertEquals(1, g.getStrips().size());
        assertNull(g.getPictureData());
    }

    @Test
    public void rejectsOtherFiles() {
        byte[][] bad = {
                new byte[0],
                "hello, this is not a drawing".getBytes(),
                {0, 5, 'H', 'E', 'L', 'L', 'O', 0, 0, 0, 1, 0, 0, 0, 1, 0, 0},
        };
        for (byte[] data : bad) {
            try {
                TjpFile.read(data);
                fail("Accepted " + data.length + " bytes of junk");
            } catch (IOException expected) {
                // Good.
            }
        }
    }

    @Test
    public void rejectsTruncatedFile() throws IOException {
        byte[] data = write(sample());
        byte[] cut = new byte[data.length - 3];
        System.arraycopy(data, 0, cut, 0, cut.length);
        try {
            TjpFile.read(cut);
            fail("Accepted a truncated file");
        } catch (IOException expected) {
            // Good.
        }
    }
}
