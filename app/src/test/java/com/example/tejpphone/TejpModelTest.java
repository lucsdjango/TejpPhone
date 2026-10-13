package com.example.tejpphone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.tejpphone.function.FenceController;
import com.example.tejpphone.function.History;
import com.example.tejpphone.function.State;
import com.example.tejpphone.model.AbnormalFence;
import com.example.tejpphone.model.Edge;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.NormalFence;
import com.example.tejpphone.model.Strip;
import com.example.tejpphone.model.VerticalFence;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class TejpModelTest {

    private static final int S = Fence.CELL_SIZE;

    private FenceController controller;

    @Before
    public void reset() {
        State.fence = new NormalFence(20, 20);
        State.selectedStrips = new HashSet<>();
        State.selectedEdge = null;
        State.activeStrip = null;
        State.activeModification = null;
        State.boxSelect = false;
        State.selectionBox = null;
        State.currentStripWidth = 1f;
        State.currentColor = State.WHITE;
        State.setMode(State.Mode.DRAWING);
        History.clear();
        controller = new FenceController(() -> {});
    }

    /** Centre of a grid cell in content units, where a finger would touch to hit that edge. */
    private static float c(int cell) {
        return cell * S + S / 2f;
    }

    @Test
    public void everyEdgeMapsBackToItsCell() {
        Fence[] fences = {new NormalFence(9, 7), new VerticalFence(9, 7), new AbnormalFence(9, 7)};
        for (Fence f : fences) {
            for (int x = 0; x < f.width; x++) {
                for (int y = 0; y < f.height; y++) {
                    Edge e = f.getEdgeAt(x, y);
                    if (e == null) continue;
                    assertEquals(f.getType(), x, e.cellX());
                    assertEquals(f.getType(), y, e.cellY());
                }
            }
        }
    }

    @Test
    public void dragDrawsAStripAndUndoRemovesIt() {
        assertTrue(controller.onDown(c(2), c(4)));
        controller.onMove(c(5), c(4));
        controller.onMove(c(8), c(4));
        controller.onUp();

        List<Strip> strips = State.fence.getStrips();
        assertEquals(1, strips.size());
        assertSame(State.fence.getEdgeAt(2, 4), strips.get(0).getStart());
        assertSame(State.fence.getEdgeAt(8, 4), strips.get(0).getEnd());

        controller.undo();
        assertTrue(State.fence.getStrips().isEmpty());
    }

    @Test
    public void touchOutsideTheFenceDoesNothing() {
        assertFalse(controller.onDown(-5, -5));
        assertFalse(controller.onDown(c(25), c(3)));
        assertTrue(State.fence.getStrips().isEmpty());
    }

    @Test
    public void cancelledDrawAddsNothing() {
        controller.onDown(c(2), c(4));
        controller.onMove(c(8), c(4));
        controller.onCancel();
        assertTrue(State.fence.getStrips().isEmpty());
        assertEquals(null, State.activeModification);
    }

    private Strip draw(int x0, int y0, int x1, int y1) {
        controller.onDown(c(x0), c(y0));
        controller.onMove(c(x1), c(y1));
        controller.onUp();
        return State.fence.getStrips().getLast();
    }

    private void tap(float x, float y) {
        controller.onDown(x, y);
        controller.onUp(true);
    }

    @Test
    public void tapInDrawModeAddsNothing() {
        tap(c(4), c(4));
        assertTrue(State.fence.getStrips().isEmpty());
        assertFalse(History.canUndo());
    }

    @Test
    public void tapsInEditModeAddAndRemoveStrips() {
        Strip a = draw(2, 4, 8, 4);
        Strip b = draw(2, 10, 8, 10);
        controller.setMode(State.Mode.EDITING);

        tap(c(5), c(4));
        tap(c(5), c(10));
        assertTrue(State.selectedStrips.contains(a));
        assertTrue(State.selectedStrips.contains(b));

        tap(c(5), c(4));   // a second tap removes it again
        assertFalse(State.selectedStrips.contains(a));
        assertTrue(State.selectedStrips.contains(b));

        tap(c(5), c(16));  // empty space clears the selection
        assertTrue(State.selectedStrips.isEmpty());

        controller.undo();
        assertEquals(1, State.selectedStrips.size());
        controller.redo();
        assertTrue(State.selectedStrips.isEmpty());
    }

    @Test
    public void dragInEditModePaintsTheSelection() {
        Strip a = draw(2, 4, 8, 4);
        Strip b = draw(2, 10, 8, 10);
        Strip c = draw(12, 16, 18, 16);
        controller.setMode(State.Mode.EDITING);

        controller.onDown(c(5), c(4));
        controller.onMove(c(5), c(7));
        controller.onMove(c(5), c(10));
        controller.onUp(false);
        assertTrue(State.selectedStrips.contains(a));
        assertTrue(State.selectedStrips.contains(b));
        assertFalse(State.selectedStrips.contains(c));
    }

    @Test
    public void boxSelectPicksStripsInsideTheBox() {
        Strip a = draw(2, 4, 8, 4);
        Strip b = draw(2, 10, 8, 10);
        Strip c = draw(12, 16, 18, 16);
        controller.setMode(State.Mode.EDITING);
        controller.setBoxSelect(true);

        controller.onDown(c(1), c(2));
        controller.onMove(c(9), c(11));
        assertTrue(State.selectionBox != null);
        controller.onUp(false);
        assertEquals(new HashSet<>(Arrays.asList(a, b)), State.selectedStrips);
        assertTrue(State.selectionBox == null);
        assertFalse(State.selectedStrips.contains(c));
    }

    @Test
    public void moveIsBlockedAtTheBorderAndUndoRestoresPosition() {
        Strip s = new Strip(State.fence.getEdgeAt(2, 14), State.fence.getEdgeAt(8, 14), 1f, NormalFence.TYPE);
        State.fence.addStrip(s);
        State.selectedStrips.add(s);
        controller.setMode(State.Mode.MOVING);

        // Drag 4 rows down, which still fits on the 20-row fence.
        controller.onDown(c(10), c(2));
        controller.onMove(c(10), c(6));
        // Then try to push it 12 more rows, past the bottom. The desktop app threw here.
        controller.onMove(c(10), c(19));
        controller.onUp();

        assertSame(State.fence.getEdgeAt(2, 18), s.getStart());
        assertSame(State.fence.getEdgeAt(8, 18), s.getEnd());

        controller.undo();
        assertSame(State.fence.getEdgeAt(2, 14), s.getStart());
        assertSame(State.fence.getEdgeAt(8, 14), s.getEnd());
    }

    @Test
    public void copyMirrorDeleteAndSelectionsDoNotCrashOnEmptyFence() {
        controller.selectAll();
        controller.selectLast();
        controller.selectSameWidth();
        controller.selectSameColor();
        controller.deselectAll();
        controller.copySelected();
        controller.mirrorSelected();
        controller.deleteSelected();
        controller.moveToBack();
        controller.moveToFront();
        controller.undo();
        controller.undo();
        assertTrue(State.fence.getStrips().isEmpty());
    }

    @Test
    public void mirrorFlipsAroundTheSelectionCentre() {
        Strip s = new Strip(State.fence.getEdgeAt(2, 4), State.fence.getEdgeAt(6, 8), 1f, NormalFence.TYPE);
        State.fence.addStrip(s);
        State.selectedStrips.add(s);

        controller.mirrorSelected();
        Strip m = State.fence.getStrips().getFirst();
        // A down-right diagonal becomes a down-left one over the same columns.
        assertEquals(s.getStart().getLeft().getY(), m.getStart().getRight().getY());
        assertTrue(m.getEnd().getLeft().getX() < m.getStart().getLeft().getX());

        controller.undo();
        assertSame(s, State.fence.getStrips().getFirst());
    }

    @Test
    public void widthAndColourApplyToSelectionAndUndo() {
        Strip s = new Strip(State.fence.getEdgeAt(2, 4), State.fence.getEdgeAt(8, 4), 1f, NormalFence.TYPE);
        State.fence.addStrip(s);
        State.selectedStrips.add(s);

        controller.setWidth(4.5f);
        controller.setColor(State.RED);
        assertEquals(4.5f, s.getWidth(), 0f);
        assertEquals(State.RED, s.getColor());

        controller.undo();
        controller.undo();
        assertEquals(1f, s.getWidth(), 0f);
        assertEquals(State.WHITE, s.getColor());
    }

    @Test
    public void stripEndingOnAnotherGoesToASeparateLayer() {
        Fence f = State.fence;
        Strip a = new Strip(f.getEdgeAt(0, 4), f.getEdgeAt(8, 4), 1f, NormalFence.TYPE);
        Strip b = new Strip(f.getEdgeAt(4, 4), f.getEdgeAt(4, 8), 1f, NormalFence.TYPE);
        Strip far = new Strip(f.getEdgeAt(0, 15), f.getEdgeAt(8, 15), 1f, NormalFence.TYPE);
        f.addStrip(a);
        f.addStrip(b);
        f.addStrip(far);

        List<Fence> layers = f.getLayers();
        assertEquals(2, layers.size());
        assertTrue(layers.get(0).getStrips().contains(b));
        assertTrue(layers.get(1).getStrips().contains(a));
        assertTrue(layers.get(1).getStrips().contains(far));
    }

    @Test
    public void copyKeepsTheOriginalColour() {
        Strip s = new Strip(State.fence.getEdgeAt(2, 4), State.fence.getEdgeAt(8, 4), 1f,
                NormalFence.TYPE, State.RED);
        State.fence.addStrip(s);
        State.selectedStrips.add(s);

        controller.copySelected();
        Strip copy = State.fence.getStrips().getLast();
        assertTrue(copy != s);
        assertEquals(State.RED, copy.getColor());
    }

    @Test
    public void mirroredStripsUseGridEdgesWithTheFlippedSlope() {
        Fence f = State.fence;
        Strip s = new Strip(f.getEdgeAt(2, 4), f.getEdgeAt(6, 8), 1f, NormalFence.TYPE);
        f.addStrip(s);
        State.selectedStrips.add(s);

        controller.mirrorSelected();
        Strip m = f.getStrips().getFirst();
        for (Edge e : new Edge[]{m.getStart(), m.getEnd()}) {
            assertSame(f.getEdgeAt(e.cellX(), e.cellY()), e);
            assertEquals(1, e.getSlope());   // the original edges were "/" (slope 0)
        }

        // Mirroring again gives back the original shape and slopes. As on the desktop, the centre
        // is rounded to a whole column, so with an odd width the strip ends up two columns over.
        controller.mirrorSelected();
        Strip back = f.getStrips().getFirst();
        assertEquals(s.getStart().getSlope(), back.getStart().getSlope());
        assertEquals(s.getEnd().getSlope(), back.getEnd().getSlope());
        assertEquals(s.getEnd().cellX() - s.getStart().cellX(), back.getEnd().cellX() - back.getStart().cellX());
        assertEquals(s.getStart().cellY(), back.getStart().cellY());
        assertEquals(s.getEnd().cellY(), back.getEnd().cellY());
        assertSame(f.getEdgeAt(back.getStart().cellX(), back.getStart().cellY()), back.getStart());
    }

    @Test
    public void mirroredStripMovesWithoutChangingShape() {
        Fence f = State.fence;
        Strip s = new Strip(f.getEdgeAt(2, 4), f.getEdgeAt(6, 8), 1f, NormalFence.TYPE);
        f.addStrip(s);
        State.selectedStrips.add(s);
        controller.mirrorSelected();
        Strip m = f.getStrips().getFirst();
        int sx = m.getStart().cellX(), sy = m.getStart().cellY();
        int ex = m.getEnd().cellX(), ey = m.getEnd().cellY();
        int startSlope = m.getStart().getSlope(), endSlope = m.getEnd().getSlope();

        controller.setMode(State.Mode.MOVING);
        controller.onDown(c(10), c(10));
        controller.onMove(c(12), c(10));   // two cells right
        controller.onUp();

        // Before the fix, a mirrored strip jumped a row here and changed shape.
        assertEquals(sx + 2, m.getStart().cellX());
        assertEquals(sy, m.getStart().cellY());
        assertEquals(ex + 2, m.getEnd().cellX());
        assertEquals(ey, m.getEnd().cellY());
        assertEquals(startSlope, m.getStart().getSlope());
        assertEquals(endSlope, m.getEnd().getSlope());
    }

    @Test
    public void mirrorKeepsTheOriginalColour() {
        Strip s = new Strip(State.fence.getEdgeAt(2, 4), State.fence.getEdgeAt(6, 8), 1f,
                NormalFence.TYPE, State.BLUE);
        State.fence.addStrip(s);
        State.selectedStrips.add(s);

        controller.mirrorSelected();
        Strip m = State.fence.getStrips().getFirst();
        assertTrue(m != s);
        assertEquals(State.BLUE, m.getColor());
    }

    @Test
    public void undoDeleteWorksAfterTheSelectionChanged() {
        Fence f = State.fence;
        Strip a = new Strip(f.getEdgeAt(2, 4), f.getEdgeAt(8, 4), 1f, NormalFence.TYPE);
        Strip b = new Strip(f.getEdgeAt(2, 8), f.getEdgeAt(8, 8), 1f, NormalFence.TYPE);
        Strip c = new Strip(f.getEdgeAt(2, 12), f.getEdgeAt(8, 12), 1f, NormalFence.TYPE);
        f.addStrip(a);
        f.addStrip(b);
        f.addStrip(c);
        State.selectedStrips.add(b);

        controller.deleteSelected();
        assertEquals(2, f.getStrips().size());
        assertFalse(State.selectedStrips.contains(b));

        // On the desktop this emptied the delete's record, so the undo below restored nothing.
        controller.selectAll();
        controller.undo();   // undoes Select all
        controller.undo();   // undoes Delete

        assertEquals(3, f.getStrips().size());
        assertSame(b, f.getStrips().get(1));   // back in its old place in the stack
        assertTrue(State.selectedStrips.contains(b));
    }

    @Test
    public void deletedStripsCannotComeBackThroughCopyOrMirror() {
        Strip s = new Strip(State.fence.getEdgeAt(2, 4), State.fence.getEdgeAt(8, 4), 1f, NormalFence.TYPE);
        State.fence.addStrip(s);
        State.selectedStrips.add(s);

        controller.deleteSelected();
        controller.copySelected();
        controller.mirrorSelected();
        assertTrue(State.fence.getStrips().isEmpty());
    }

    @Test
    public void longPressOnAStripEndDragsThatEnd() {
        Strip s = new Strip(State.fence.getEdgeAt(2, 4), State.fence.getEdgeAt(8, 4), 1f, NormalFence.TYPE);
        State.fence.addStrip(s);

        controller.onDown(c(2), c(4));     // the start end
        assertTrue(controller.onLongPress());
        controller.onMove(c(0), c(4));
        controller.onUp();
        assertEquals(1, State.fence.getStrips().size());
        assertTrue(s.getStart() == State.fence.getEdgeAt(0, 4) || s.getEnd() == State.fence.getEdgeAt(0, 4));
        assertTrue(s.getStart() == State.fence.getEdgeAt(8, 4) || s.getEnd() == State.fence.getEdgeAt(8, 4));

        controller.undo();
        assertTrue(s.getStart() == State.fence.getEdgeAt(2, 4) || s.getEnd() == State.fence.getEdgeAt(2, 4));
        controller.redo();
        assertTrue(s.getStart() == State.fence.getEdgeAt(0, 4) || s.getEnd() == State.fence.getEdgeAt(0, 4));
    }

    @Test
    public void longPressAwayFromStripEndsDoesNothing() {
        controller.onDown(c(4), c(4));
        assertFalse(controller.onLongPress());
        controller.onCancel();
    }

    // ------------------------------------------------------------------ redo

    @Test
    public void redoAfterUndoForDrawMoveMirrorCopyDelete() {
        Strip s = draw(2, 14, 8, 14);
        controller.undo();
        assertTrue(State.fence.getStrips().isEmpty());
        controller.redo();
        assertEquals(1, State.fence.getStrips().size());

        State.selectedStrips.add(s);
        controller.setMode(State.Mode.MOVING);
        controller.onDown(c(10), c(2));
        controller.onMove(c(10), c(6));
        controller.onUp();
        assertSame(State.fence.getEdgeAt(2, 18), s.getStart());
        controller.undo();
        assertSame(State.fence.getEdgeAt(2, 14), s.getStart());
        controller.redo();
        assertSame(State.fence.getEdgeAt(2, 18), s.getStart());

        controller.mirrorSelected();
        Strip m = State.fence.getStrips().getFirst();
        controller.undo();
        controller.redo();
        assertSame(m, State.fence.getStrips().getFirst());
        assertEquals(1, State.fence.getStrips().size());

        controller.copySelected();
        assertEquals(2, State.fence.getStrips().size());
        controller.undo();
        controller.redo();
        assertEquals(2, State.fence.getStrips().size());

        controller.deleteSelected();
        assertEquals(1, State.fence.getStrips().size());
        controller.undo();
        controller.redo();
        assertEquals(1, State.fence.getStrips().size());
    }

    @Test
    public void newActionClearsRedo() {
        draw(2, 4, 8, 4);
        controller.undo();
        assertTrue(History.canRedo());
        draw(2, 8, 8, 8);
        assertFalse(History.canRedo());
    }

    @Test
    public void widthAndColourRedo() {
        Strip s = draw(2, 4, 8, 4);
        State.selectedStrips.add(s);
        controller.setColor(State.GREEN);
        controller.setWidth(3f);
        controller.undo();
        controller.undo();
        assertEquals(Strip.WHITE, s.getColor());
        controller.redo();
        controller.redo();
        assertEquals(State.GREEN, s.getColor());
        assertEquals(3f, s.getWidth(), 0f);
    }

    // ------------------------------------------------------------------ unsaved changes

    @Test
    public void unsavedChangesAreTrackedButSelectionDoesNotCount() {
        assertFalse(History.isDirty());
        Strip s = draw(2, 4, 8, 4);
        assertTrue(History.isDirty());

        History.markSaved(History.currentState());
        assertFalse(History.isDirty());

        controller.setMode(State.Mode.EDITING);
        tap(c(5), c(4));
        assertTrue(State.selectedStrips.contains(s));
        assertFalse(History.isDirty());   // selecting is not an edit

        controller.setMode(State.Mode.DRAWING);
        draw(2, 10, 8, 10);
        assertTrue(History.isDirty());
        controller.undo();
        controller.undo();   // the selection clearing when Draw was chosen
        assertFalse(History.isDirty());   // back where it was saved
    }

    @Test
    public void markDirtySurvivesUntilSaved() {
        History.markDirty();
        assertTrue(History.isDirty());
        History.markSaved(History.currentState());
        assertFalse(History.isDirty());
    }
}
