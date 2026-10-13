package com.example.tejpphone.function;

import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Strip;

import java.util.HashSet;

/** Drags the selected strips across the grid (Move tool). */
public class MoveModification extends TransformStripsModification {

    private int lastx, lasty;
    private final Edge initEdge;

    @SuppressWarnings("unchecked")
    public MoveModification(Edge e) {
        initEdge = e;
        fence = State.fence;
        oldStrips = (HashSet<Strip>) State.selectedStrips.clone();
    }

    /**
     * Valid if the strips actually moved.
     * The desktop version tracked the last requested offset instead of the last applied one,
     * so an undo after a blocked move shifted the strips to the wrong place.
     */
    @Override
    public boolean isValid() {
        return lastx != 0 || lasty != 0;
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
            for (Strip s : oldStrips) {
                s.shift(horShift, vertShift, fence);
            }
            lastx = dx;
            lasty = dy;
        }
    }

    private boolean canShiftAll(int x, int y) {
        for (Strip s : oldStrips) {
            if (!s.canShift(x, y, fence)) return false;
        }
        return true;
    }

    private void shiftBack() {
        if (canShiftAll(-lastx, -lasty)) {
            for (Strip s : oldStrips) {
                s.shift(-lastx, -lasty, fence);
            }
        }
        lastx = 0;
        lasty = 0;
    }

    @Override
    public void perform() {}

    @Override
    public void revert() {
        shiftBack();
    }

    @Override
    public void cancel() {
        shiftBack();
    }
}
