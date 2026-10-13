package com.example.tejpphone;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.tejpphone.function.FenceController;
import com.example.tejpphone.function.History;
import com.example.tejpphone.function.State;
import com.example.tejpphone.model.AbnormalFence;
import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.NormalFence;
import com.example.tejpphone.model.Picture;
import com.example.tejpphone.model.Strip;
import com.example.tejpphone.model.VerticalFence;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

public class PictureAndAutoStripsTest {

    private static final int S = Fence.CELL_SIZE;

    private FenceController controller;

    @Before
    public void reset() {
        State.fence = new NormalFence(20, 20);
        State.selectedStrips = new HashSet<>();
        State.selectedEdge = null;
        State.activeStrip = null;
        State.activeModification = null;
        State.shiftPressed = false;
        State.ctrlPressed = false;
        State.inverted = false;
        State.showingPic = true;
        State.autoDirection = State.AutoDirection.HORIZONTAL;
        Arrays.fill(State.autoThreshold, 1f);
        Arrays.fill(State.autoContrast, 1f);
        History.clear();
        controller = new FenceController(() -> {});
    }

    /** A picture of one colour covering the whole fence. */
    private void fill(int argb) {
        int[] px = new int[16];
        Arrays.fill(px, argb);
        State.fence.setPicture(Picture.fromPixels(4, 4, px, 0, 0, State.fence.width, State.fence.height));
    }

    private void settings(State.AutoDirection d, float threshold, float contrast) {
        State.autoDirection = d;
        State.autoThreshold[d.ordinal()] = threshold;
        State.autoContrast[d.ordinal()] = contrast;
    }

    private static boolean horizontal(Strip s) {
        return Math.min(s.getStart().getLeft().getY(), s.getStart().getRight().getY())
                == Math.min(s.getEnd().getLeft().getY(), s.getEnd().getRight().getY());
    }

    // ------------------------------------------------------------------ auto strips

    @Test
    public void brightPictureGivesThickHorizontalStrips() {
        fill(0xFFFFFFFF);
        settings(State.AutoDirection.HORIZONTAL, 0.5f, 1f);
        assertTrue(controller.runAutoStrips());

        assertTrue(State.fence.getStrips().size() >= 15);
        for (Strip s : State.fence.getStrips()) {
            assertEquals(4.51f, s.getWidth(), 0f);
            assertEquals(Strip.WHITE, s.getColor());
            assertTrue(horizontal(s));
        }
    }

    @Test
    public void darkPictureGivesNothingUnlessInverted() {
        fill(0xFF000000);
        settings(State.AutoDirection.HORIZONTAL, 0.5f, 1f);
        controller.runAutoStrips();
        assertTrue(State.fence.getStrips().isEmpty());

        controller.setInverted(true);
        controller.runAutoStrips();
        assertFalse(State.fence.getStrips().isEmpty());
    }

    @Test
    public void contrastGivesThinStrips() {
        fill(0xFF808080);   // brightness about 0.5
        settings(State.AutoDirection.HORIZONTAL, 0.6f, 0.5f);
        controller.runAutoStrips();

        assertFalse(State.fence.getStrips().isEmpty());
        for (Strip s : State.fence.getStrips()) assertEquals(1.51f, s.getWidth(), 0f);
    }

    @Test
    public void verticalStripsOnNormalFence() {
        fill(0xFFFFFFFF);
        settings(State.AutoDirection.VERTICAL, 0.5f, 1f);
        controller.runAutoStrips();

        assertFalse(State.fence.getStrips().isEmpty());
        for (Strip s : State.fence.getStrips()) {
            assertEquals(4.5f, s.getWidth(), 0f);
            assertEquals(s.getStart().getLeft().getX(), s.getEnd().getLeft().getX());
        }
    }

    @Test
    public void diagonalStripsDoNotPileUpWhileTheSliderMoves() {
        fill(0xFFFFFFFF);
        settings(State.AutoDirection.DIAGONAL, 0.5f, 1f);
        controller.runAutoStrips();
        int first = State.fence.getStrips().size();
        assertTrue(first > 0);
        for (Strip s : State.fence.getStrips()) assertEquals(5f, s.getWidth(), 0f);

        // The desktop added a full new set on every slider tick here.
        controller.runAutoStrips();
        controller.runAutoStrips();
        assertEquals(first, State.fence.getStrips().size());
    }

    @Test
    public void sliderSessionIsOneUndoStepAndKeepsColouredStrips() {
        Fence f = State.fence;
        Strip red = new Strip(f.getEdgeAt(2, 4), f.getEdgeAt(8, 4), 1f, NormalFence.TYPE, State.RED);
        f.addStrip(red);
        fill(0xFFFFFFFF);

        settings(State.AutoDirection.HORIZONTAL, 0.5f, 1f);
        controller.runAutoStrips();
        settings(State.AutoDirection.HORIZONTAL, 0.4f, 1f);
        controller.runAutoStrips();
        assertTrue(f.getStrips().contains(red));   // Only white strips are cleared, as on the desktop.
        assertTrue(f.getStrips().size() > 1);

        controller.undo();
        assertEquals(1, f.getStrips().size());
        assertTrue(f.getStrips().contains(red));
    }

    @Test
    public void somethingElseInBetweenStartsANewSession() {
        fill(0xFFFFFFFF);
        settings(State.AutoDirection.HORIZONTAL, 0.5f, 1f);
        controller.runAutoStrips();
        int horizontal = State.fence.getStrips().size();

        controller.selectAll();
        settings(State.AutoDirection.VERTICAL, 0.5f, 1f);
        controller.runAutoStrips();
        int both = State.fence.getStrips().size();
        assertTrue(both > horizontal);

        controller.undo();   // the vertical session only
        assertEquals(horizontal, State.fence.getStrips().size());
    }

    @Test
    public void switchingDirectionKeepsTheStripsOfTheLastDirection() {
        fill(0xFFFFFFFF);
        settings(State.AutoDirection.HORIZONTAL, 0.5f, 1f);
        controller.runAutoStrips();
        int horizontal = State.fence.getStrips().size();

        controller.setAutoDirection(State.AutoDirection.DIAGONAL);
        settings(State.AutoDirection.DIAGONAL, 0.5f, 1f);
        controller.runAutoStrips();
        assertTrue(State.fence.getStrips().size() > horizontal);

        controller.undo();   // the diagonal session only
        assertEquals(horizontal, State.fence.getStrips().size());
    }

    @Test
    public void verticalFenceHorizontalAndDiagonal() {
        State.fence = new VerticalFence(33, 21);
        fill(0xFFFFFFFF);

        settings(State.AutoDirection.HORIZONTAL, 0.5f, 1f);
        assertTrue(controller.runAutoStrips());
        assertFalse(State.fence.getStrips().isEmpty());
        for (Strip s : State.fence.getStrips()) assertEquals(3f, s.getWidth(), 0f);

        controller.selectAll();   // ends the session
        settings(State.AutoDirection.DIAGONAL, 0.5f, 1f);
        assertTrue(controller.runAutoStrips());
        boolean anyDiagonal = false;
        for (Strip s : State.fence.getStrips()) anyDiagonal |= s.isDiag() && s.getStart() != s.getEnd();
        assertTrue(anyDiagonal);
    }

    @Test
    public void noAutoStripsWithoutPictureOrOnAbnormalFence() {
        assertFalse(controller.runAutoStrips());
        State.fence = new AbnormalFence(20, 20);
        fill(0xFFFFFFFF);
        assertFalse(controller.runAutoStrips());
    }

    // ------------------------------------------------------------------ picture

    @Test
    public void loadingAPictureCanBeUndone() {
        controller.loadPicture(new int[]{-1}, 1, 1);
        assertArrayEquals(new double[]{2, 2, 12, 12}, State.fence.getPicture().getCorners(), 0);
        controller.undo();
        assertNull(State.fence.getPicture());
    }

    @Test
    public void replacementPictureKeepsThePosition() {
        controller.loadPicture(new int[]{-1}, 1, 1);
        State.fence.getPicture().setCorners(1, 2, 3, 4);
        controller.loadPicture(new int[]{0xFF000000}, 1, 1);
        assertArrayEquals(new double[]{1, 2, 3, 4}, State.fence.getPicture().getCorners(), 0);
    }

    @Test
    public void dragInsideMovesThePictureAndUndoPutsItBack() {
        controller.loadPicture(new int[]{-1}, 1, 1);   // cells 2..12
        controller.setMode(State.Mode.PICTURE);

        assertTrue(controller.onDown(5 * S, 5 * S, 0));
        controller.onMove(7 * S, 6 * S);
        controller.onUp();
        assertArrayEquals(new double[]{4, 3, 14, 13}, State.fence.getPicture().getCorners(), 1e-9);

        controller.undo();
        assertArrayEquals(new double[]{2, 2, 12, 12}, State.fence.getPicture().getCorners(), 1e-9);
    }

    @Test
    public void cornerHandleResizesWithoutShift() {
        controller.loadPicture(new int[]{-1}, 1, 1);
        controller.setMode(State.Mode.PICTURE);

        // Just outside the top-left corner, but within the touch radius.
        assertTrue(controller.onDown(2 * S - 3, 2 * S - 3, 6));
        controller.onMove(-3, -3);
        controller.onUp();
        assertArrayEquals(new double[]{0, 0, 12, 12}, State.fence.getPicture().getCorners(), 1e-9);
    }

    @Test
    public void shiftDragMovesTheCornerOfThatQuarterAndCornersCannotCross() {
        controller.loadPicture(new int[]{-1}, 1, 1);
        controller.setMode(State.Mode.PICTURE);
        State.shiftPressed = true;

        controller.onDown(10 * S, 10 * S, 0);   // bottom-right quarter
        controller.onMove(-50 * S, -50 * S);     // far past the opposite corner
        controller.onUp();
        double[] c = State.fence.getPicture().getCorners();
        assertEquals(2, c[0], 1e-9);
        assertEquals(2, c[1], 1e-9);
        assertEquals(2 + Picture.MIN_CELLS, c[2], 1e-9);
        assertEquals(2 + Picture.MIN_CELLS, c[3], 1e-9);
    }

    @Test
    public void hiddenPictureCannotBeDragged() {
        controller.loadPicture(new int[]{-1}, 1, 1);
        controller.setMode(State.Mode.PICTURE);
        controller.setShowingPic(false);
        assertFalse(controller.onDown(5 * S, 5 * S, 0));
    }

    @Test
    public void removePictureCanBeUndone() {
        controller.loadPicture(new int[]{-1}, 1, 1);
        Picture p = State.fence.getPicture();
        controller.removePicture();
        assertNull(State.fence.getPicture());
        controller.undo();
        assertEquals(p, State.fence.getPicture());
    }
}
