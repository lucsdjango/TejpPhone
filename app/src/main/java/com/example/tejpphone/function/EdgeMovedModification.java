package com.example.tejpphone.function;

import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Strip;

import java.util.HashSet;

/** Drags the end of an existing strip to a new edge (Shift + draw). */
public class EdgeMovedModification extends Modification {

    private final Strip strip;
    private final Edge oldEdge;
    private Edge newEdge;
    private final HashSet<Strip> oldSelStrips;

    @SuppressWarnings("unchecked")
    public EdgeMovedModification() {
        oldSelStrips = (HashSet<Strip>) State.selectedStrips.clone();
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
    public void revert() {
        strip.edgeSelected(newEdge);
        strip.setEnd(oldEdge);
        State.selectedStrips.remove(strip);
        State.selectedStrips.addAll(oldSelStrips);
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
