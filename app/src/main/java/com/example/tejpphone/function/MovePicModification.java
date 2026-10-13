package com.example.tejpphone.function;

import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Picture;

/**
 * Moves or resizes the picture. Port of the desktop MovePicModification.
 *
 * <p>Dragging inside the picture moves it. Dragging a corner handle resizes it from that corner,
 * which replaces the desktop's Shift-drag. With "keep aspect" on, resizing keeps the
 * picture's width-to-height ratio.
 */
public class MovePicModification extends Modification {

    private static final double S = Fence.CELL_SIZE;

    private final Picture picture;
    private final double oldX0, oldY0, oldX3, oldY3;
    private double[] newCorners;
    private final float orgx, orgy;
    /** 0 top left, 1 top right, 2 bottom left, 3 bottom right, or -1 to move. */
    private final int corner;

    /**
     * @param x      touch position in content units
     * @param handle corner handle under the finger, or -1 to move the whole picture
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
        corner = handle;
    }

    /** True while a corner is being dragged rather than the whole picture. */
    public boolean isResizing() {
        return corner >= 0;
    }

    public int getCorner() {
        return corner;
    }

    public void moveTo(float x, float y) {
        double dx = (x - orgx) / S, dy = (y - orgy) / S;
        double x0 = oldX0, y0 = oldY0, x3 = oldX3, y3 = oldY3;

        if (!isResizing()) {
            picture.setCorners(x0 + dx, y0 + dy, x3 + dx, y3 + dy);
            return;
        }

        boolean left = corner == 0 || corner == 2, top = corner == 0 || corner == 1;
        // The opposite corner stays put.
        double ax = left ? oldX3 : oldX0, ay = top ? oldY3 : oldY0;
        double cx = (left ? oldX0 : oldX3) + dx, cy = (top ? oldY0 : oldY3) + dy;

        // Corners cannot cross, so the picture never turns inside out.
        double m = Picture.MIN_CELLS;
        double w = Math.max(m, left ? ax - cx : cx - ax);
        double h = Math.max(m, top ? ay - cy : cy - ay);

        if (State.keepPictureAspect) {
            double ow = oldX3 - oldX0, oh = oldY3 - oldY0;
            double s = Math.max(w / ow, h / oh);
            w = Math.max(m, ow * s);
            h = Math.max(m, oh * s);
        }

        x0 = left ? ax - w : ax;
        x3 = left ? ax : ax + w;
        y0 = top ? ay - h : ay;
        y3 = top ? ay : ay + h;
        picture.setCorners(x0, y0, x3, y3);
    }

    @Override
    public boolean isValid() {
        double[] c = picture.getCorners();
        return oldX0 != c[0] || oldY0 != c[1] || oldX3 != c[2] || oldY3 != c[3];
    }

    @Override
    public void perform() {
        newCorners = picture.getCorners();
    }

    @Override
    public void redo() {
        picture.setCorners(newCorners[0], newCorners[1], newCorners[2], newCorners[3]);
    }

    @Override
    public void revert() {
        picture.setCorners(oldX0, oldY0, oldX3, oldY3);
    }

    @Override
    public void cancel() {
        revert();
    }
}
