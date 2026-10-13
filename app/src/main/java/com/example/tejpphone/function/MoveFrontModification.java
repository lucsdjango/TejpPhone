package com.example.tejpphone.function;

import com.example.tejpphone.model.Strip;

import java.util.LinkedList;

/** Moves the selected strips to the top of the stack (desktop: down arrow). */
public class MoveFrontModification extends Modification {

    private final LinkedList<Strip> oldStrips;
    private final LinkedList<Strip> strips;

    public MoveFrontModification(LinkedList<Strip> currentStrips) {
        strips = currentStrips;
        oldStrips = new LinkedList<>(strips);
    }

    @Override
    public void perform() {
        for (Strip strip : State.selectedStrips) {
            if (strips.remove(strip)) strips.addLast(strip);
        }
    }

    @Override
    public void revert() {
        strips.clear();
        strips.addAll(oldStrips);
    }

    @Override
    public boolean isValid() {
        return true;
    }
}
