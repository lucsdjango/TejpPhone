package com.example.tejpphone.function;

import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Strip;

import java.util.HashSet;

/** Drags the selected strips across the grid (Move tool). */
public class MoveModification extends TransformStripsModification {

    /** Offset reached so far in the live drag. */
    private int lastx, lasty;
    /** Offset when the drag ended, for undo and redo. */
    private int appliedX, appliedY;
    private final Edge initEdge;

    public MoveModification(Edge e) {
        initEdge = e;
        fence = State.fence;
        oldStrips = new HashSet<>(State.selectedStrips);
    }

    /**
     * Valid if the strips actually moved.
     * The desktop version tracked the last requested offset instead of the last applied one,
     * so an undo after a blocked move shifted the strips to the wrong place.
     */
    @Override
    public boolean isValid() {
        return appliedX != 0 || appliedY != 0;
    }

    public void move(Edge e) {
        int dx = ((e.getLeft().getX() - initEdge.getLeft().getX()) / 2 * 2);
        int dy = e.getLeft().getY() - initEdge.getLeft().getY();

        int minX = calcLeft();
        int maxX = calcRight();

        int horShift = dx - lastx;
        int vertShift = dy - lasty;

        if ((minX + horShift) > 0 && (maxX + horShift) < (fence.width - 1)
                && canShiftAll(horShift, vertShift)) {
            shiftAll(horShift, vertShift);
            lastx = dx;
            lasty = dy;
        }
    }

    /** Changes whenever the strips have actually moved, for haptic feedback. */
    public long offsetKey() {
        return ((long) lastx << 32) ^ (lasty & 0xFFFFFFFFL);
    }

    private boolean canShiftAll(int x, int y) {
        for (Strip s : oldStrips) {
            if (!s.canShift(x, y, fence)) return false;
        }
        return true;
    }

    private void shiftAll(int x, int y) {
        if (!canShiftAll(x, y)) return;
        for (Strip s : oldStrips) s.shift(x, y, fence);
    }

    @Override
    public void perform() {
        appliedX = lastx;
        appliedY = lasty;
    }

    @Override
    public void revert() {
        shiftAll(-appliedX, -appliedY);
    }

    @Override
    public void redo() {
        shiftAll(appliedX, appliedY);
    }

    @Override
    public void cancel() {
        shiftAll(-lastx, -lasty);
        lastx = 0;
        lasty = 0;
    }
}
