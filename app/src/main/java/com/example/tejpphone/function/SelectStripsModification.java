package com.example.tejpphone.function;

import com.example.tejpphone.model.Strip;

import java.util.HashSet;
import java.util.Set;

/** Changes the selection. Replaces it unless Ctrl is on. */
public class SelectStripsModification extends Modification {

    private final HashSet<Strip> oldSelectedStrips;
    private final HashSet<Strip> strips = new HashSet<>();

    @SuppressWarnings("unchecked")
    public SelectStripsModification() {
        oldSelectedStrips = (HashSet<Strip>) State.selectedStrips.clone();
        if (!State.ctrlPressed) State.selectedStrips.clear();
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
    }

    @Override
    public void revert() {
        State.selectedStrips = oldSelectedStrips;
        strips.clear();
    }

    @Override
    public void cancel() {
        State.selectedStrips = oldSelectedStrips;
        strips.clear();
    }

    @Override
    public boolean isValid() {
        return !(oldSelectedStrips.containsAll(State.selectedStrips)
                && State.selectedStrips.containsAll(oldSelectedStrips));
    }

    public HashSet<Strip> getStrips() {
        return strips;
    }
}
