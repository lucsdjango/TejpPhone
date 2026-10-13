package com.example.tejpphone.function;

import com.example.tejpphone.model.Strip;

import java.util.HashMap;
import java.util.Map;

public class ChangeWidthModification extends Modification {

    private final HashMap<Strip, Float> strips = new HashMap<>();
    private final float width;

    public ChangeWidthModification(float f) {
        width = f;
        for (Strip s : State.selectedStrips) {
            strips.put(s, s.getWidth());
        }
    }

    @Override
    public boolean isValid() {
        return !strips.isEmpty();
    }

    @Override
    public void perform() {
        for (Strip s : strips.keySet()) s.setWidth(width);
    }

    @Override
    public void revert() {
        for (Map.Entry<Strip, Float> e : strips.entrySet()) {
            e.getKey().setWidth(e.getValue());
        }
    }
}
