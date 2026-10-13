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
    @SuppressWarnings("unchecked")
    public void perform() {
        fence.getStrips().add(strip);
        oldSelStrips = (HashSet<Strip>) State.selectedStrips.clone();
        State.selectedStrips.clear();
    }

    @Override
    public void revert() {
        fence.getStrips().remove(strip);
        State.selectedStrips.addAll(oldSelStrips);
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
