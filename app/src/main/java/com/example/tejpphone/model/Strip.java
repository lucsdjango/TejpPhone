package com.example.tejpphone.model;

/**
 * A strip of tape running from one grid edge to another.
 * Geometry is ported from the desktop app. Drawing lives in view.StripRenderer.
 */
public class Strip {

    public static final int WHITE = 0xFFFFFFFF;

    private Edge start, end;
    private float width;
    private int color;
    private final String fenceType;

    /** Bumped whenever the shape changes, so the renderer knows to rebuild its cached path. */
    private int version = 0;

    public Strip(Edge start, Edge end, float w, String fenceType) {
        this(start, end, w, fenceType, WHITE);
    }

    public Strip(Edge start, Edge end, float w, String fenceType, int color) {
        this.start = start;
        this.end = end;
        this.width = w;
        this.fenceType = fenceType;
        this.color = color;
    }

    public Edge getStart() {
        return start;
    }

    public Edge getEnd() {
        return end;
    }

    public float getWidth() {
        return width;
    }

    public int getColor() {
        return color;
    }

    public String getFenceType() {
        return fenceType;
    }

    public int getVersion() {
        return version;
    }

    public void setEnd(Edge e) {
        end = e;
        version++;
    }

    public void setWidth(float f) {
        width = f;
        version++;
    }

    public void setColor(int col) {
        color = col;
    }

    public boolean isDiag() {
        int dX = getStart().getLeft().getX() - getEnd().getLeft().getX();
        int dY = getStart().getLeft().getY() - getEnd().getLeft().getY();
        return dY == dX;
    }

    /** Index of the cell an edge lands in after a shift. Same rule as the desktop Strip.shift. */
    private static int[] shiftedCell(Edge e, int x, int y) {
        return new int[]{e.cellX() + x, e.cellY() + y};
    }

    /**
     * True if both ends can shift by (x, y) and still land on a grid edge.
     * The desktop app skipped this check and threw ArrayIndexOutOfBoundsException.
     * Swing survived that, but it would crash an Android app.
     */
    public boolean canShift(int x, int y, Fence fence) {
        int[] s = shiftedCell(start, x, y);
        int[] e = shiftedCell(end, x, y);
        return fence.edgeAtOrNull(s[0], s[1]) != null && fence.edgeAtOrNull(e[0], e[1]) != null;
    }

    /** Moves the strip by (x, y) cells. Call canShift first. */
    public void shift(int x, int y, Fence fence) {
        int[] s = shiftedCell(start, x, y);
        int[] e = shiftedCell(end, x, y);
        start = fence.getEdgeAt(s[0], s[1]);
        end = fence.getEdgeAt(e[0], e[1]);
        version++;
    }

    /**
     * The two clip polygons from the desktop getPolygons, in content units.
     * Each is {x0, y0, x1, y1, x2, y2, x3, y3}.
     */
    public int[][] getPolygons() {
        final int S = Fence.CELL_SIZE;
        Coord e1l = start.getLeft();
        Coord e1r = start.getRight();
        Coord e2l = end.getLeft();
        Coord e2r = end.getRight();

        int[] p1 = null, p2 = null;

        if (fenceType.equals(NormalFence.TYPE)) {
            p1 = new int[]{
                    e1l.getX() * S, e1l.getY() * S,
                    e1r.getX() * S, e1r.getY() * S,
                    e2r.getX() * S, e2r.getY() * S,
                    e2l.getX() * S, e2l.getY() * S};
            p2 = new int[]{
                    e1l.getX() * S, e1l.getY() * S,
                    e1r.getX() * S, e1r.getY() * S,
                    e2l.getX() * S, e2l.getY() * S,
                    e2r.getX() * S, e2r.getY() * S};
        } else if (fenceType.equals(VerticalFence.TYPE)) {
            int h = S / 2;
            p1 = new int[]{
                    e1l.getX() * S + h, e1l.getY() * S,
                    e1r.getX() * S + h, e1r.getY() * S,
                    e2r.getX() * S + h, e2r.getY() * S,
                    e2l.getX() * S + h, e2l.getY() * S};
            p2 = new int[]{
                    e1l.getX() * S + h, e1l.getY() * S,
                    e1r.getX() * S + h, e1r.getY() * S,
                    e2l.getX() * S + h, e2l.getY() * S,
                    e2r.getX() * S + h, e2r.getY() * S};
        } else if (fenceType.equals(AbnormalFence.TYPE)) {
            int h = S / 2;
            int[] a, b, c, d;
            if (start.slope == 3) {
                a = new int[]{e1l.getX() * S + h, e1l.getY() * S - h};
                b = new int[]{e1r.getX() * S + h, e1r.getY() * S + h};
            } else {
                a = new int[]{e1l.getX() * S - h, e1l.getY() * S + h};
                b = new int[]{e1r.getX() * S + h, e1r.getY() * S + h};
            }
            if (end.slope == 3) {
                c = new int[]{e2l.getX() * S + h, e2l.getY() * S - h};
                d = new int[]{e2r.getX() * S + h, e2r.getY() * S + h};
            } else {
                c = new int[]{e2l.getX() * S - h, e2l.getY() * S + h};
                d = new int[]{e2r.getX() * S + h, e2r.getY() * S + h};
            }
            p1 = new int[]{a[0], a[1], b[0], b[1], c[0], c[1], d[0], d[1]};
            p2 = new int[]{a[0], a[1], b[0], b[1], d[0], d[1], c[0], c[1]};
        }
        return new int[][]{p1, p2};
    }

    /** Centre line of the strip as {x1, y1, x2, y2} in content units, as in the desktop paintStrip. */
    public float[] getCentreLine() {
        final int S = Fence.CELL_SIZE;
        Coord e1l = start.getLeft();
        Coord e1r = start.getRight();
        Coord e2l = end.getLeft();
        Coord e2r = end.getRight();
        int x1, y1, x2, y2;
        if (fenceType.equals(NormalFence.TYPE)) {
            x1 = e1l.getX() * S + S / 2;
            y1 = (e1l.getY() * S + e1r.getY() * S) / 2;
            x2 = e2l.getX() * S + S / 2;
            y2 = (e2l.getY() * S + e2r.getY() * S) / 2;
        } else {
            x1 = e1l.getX() * S + S / 2;
            y1 = e1l.getY() * S + S / 2;
            x2 = e2l.getX() * S + S / 2;
            y2 = e2l.getY() * S + S / 2;
        }
        return new float[]{x1, y1, x2, y2};
    }

    /** Stroke width in content units, as in the desktop paintStrip. */
    public float getStrokeWidth() {
        final int S = Fence.CELL_SIZE;
        float edgeLength = (float) Math.sqrt(2.0 * S * S);
        return edgeLength * (width / Edge.EDGE_LENGTH);
    }

    /** True if the point (content units) is inside the strip outline. Used for selecting. */
    public boolean contains(double px, double py) {
        int[][] pols = getPolygons();
        return Geometry.polygonContains(pols[0], px, py) || Geometry.polygonContains(pols[1], px, py);
    }

    /** If e is one of the strip's ends, make it the end and return true. */
    public boolean edgeSelected(Edge e) {
        if (e.equals(end)) {
            return true;
        } else if (e.equals(start)) {
            swapEdges();
            return true;
        } else {
            return false;
        }
    }

    public void swapEdges() {
        Edge temp = end;
        end = start;
        start = temp;
        version++;
    }

    /** True if this strip's centre line crosses one of the other strip's end edges. */
    public boolean intersects(Strip other) {
        Coord sl = getStart().getLeft();
        Coord sr = getStart().getRight();
        Coord el = getEnd().getLeft();
        Coord er = getEnd().getRight();
        double ax = (sl.getX() + sr.getX()) / 2.0, ay = (sl.getY() + sr.getY()) / 2.0;
        double bx = (el.getX() + er.getX()) / 2.0, by = (el.getY() + er.getY()) / 2.0;

        sl = other.getStart().getLeft();
        sr = other.getStart().getRight();
        el = other.getEnd().getLeft();
        er = other.getEnd().getRight();

        boolean collision = Geometry.linesIntersect(ax, ay, bx, by, sl.getX(), sl.getY(), sr.getX(), sr.getY())
                && !other.getStart().equals(getStart())
                && !other.getStart().equals(getEnd());
        if (!collision) {
            collision = Geometry.linesIntersect(ax, ay, bx, by, el.getX(), el.getY(), er.getX(), er.getY())
                    && !other.getEnd().equals(getStart())
                    && !other.getEnd().equals(getEnd());
        }
        return collision;
    }

    @Override
    public String toString() {
        return getStart().toString() + getEnd().toString() + width + " " + Integer.toHexString(color);
    }
}
