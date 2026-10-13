package com.example.tejpphone.model;

import java.util.Iterator;
import java.util.LinkedList;

/** Sorts strips into layers by which strips end on top of others. Ported unchanged. */
public class Layer {

    private Layer subLayer = null;
    private final LinkedList<Strip> strips;

    public Layer(LinkedList<Strip> ss) {
        strips = ss;
    }

    public Layer() {
        this(new LinkedList<>());
    }

    public void addStrip(Strip s) {
        boolean hasToBeAdded = true;
        Iterator<Strip> i = strips.iterator();
        while (i.hasNext()) {
            Strip s0 = i.next();

            if ((s0.getStart().equals(s.getStart()) && s0.getEnd().equals(s.getEnd()))
                    || (s0.getStart().equals(s.getEnd()) && s0.getStart().equals(s.getStart()))) {
                if (s0.getWidth() >= s.getWidth()) {
                    hasToBeAdded = false;
                    break;
                } else {
                    i.remove();
                    continue;
                }
            }

            if (s0.intersects(s)) {
                if (subLayer == null) subLayer = new Layer();
                subLayer.addStrip(s);
                hasToBeAdded = false;
            } else if (s.intersects(s0)) {
                if (subLayer == null) subLayer = new Layer();
                subLayer.addStrip(s0);
                i.remove();
            }
        }
        if (hasToBeAdded) strips.add(s);
    }

    public LinkedList<Strip[]> getLayers() {
        LinkedList<Strip[]> stripss = new LinkedList<>();
        if (subLayer != null) stripss = subLayer.getLayers();
        stripss.addFirst(strips.toArray(new Strip[0]));
        return stripss;
    }
}
