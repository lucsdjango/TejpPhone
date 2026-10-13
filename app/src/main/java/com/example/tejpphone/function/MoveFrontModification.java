package com.example.tejpphone.function;

import com.example.tejpphone.model.Strip;

import java.util.LinkedList;

/** Moves the selected strips to the top of the stack (desktop: down arrow). */
public class MoveFrontModification extends Modification {

    private final LinkedList<Strip> oldStrips;
    private final LinkedList<Strip> strips;
    private LinkedList<Strip> newStrips;

    public MoveFrontModification(LinkedList<Strip> currentStrips) {
        strips = currentStrips;
        oldStrips = new LinkedList<>(strips);
    }

    @Override
    public void perform() {
        for (Strip strip : State.selectedStrips) {
            if (strips.remove(strip)) strips.addLast(strip);
        }
        newStrips = new LinkedList<>(strips);
    }

    @Override
    public void redo() {
        strips.clear();
        strips.addAll(newStrips);
    }

    @Override
    public void revert() {
        strips.clear();
        strips.addAll(oldStrips);
    }

    @Override
    public boolean isValid() {
        return !oldStrips.equals(newStrips);
    }
}
