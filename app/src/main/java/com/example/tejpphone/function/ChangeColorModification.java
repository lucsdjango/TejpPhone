package com.example.tejpphone.function;

import com.example.tejpphone.model.Strip;

import java.util.HashMap;
import java.util.Map;

public class ChangeColorModification extends Modification {

    private final HashMap<Strip, Integer> strips = new HashMap<>();
    private final int color;

    public ChangeColorModification(int col) {
        color = col;
        for (Strip s : State.selectedStrips) {
            strips.put(s, s.getColor());
        }
    }

    @Override
    public void perform() {
        for (Strip s : strips.keySet()) s.setColor(color);
    }

    @Override
    public void revert() {
        for (Map.Entry<Strip, Integer> e : strips.entrySet()) {
            e.getKey().setColor(e.getValue());
        }
    }

    @Override
    public boolean isValid() {
        return !strips.isEmpty();
    }
}
