package com.example.tejpphone.model;

/** One edge of the fence grid. A strip runs from one edge to another. */
public class Edge {

    /** The desktop FencePanel set Edge.edgeLength to 6 before anything was drawn. */
    public static final float EDGE_LENGTH = 6f;

    private final Coord leftCoord;
    private final Coord rightCoord;

    /** 0 = /   1 = \   2 = -   3 = | */
    public final int slope;

    private boolean selected = false;

    public Edge(int slope, Coord l, Coord r) {
        this.slope = slope;
        leftCoord = l;
        rightCoord = r;
    }

    public Coord getLeft() {
        return leftCoord;
    }

    public Coord getRight() {
        return rightCoord;
    }

    public int getSlope() {
        return slope;
    }

    public void select() {
        selected = true;
    }

    public void unselect() {
        selected = false;
    }

    public boolean isSelected() {
        return selected;
    }

    /** Column of the grid cell that holds this edge. */
    public int cellX() {
        return leftCoord.getX();
    }

    /** Row of the grid cell that holds this edge. Same rule as Strip.shift in the desktop app. */
    public int cellY() {
        return slope > 0 ? leftCoord.getY() : rightCoord.getY();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Edge)) return false;
        Edge e = (Edge) o;
        return e.getLeft().equals(leftCoord) && e.getRight().equals(rightCoord);
    }

    @Override
    public int hashCode() {
        return 31 * leftCoord.hashCode() + rightCoord.hashCode();
    }

    @Override
    public String toString() {
        return leftCoord.toString() + slope + " ";
    }
}
