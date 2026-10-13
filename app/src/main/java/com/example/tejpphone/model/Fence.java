package com.example.tejpphone.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** The grid of edges plus the strips drawn on it. */
public abstract class Fence {

    /** Cell size in content units. Matches the desktop default EdgePanel.size of 12. */
    public static final int CELL_SIZE = 12;

    public final int width, height;
    protected final Edge[][] edges;
    protected LinkedList<Strip> strips;

    /**
     * Data from a loaded .tjp file that the phone app does not use, kept so that saving
     * writes it back unchanged: the desktop reference picture and any animation frames after the first.
     */
    private byte[] pictureData;
    private List<List<Strip>> extraFrames = new ArrayList<>();

    public byte[] getPictureData() {
        return pictureData;
    }

    public void setPictureData(byte[] data) {
        pictureData = data;
    }

    public List<List<Strip>> getExtraFrames() {
        return extraFrames;
    }

    public Fence(int x, int y) {
        width = x;
        height = y;
        edges = new Edge[width][height];
        strips = new LinkedList<>();
        createEdges();
    }

    public abstract String getType();

    protected abstract void createEdges();

    /** A new empty fence of the same type and size. */
    protected abstract Fence createEmpty();

    public Edge getEdgeAt(Coord c) {
        return edges[c.getX()][c.getY()];
    }

    public Edge getEdgeAt(int x, int y) {
        return edges[x][y];
    }

    /** Like getEdgeAt, but returns null instead of throwing for cells outside the grid. */
    public Edge edgeAtOrNull(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return null;
        return edges[x][y];
    }

    public LinkedList<Strip> getStrips() {
        return strips;
    }

    public void setStrips(LinkedList<Strip> strips) {
        this.strips = strips;
    }

    public void addStrip(Strip s) {
        strips.add(s);
    }

    public void removeLastStrip() {
        strips.removeLast();
    }

    /** Splits the strips into stacking layers, bottom layer first. */
    public LinkedList<Fence> getLayers() {
        LinkedList<Fence> fences = new LinkedList<>();

        Layer top = new Layer();
        for (Strip s : strips) {
            top.addStrip(s);
        }

        for (Strip[] ss : top.getLayers()) {
            Fence f = createEmpty();
            for (Strip s : ss) {
                f.addStrip(s);
            }
            fences.addFirst(f);
        }
        return fences;
    }
}
