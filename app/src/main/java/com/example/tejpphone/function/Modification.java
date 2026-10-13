package com.example.tejpphone.function;

/** An undoable edit. */
public abstract class Modification {

    public abstract void perform();

    public abstract void revert();

    public abstract boolean isValid();

    /**
     * Rolls back live changes made during a touch gesture that was interrupted before perform(),
     * for example when a second finger lands to pinch-zoom. New for the phone port.
     */
    public void cancel() {}
}
