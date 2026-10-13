package com.example.tejpphone.function;

import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.NormalFence;
import com.example.tejpphone.model.Picture;
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

    /** onDown without a touch radius, for tests. */
    public boolean onDown(float x, float y) {
        return onDown(x, y, 0f);
    }

    @Override
    public boolean onDown(float x, float y, float touchRadius) {
        if (State.getMode() == State.Mode.PICTURE) return pictureDown(x, y, touchRadius);

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
        if (State.activeModification instanceof MovePicModification) {
            ((MovePicModification) State.activeModification).moveTo(x, y);
            repaint.run();
            return;
        }
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

    /**
     * Desktop MOVINGPIC press. The press must land on the shown picture, or on one of its corner
     * handles, which are new on the phone. Unlike the other tools, this works outside the grid too,
     * since the picture can stick out past it.
     */
    private boolean pictureDown(float x, float y, float touchRadius) {
        Picture p = fence().getPicture();
        if (p == null || !State.showingPic) return false;

        double[] c = p.getCorners();
        double left = c[0] * S, top = c[1] * S, right = c[2] * S, bottom = c[3] * S;

        int handle = -1;
        double best = touchRadius;
        double[][] corners = {{left, top}, {right, top}, {left, bottom}, {right, bottom}};
        for (int i = 0; i < 4; i++) {
            double d = Math.hypot(x - corners[i][0], y - corners[i][1]);
            if (d <= best) {
                best = d;
                handle = i;
            }
        }
        boolean inside = x >= left && x <= right && y >= top && y <= bottom;
        if (handle < 0 && !inside) return false;

        State.activeModification = new MovePicModification(p, x, y, handle);
        repaint.run();
        return true;
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

            case PICTURE:
                break;   // Handled in pictureDown.
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
        endAutoSession();
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
        endAutoSession();
        repaint.run();
    }

    // ------------------------------------------------------------------ picture

    /**
     * Desktop "pic1" button. A replacement picture keeps the old one's position,
     * and a first picture starts at cells (2, 2) to (12, 12), as on the desktop.
     */
    public void loadPicture(int[] argb, int width, int height) {
        Picture old = fence().getPicture();
        double[] c = old != null ? old.getCorners() : new double[]{2, 2, 12, 12};
        Picture p = Picture.fromPixels(width, height, argb, c[0], c[1], c[2], c[3]);
        Modification mod = new SetPictureModification(fence(), p);
        mod.perform();
        History.addAction(mod);
        State.showingPic = true;
        repaint.run();
    }

    public void removePicture() {
        Modification mod = new SetPictureModification(fence(), null);
        mod.perform();
        History.addAction(mod);
        repaint.run();
    }

    /** Desktop "pic2" button. */
    public void setShowingPic(boolean showing) {
        State.showingPic = showing;
        endAutoSession();
        repaint.run();
    }

    // ------------------------------------------------------------------ auto strips

    private final AutoStrips autoStrips = new AutoStrips();
    private AutoStripsModification autoSession;

    /**
     * Ends the current slider session, so the next slider change starts a new undo step from
     * the current strips. The desktop ended it on every button press.
     */
    private void endAutoSession() {
        autoSession = null;
    }

    /** Desktop "hor", "diag" and "vert" buttons. */
    public void setAutoDirection(State.AutoDirection d) {
        State.autoDirection = d;
        endAutoSession();
    }

    /**
     * Desktop slider change: recomputes the auto strips for the current direction.
     *
     * <p>Slider changes in a row form one undo step, like the desktop's AutoStripsModification.
     * The session ends as soon as anything else lands in the undo history. Each change starts from
     * the strips as they were when the session began, so strips never pile up. The desktop piled
     * them up for diagonal strips on normal fences, where its clearing was commented out.
     *
     * @return false if there is no usable picture, or the fence type has no auto strips
     */
    public boolean runAutoStrips() {
        Fence f = fence();
        Picture p = f.getPicture();
        if (p == null || !p.hasPixels() || AutoStrips.kindFor(f, State.autoDirection) == null) return false;

        if (autoSession == null || autoSession.getFence() != f || History.peekLast() != autoSession) {
            autoSession = new AutoStripsModification(f);
            History.addAction(autoSession);
        }
        int d = State.autoDirection.ordinal();
        autoStrips.apply(f, autoSession.getOldStrips(), p, State.autoDirection,
                State.autoThreshold[d], State.autoContrast[d], State.inverted);
        State.selectedStrips.retainAll(f.getStrips());
        repaint.run();
        return true;
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
