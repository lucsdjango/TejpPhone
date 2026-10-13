package com.example.tejpphone.function;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Undo and redo stacks, and whether the drawing has changed since it was last saved or loaded.
 * The desktop app had undo only.
 */
public final class History {

    private History() {}

    private static final LinkedList<Modification> undo = new LinkedList<>();
    private static final LinkedList<Modification> redo = new LinkedList<>();

    /** The newest drawing change when the file was last saved or loaded. Null for none. */
    private static Modification savedAt = null;
    /** Set when restoring an autosave of a drawing that had unsaved changes. */
    private static boolean forceDirty = false;

    /** Records a modification that has been performed. A new action clears the redo stack. */
    public static void addAction(Modification a) {
        if (!a.isValid()) return;
        undo.add(a);
        redo.clear();
    }

    /** The latest modification without removing it, or null. */
    public static Modification peekLast() {
        return undo.isEmpty() ? null : undo.getLast();
    }

    public static boolean canUndo() {
        return !undo.isEmpty();
    }

    public static boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Reverts the latest modification. Returns false if there is nothing to undo. */
    public static boolean undo() {
        if (undo.isEmpty()) return false;
        Modification m = undo.removeLast();
        m.revert();
        redo.add(m);
        return true;
    }

    /** Re-applies the latest undone modification. Returns false if there is nothing to redo. */
    public static boolean redo() {
        if (redo.isEmpty()) return false;
        Modification m = redo.removeLast();
        m.redo();
        undo.add(m);
        return true;
    }

    /** Forgets everything, for a new or loaded drawing. That drawing counts as saved. */
    public static void clear() {
        undo.clear();
        redo.clear();
        savedAt = null;
        forceDirty = false;
    }

    /**
     * Identifies the drawing as it is now. Take it when a save starts, and pass it to markSaved
     * when the save has finished, so edits made meanwhile still count as unsaved.
     */
    public static Object currentState() {
        return lastDrawingChange();
    }

    /** Call when a save has finished. */
    public static void markSaved(Object state) {
        savedAt = (Modification) state;
        forceDirty = false;
    }

    /** For a restored autosave that had unsaved changes. */
    public static void markDirty() {
        forceDirty = true;
    }

    /** True if the drawing differs from the saved file. Selection changes do not count. */
    public static boolean isDirty() {
        return forceDirty || lastDrawingChange() != savedAt;
    }

    private static Modification lastDrawingChange() {
        Iterator<Modification> i = undo.descendingIterator();
        while (i.hasNext()) {
            Modification m = i.next();
            if (m.changesDrawing()) return m;
        }
        return null;
    }
}
