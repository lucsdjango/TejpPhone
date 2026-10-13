package com.example.tejpphone.function;

import com.example.tejpphone.model.Strip;

import java.util.HashSet;

/**
 * A selection made by touch in Edit mode: tap, paint or box. The selection is changed live
 * during the gesture; this records it before and after, for undo and redo.
 */
public class SelectionChange extends Modification {

    private final HashSet<Strip> before;
    private HashSet<Strip> after;

    public SelectionChange() {
        before = new HashSet<>(State.selectedStrips);
    }

    public HashSet<Strip> getBefore() {
        return before;
    }

    @Override
    public void perform() {
        after = new HashSet<>(State.selectedStrips);
    }

    @Override
    public void redo() {
        State.selectedStrips = new HashSet<>(after);
    }

    @Override
    public void revert() {
        State.selectedStrips = new HashSet<>(before);
    }

    @Override
    public void cancel() {
        revert();
    }

    @Override
    public boolean isValid() {
        return after != null && !after.equals(before);
    }

    @Override
    public boolean changesDrawing() {
        return false;
    }
}
