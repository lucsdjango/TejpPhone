package com.example.tejpphone.function;

import com.example.tejpphone.model.Strip;

import java.util.LinkedList;

/** Moves the selected strips to the bottom of the stack (desktop: up arrow). */
public class MoveBackModification extends Modification {

    private final LinkedList<Strip> oldStrips;
    private final LinkedList<Strip> strips;

    public MoveBackModification(LinkedList<Strip> currentStrips) {
        strips = currentStrips;
        oldStrips = new LinkedList<>(strips);
    }

    @Override
    public void perform() {
        for (Strip strip : State.selectedStrips) {
            if (strips.remove(strip)) strips.addFirst(strip);
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
