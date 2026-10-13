package com.example.tejpphone.function;

import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Strip;

import java.util.LinkedList;

/**
 * One auto strips session: every slider change until something else is done.
 * Undo returns the strips to how they were before the session. Port of the desktop class.
 */
public class AutoStripsModification extends Modification {

    private final Fence fence;
    private final LinkedList<Strip> oldStrips;

    public AutoStripsModification(Fence f) {
        fence = f;
        oldStrips = new LinkedList<>(f.getStrips());
    }

    public Fence getFence() {
        return fence;
    }

    /** Strips as they were when the session started. Each slider change starts from these. */
    public LinkedList<Strip> getOldStrips() {
        return oldStrips;
    }

    @Override
    public void perform() {}

    @Override
    public void revert() {
        fence.getStrips().clear();
        fence.getStrips().addAll(oldStrips);
        State.selectedStrips.retainAll(oldStrips);
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
