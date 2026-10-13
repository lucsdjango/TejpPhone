package com.example.tejpphone.function;

import com.example.tejpphone.model.AbnormalFence;
import com.example.tejpphone.model.Coord;
import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.NormalFence;
import com.example.tejpphone.model.Strip;
import com.example.tejpphone.model.VerticalFence;

import java.util.HashSet;

/** Mirrors the selected strips left to right around their own centre. */
public class MirrorModification extends TransformStripsModification {

    public MirrorModification(Fence f) {
        fence = f;
        oldStrips = new HashSet<>(State.selectedStrips);
        newStrips = new HashSet<>();
    }

    @Override
    public boolean isValid() {
        return !oldStrips.isEmpty();
    }

    /** Computes the mirrored strips once, so redo puts back the same strip objects. */
    @Override
    public void perform() {
        if (newStrips.isEmpty()) mirror();
        fence.getStrips().removeAll(oldStrips);
        fence.getStrips().addAll(newStrips);
        State.selectedStrips.removeAll(oldStrips);
        State.selectedStrips.addAll(newStrips);
    }

    private void mirror() {
        int midX = calcMiddle();

        for (Strip s : oldStrips) {
            Coord sl = s.getStart().getLeft();
            Coord sr = s.getStart().getRight();
            Coord el = s.getEnd().getLeft();
            Coord er = s.getEnd().getRight();

            int startSlope = convertSlope(s.getStart().getSlope());
            int endSlope = convertSlope(s.getEnd().getSlope());

            Edge newStart = null, newEnd = null;

            if (fence instanceof NormalFence) {
                // Mirroring turns / into \ and back. The desktop converted the slope twice here,
                // which gave back the original, so mirrored edges carried the wrong slope.
                newStart = new Edge(startSlope,
                        new Coord(midX + (midX - sr.getX()), sr.getY()),
                        new Coord(midX + (midX - sl.getX()), sl.getY()));
                newEnd = new Edge(endSlope,
                        new Coord(midX + (midX - er.getX()), er.getY()),
                        new Coord(midX + (midX - el.getX()), el.getY()));
            } else if (fence instanceof AbnormalFence) {
                if (startSlope == 3) {
                    newStart = new Edge(convertSlope(startSlope),
                            new Coord(midX + (midX - sl.getX()), sl.getY()),
                            new Coord(midX + (midX - sr.getX()), sr.getY()));
                } else {
                    newStart = new Edge(convertSlope(startSlope),
                            new Coord(midX + (midX - sr.getX()) + 1, sr.getY()),
                            new Coord(midX + (midX - sl.getX()) + 1, sl.getY()));
                }
                if (endSlope == 3) {
                    newEnd = new Edge(convertSlope(endSlope),
                            new Coord(midX + (midX - el.getX()), el.getY()),
                            new Coord(midX + (midX - er.getX()), er.getY()));
                } else {
                    newEnd = new Edge(convertSlope(endSlope),
                            new Coord(midX + (midX - er.getX()) + 1, er.getY()),
                            new Coord(midX + (midX - el.getX()) + 1, el.getY()));
                }
            } else if (fence instanceof VerticalFence) {
                newStart = new Edge(startSlope,
                        new Coord(midX + (midX - sl.getX()), sl.getY()),
                        new Coord(midX + (midX - sr.getX()), sr.getY()));
                newEnd = new Edge(endSlope,
                        new Coord(midX + (midX - el.getX()), el.getY()),
                        new Coord(midX + (midX - er.getX()), er.getY()));
            }

            // The desktop left out the colour here, so mirrored strips turned white.
            newStrips.add(new Strip(onGrid(newStart), onGrid(newEnd), s.getWidth(), fence.getType(), s.getColor()));
        }
    }

    @Override
    public void revert() {
        fence.getStrips().removeAll(newStrips);
        fence.getStrips().addAll(oldStrips);
        State.selectedStrips.removeAll(newStrips);
        State.selectedStrips.addAll(oldStrips);
    }

    /**
     * The fence's own edge at the same place, if there is one. Then mirrored strips behave like
     * drawn ones, for example when their end is dragged. Edges mirrored off the grid stay as they are.
     */
    private Edge onGrid(Edge e) {
        Edge g = fence.edgeAtOrNull(e.cellX(), e.cellY());
        return g != null && g.equals(e) && g.slope == e.slope ? g : e;
    }

    private int convertSlope(int i) {
        if (i == 1) return 0;
        if (i == 0) return 1;
        if (i == 2) return 2;
        if (i == 3) return 3;
        return -1;
    }
}
