package com.example.tejpphone.function;

import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.NormalFence;
import com.example.tejpphone.model.Strip;
import com.example.tejpphone.model.VerticalFence;
import com.example.tejpphone.view.FenceView;

import java.util.HashSet;
import java.util.LinkedList;

/**
 * Port of the desktop FenceListener, minus the auto* functions and all file and picture handling.
 * One finger on the canvas plays the role of the mouse. Toolbar chips replace the keyboard shortcuts.
 */
public class FenceController implements FenceView.TouchController {

    private static final int S = Fence.CELL_SIZE;

    private final Runnable repaint;

    public FenceController(Runnable repaint) {
        this.repaint = repaint;
    }

    private Fence fence() {
        return State.fence;
    }

    // ------------------------------------------------------------------ touch (was mouse)

    @Override
    public boolean onDown(float x, float y) {
        Fence f = fence();
        int cx = (int) Math.floor(x / S);
        int cy = (int) Math.floor(y / S);
        if (cx < 0 || cy < 0 || cx >= f.width || cy >= f.height) return false;

        Edge e = f.getEdgeAt(cx, cy);
        if (e != null) edgeEntered(e);
        if (State.selectedEdge == null) return false;

        mousePressed(x, y);
        repaint.run();
        return true;
    }

    @Override
    public void onMove(float x, float y) {
        Edge e = fence().edgeAtOrNull((int) Math.floor(x / S), (int) Math.floor(y / S));
        if (e != null) edgeEntered(e);
        mouseDragged(x, y);
        repaint.run();
    }

    @Override
    public void onUp() {
        mouseReleased();
        repaint.run();
    }

    @Override
    public void onCancel() {
        if (State.activeModification != null) State.activeModification.cancel();
        State.activeModification = null;
        State.activeStrip = null;
        repaint.run();
    }

    /** Desktop mouseEntered on an EdgePanel. */
    private void edgeEntered(Edge edge) {
        if (edge == State.selectedEdge) return;

        if (State.selectedEdge != null) State.selectedEdge.unselect();
        edge.select();
        State.selectedEdge = edge;

        if (State.activeStrip != null && State.getMode() == State.Mode.DRAWING) {
            State.activeStrip.setEnd(edge);
        }
        if (State.activeModification instanceof MoveModification && State.getMode() == State.Mode.MOVING) {
            ((MoveModification) State.activeModification).move(edge);
        }
    }

    private void mousePressed(float x, float y) {
        Edge edge = State.selectedEdge;

        switch (State.getMode()) {
            case DRAWING:
                if (!State.shiftPressed) {
                    State.activeStrip = new Strip(edge, edge, State.currentStripWidth,
                            fence().getType(), State.currentColor);
                    State.activeModification = new AddStripModification(fence());
                } else {
                    for (Strip s : fence().getStrips()) {
                        if (s.edgeSelected(edge)) State.activeStrip = s;
                    }
                    if (State.activeStrip != null) State.activeModification = new EdgeMovedModification();
                }
                break;

            case EDITING:
                State.activeModification = new SelectStripsModification();
                addStripsAt(x, y);
                break;

            case MOVING:
                State.activeModification = new MoveModification(edge);
                break;
        }
    }

    private void mouseDragged(float x, float y) {
        if (State.getMode() == State.Mode.EDITING && State.shiftPressed
                && State.activeModification instanceof SelectStripsModification) {
            addStripsAt(x, y);
        }
    }

    private void mouseReleased() {
        if (State.activeModification != null) {
            State.activeModification.perform();
            History.addAction(State.activeModification);
            State.activeModification = null;
            State.activeStrip = null;
        }
    }

    private void addStripsAt(float x, float y) {
        HashSet<Strip> strips = new HashSet<>();
        for (Strip strip : fence().getStrips()) {
            if (strip.contains(x, y)) strips.add(strip);
        }
        ((SelectStripsModification) State.activeModification).addStripToSelection(strips);
    }

    // ------------------------------------------------------------------ toolbar (was buttons and keys)

    /** Draw / Edit / Move tool buttons. */
    public void setMode(State.Mode mode) {
        State.setMode(mode);
        if (mode == State.Mode.DRAWING) clearSelectedStrips();
        repaint.run();
    }

    /** Key z. */
    public void undo() {
        Modification m = History.lastMod();
        if (m != null) m.revert();
        repaint.run();
    }

    /** Up arrow: selected strips go to the bottom of the stack. */
    public void moveToBack() {
        if (State.activeModification == null) {
            MoveBackModification mod = new MoveBackModification(fence().getStrips());
            mod.perform();
            History.addAction(mod);
        }
        repaint.run();
    }

    /** Down arrow: selected strips go to the top of the stack. */
    public void moveToFront() {
        if (State.activeModification == null) {
            MoveFrontModification mod = new MoveFrontModification(fence().getStrips());
            mod.perform();
            History.addAction(mod);
        }
        repaint.run();
    }

    /** Key A. */
    public void selectAll() {
        if (State.activeModification == null) {
            SelectStripsModification mod = new SelectStripsModification();
            mod.addStripToSelection(new HashSet<>(fence().getStrips()));
            mod.perform();
            History.addAction(mod);
        }
        repaint.run();
    }

    /** Key s: select strips with the current width. */
    public void selectSameWidth() {
        if (State.activeModification == null) {
            SelectStripsModification mod = new SelectStripsModification();
            HashSet<Strip> match = new HashSet<>();
            for (Strip s : fence().getStrips()) {
                if (s.getWidth() == State.currentStripWidth) match.add(s);
            }
            mod.addStripToSelection(match);
            mod.perform();
            History.addAction(mod);
        }
        repaint.run();
    }

    /** Key f: select strips with the current colour. */
    public void selectSameColor() {
        if (State.activeModification == null) {
            SelectStripsModification mod = new SelectStripsModification();
            HashSet<Strip> match = new HashSet<>();
            for (Strip s : fence().getStrips()) {
                if (s.getColor() == State.currentColor) match.add(s);
            }
            mod.addStripToSelection(match);
            mod.perform();
            History.addAction(mod);
        }
        repaint.run();
    }

    /** Key L: select the most recently drawn strip. */
    public void selectLast() {
        LinkedList<Strip> strips = fence().getStrips();
        if (State.activeModification == null && !strips.isEmpty()) {
            SelectStripsModification mod = new SelectStripsModification();
            mod.addStripToSelection(strips.getLast());
            mod.perform();
            History.addAction(mod);
        }
        repaint.run();
    }

    /** Key d: deselect everything (keeps the selection if Ctrl is on, as on the desktop). */
    public void deselectAll() {
        if (State.activeModification == null) {
            SelectStripsModification mod = new SelectStripsModification();
            mod.perform();
            History.addAction(mod);
        }
        repaint.run();
    }

    /** Delete key. */
    public void deleteSelected() {
        Modification mod = new DeleteStripsModification(fence());
        mod.perform();
        History.addAction(mod);
        repaint.run();
    }

    /** Key c. */
    public void copySelected() {
        Modification mod = new CopyModification(fence());
        mod.perform();
        History.addAction(mod);
        repaint.run();
    }

    /** Key m. */
    public void mirrorSelected() {
        Modification mod = new MirrorModification(fence());
        mod.perform();
        History.addAction(mod);
        repaint.run();
    }

    /** Width menu: sets the drawing width and applies it to the selected strips. */
    public void setWidth(float w) {
        State.currentStripWidth = w;
        Modification mod = new ChangeWidthModification(w);
        mod.perform();
        History.addAction(mod);
        repaint.run();
    }

    /** Colour menu: sets the drawing colour and applies it to the selected strips. */
    public void setColor(int color) {
        State.currentColor = color;
        Modification mod = new ChangeColorModification(color);
        mod.perform();
        History.addAction(mod);
        repaint.run();
    }

    /** Invert button. */
    public void setInverted(boolean inverted) {
        State.inverted = inverted;
        repaint.run();
    }

    /** "normal" button: a new diagonal fence the size of the current one. */
    public void newNormalFence() {
        replaceFence(new NormalFence(fence().width, fence().height));
        forgetFile();
    }

    /** "vertical" button: a new 33 x 21 vertical fence, as on the desktop. */
    public void newVerticalFence() {
        replaceFence(new VerticalFence(33, 21));
        forgetFile();
    }

    /** Shows a fence read from a file. */
    public void loadFence(Fence f, String uri, String name) {
        replaceFence(f);
        State.fileUri = uri;
        State.fileName = name;
    }

    /** So that Save cannot overwrite the old file with a new drawing. */
    private void forgetFile() {
        State.fileUri = null;
        State.fileName = null;
    }

    /**
     * The desktop app kept the undo history and selection when it swapped fences.
     * Undoing into the old fence could then crash on Android, so both are cleared here.
     */
    private void replaceFence(Fence f) {
        if (State.selectedEdge != null) State.selectedEdge.unselect();
        State.fence = f;
        State.selectedEdge = null;
        State.activeStrip = null;
        State.activeModification = null;
        State.selectedStrips = new HashSet<>();
        History.clear();
        repaint.run();
    }

    private void clearSelectedStrips() {
        Modification mod = new SelectStripsModification();
        State.selectedStrips.clear();
        mod.perform();
        History.addAction(mod);
    }
}
