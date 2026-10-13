package com.example.tejpphone.function;

import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Strip;

import java.util.HashSet;

public class AddStripModification extends Modification {

    private final Fence fence;
    private final Strip strip;
    private HashSet<Strip> oldSelStrips = new HashSet<>();

    public AddStripModification(Fence f) {
        fence = f;
        strip = State.activeStrip;
    }

    @Override
    public void perform() {
        fence.getStrips().add(strip);
        oldSelStrips = new HashSet<>(State.selectedStrips);
        State.selectedStrips.clear();
    }

    @Override
    public void revert() {
        fence.getStrips().remove(strip);
        State.selectedStrips = new HashSet<>(oldSelStrips);
    }

    /**
     * A tap without dragging gives a strip that starts and ends on the same edge. The desktop
     * added these, but they are invisible, and on a phone taps happen all the time.
     */
    @Override
    public boolean isValid() {
        return strip.getStart() != strip.getEnd();
    }
}
