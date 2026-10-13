package com.example.tejpphone.function;

import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Picture;

/**
 * Moves or resizes the picture. Port of the desktop MovePicModification.
 *
 * <p>As on the desktop, a plain drag moves the whole picture. With Shift, the drag moves the corner
 * in the quarter of the picture where the finger landed. New on the phone: grabbing a corner
 * handle resizes from that corner without Shift.
 */
public class MovePicModification extends Modification {

    private static final double S = Fence.CELL_SIZE;

    private final Picture picture;
    private final double oldX0, oldY0, oldX3, oldY3;
    private final float orgx, orgy;
    /** 0 top left, 1 top right, 2 bottom left, 3 bottom right. */
    private final int corner;
    private final boolean handle;

    /**
     * @param x      touch position in content units
     * @param handle corner handle under the finger, or -1
     */
    public MovePicModification(Picture p, float x, float y, int handle) {
        picture = p;
        double[] c = p.getCorners();
        oldX0 = c[0];
        oldY0 = c[1];
        oldX3 = c[2];
        oldY3 = c[3];
        orgx = x;
        orgy = y;
        this.handle = handle >= 0;

        if (handle >= 0) {
            corner = handle;
        } else {
            int q = 0;
            if (x >= (oldX0 + oldX3) / 2 * S) q += 1;
            if (y >= (oldY0 + oldY3) / 2 * S) q += 2;
            corner = q;
        }
    }

    /** True while a corner is being dragged rather than the whole picture. */
    public boolean isResizing() {
        return handle || State.shiftPressed;
    }

    public int getCorner() {
        return corner;
    }

    public void moveTo(float x, float y) {
        double dx = (x - orgx) / S, dy = (y - orgy) / S;
        double x0 = oldX0, y0 = oldY0, x3 = oldX3, y3 = oldY3;

        if (!isResizing()) {
            x0 += dx;
            y0 += dy;
            x3 += dx;
            y3 += dy;
        } else {
            // Corners cannot cross, so the picture never turns inside out.
            double m = Picture.MIN_CELLS;
            if (corner == 0 || corner == 2) x0 = Math.min(oldX0 + dx, oldX3 - m);
            if (corner == 1 || corner == 3) x3 = Math.max(oldX3 + dx, oldX0 + m);
            if (corner == 0 || corner == 1) y0 = Math.min(oldY0 + dy, oldY3 - m);
            if (corner == 2 || corner == 3) y3 = Math.max(oldY3 + dy, oldY0 + m);
        }
        picture.setCorners(x0, y0, x3, y3);
    }

    @Override
    public boolean isValid() {
        double[] c = picture.getCorners();
        return oldX0 != c[0] || oldY0 != c[1] || oldX3 != c[2] || oldY3 != c[3];
    }

    @Override
    public void perform() {}

    @Override
    public void revert() {
        picture.setCorners(oldX0, oldY0, oldX3, oldY3);
    }

    @Override
    public void cancel() {
        revert();
    }
}
