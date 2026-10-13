package com.example.tejpphone.function;

import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Strip;

import java.util.HashSet;
import java.util.LinkedList;

/**
 * Deletes the selected strips.
 *
 * <p>The desktop version kept a reference to the live selection set instead of a copy.
 * Any later selection change emptied it, so undo restored nothing. It also left the deleted
 * strips selected, so a later Copy or Mirror could bring them back. Here the strips are copied,
 * removed from the selection, and undo puts them back where they were in the stacking order.
 */
public class DeleteStripsModification extends Modification {

    private final HashSet<Strip> strips;
    private final LinkedList<Strip> oldOrder;
    private final Fence fence;

    @SuppressWarnings("unchecked")
    public DeleteStripsModification(Fence f) {
        fence = f;
        strips = (HashSet<Strip>) State.selectedStrips.clone();
        oldOrder = new LinkedList<>(f.getStrips());
    }

    @Override
    public void perform() {
        fence.getStrips().removeAll(strips);
        State.selectedStrips.removeAll(strips);
    }

    @Override
    public void revert() {
        // Undo runs newest first, so the list is back to how perform() left it.
        fence.getStrips().clear();
        fence.getStrips().addAll(oldOrder);
        State.selectedStrips.addAll(strips);
    }

    @Override
    public boolean isValid() {
        return !strips.isEmpty();
    }
}
