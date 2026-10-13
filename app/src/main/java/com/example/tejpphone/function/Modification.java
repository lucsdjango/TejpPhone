package com.example.tejpphone.function;

/** An undoable edit. */
public abstract class Modification {

    public abstract void perform();

    public abstract void revert();

    public abstract boolean isValid();

    /**
     * Re-applies the edit after revert(). By default the same as perform(), but edits that happen
     * live during a drag, such as moving strips, have nothing left to do in perform() and override this.
     */
    public void redo() {
        perform();
    }

    /**
     * Rolls back live changes made during a touch gesture that was interrupted before perform(),
     * for example when a second finger lands to pinch-zoom. New for the phone port.
     */
    public void cancel() {}

    /** False for edits that only change the selection, which is not saved in files. */
    public boolean changesDrawing() {
        return true;
    }
}
