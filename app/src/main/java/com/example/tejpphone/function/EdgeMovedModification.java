package com.example.tejpphone.function;

import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Strip;

import java.util.HashSet;

/** Drags the end of an existing strip to a new edge (long press in Draw; Shift on the desktop). */
public class EdgeMovedModification extends Modification {

    private final Strip strip;
    private final Edge oldEdge;
    private Edge newEdge;
    private final HashSet<Strip> oldSelStrips;

    public EdgeMovedModification() {
        oldSelStrips = new HashSet<>(State.selectedStrips);
        strip = State.activeStrip;
        oldEdge = strip.getEnd();
    }

    @Override
    public void perform() {
        newEdge = State.selectedEdge;
        State.selectedStrips.clear();
        State.selectedStrips.add(strip);
    }

    @Override
    public void redo() {
        strip.edgeSelected(oldEdge);
        strip.setEnd(newEdge);
        State.selectedStrips.clear();
        State.selectedStrips.add(strip);
    }

    @Override
    public void revert() {
        strip.edgeSelected(newEdge);
        strip.setEnd(oldEdge);
        State.selectedStrips = new HashSet<>(oldSelStrips);
    }

    @Override
    public void cancel() {
        strip.setEnd(oldEdge);
    }

    @Override
    public boolean isValid() {
        return newEdge != oldEdge;
    }
}
