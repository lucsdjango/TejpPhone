package com.example.tejpphone.function;

import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Strip;

import java.util.HashSet;

public class DeleteStripsModification extends Modification {

    private final HashSet<Strip> strips;
    private final Fence fence;

    public DeleteStripsModification(Fence f) {
        strips = State.selectedStrips;
        fence = f;
    }

    @Override
    public void perform() {
        fence.getStrips().removeAll(strips);
    }

    @Override
    public void revert() {
        fence.getStrips().addAll(strips);
    }

    @Override
    public boolean isValid() {
        return !strips.isEmpty();
    }
}
