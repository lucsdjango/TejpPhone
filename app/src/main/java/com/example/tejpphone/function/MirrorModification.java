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

    @SuppressWarnings("unchecked")
    public MirrorModification(Fence f) {
        fence = f;
        oldStrips = (HashSet<Strip>) State.selectedStrips.clone();
        newStrips = new HashSet<>();
    }

    @Override
    public boolean isValid() {
        return !oldStrips.isEmpty();
    }

    @Override
    public void perform() {
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
                newStart = new Edge(convertSlope(startSlope),
                        new Coord(midX + (midX - sr.getX()), sr.getY()),
                        new Coord(midX + (midX - sl.getX()), sl.getY()));
                newEnd = new Edge(convertSlope(endSlope),
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

            newStrips.add(new Strip(newStart, newEnd, s.getWidth(), fence.getType()));
        }

        fence.getStrips().removeAll(oldStrips);
        fence.getStrips().addAll(newStrips);
        State.selectedStrips.removeAll(oldStrips);
        State.selectedStrips.addAll(newStrips);
    }

    @Override
    public void revert() {
        fence.getStrips().removeAll(newStrips);
        fence.getStrips().addAll(oldStrips);
        State.selectedStrips.removeAll(newStrips);
        State.selectedStrips.addAll(oldStrips);
    }

    private int convertSlope(int i) {
        if (i == 1) return 0;
        if (i == 0) return 1;
        if (i == 2) return 2;
        if (i == 3) return 3;
        return -1;
    }
}
