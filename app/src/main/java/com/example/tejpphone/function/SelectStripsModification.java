package com.example.tejpphone.function;

import com.example.tejpphone.model.Strip;

import java.util.HashSet;
import java.util.Set;

/**
 * Replaces the selection, for the Select all, Select none, Select last, Same width and
 * Same colour actions. The desktop kept the old selection while Ctrl was held. On the phone,
 * tapping strips in Edit mode adds and removes them instead, see SelectionChange.
 */
public class SelectStripsModification extends Modification {

    private final HashSet<Strip> before;
    private final HashSet<Strip> strips = new HashSet<>();
    private HashSet<Strip> after;

    public SelectStripsModification() {
        before = new HashSet<>(State.selectedStrips);
        State.selectedStrips.clear();
    }

    public void addStripToSelection(Set<Strip> ss) {
        strips.addAll(ss);
    }

    public void addStripToSelection(Strip strip) {
        strips.add(strip);
    }

    @Override
    public void perform() {
        State.selectedStrips.addAll(strips);
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
