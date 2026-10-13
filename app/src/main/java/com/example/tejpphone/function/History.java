package com.example.tejpphone.function;

import java.util.LinkedList;

/** Undo stack. */
public final class History {

    private History() {}

    private static final LinkedList<Modification> modifications = new LinkedList<>();

    public static void addAction(Modification a) {
        if (a.isValid()) modifications.add(a);
    }

    /** Removes and returns the latest modification, or null if there is none. */
    public static Modification lastMod() {
        return modifications.isEmpty() ? null : modifications.removeLast();
    }

    /** The latest modification without removing it, or null. */
    public static Modification peekLast() {
        return modifications.isEmpty() ? null : modifications.getLast();
    }

    public static void clear() {
        modifications.clear();
    }
}
