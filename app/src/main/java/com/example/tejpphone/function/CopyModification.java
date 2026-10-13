package com.example.tejpphone.function;

import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Strip;

import java.util.HashSet;

/** Duplicates the selected strips in place and selects the copies. */
public class CopyModification extends Modification {

    private final HashSet<Strip> selStrips;
    private final HashSet<Strip> newStrips = new HashSet<>();
    private final Fence fence;

    public CopyModification(Fence f) {
        fence = f;
        selStrips = new HashSet<>(State.selectedStrips);
        for (Strip s : selStrips) {
            // The desktop app gave copies the default white. They now keep the original's colour.
            Strip sCopy = new Strip(s.getStart(), s.getEnd(), s.getWidth(), f.getType(), s.getColor());
            newStrips.add(sCopy);
            fence.addStrip(sCopy);
        }
    }

    @Override
    public boolean isValid() {
        return !newStrips.isEmpty();
    }

    @Override
    public void perform() {
        State.selectedStrips = new HashSet<>(newStrips);
    }

    @Override
    public void redo() {
        fence.getStrips().addAll(newStrips);
        perform();
    }

    @Override
    public void revert() {
        fence.getStrips().removeAll(newStrips);
        State.selectedStrips = new HashSet<>(selStrips);
    }
}
