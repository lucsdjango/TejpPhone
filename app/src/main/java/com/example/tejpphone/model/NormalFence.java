package com.example.tejpphone.model;

/** The diagonal grid: alternating / and \ edges. */
public class NormalFence extends Fence {

    public static final String TYPE = "NORMAL";

    public NormalFence(int x, int y) {
        super(x, y);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected Fence createEmpty() {
        return new NormalFence(width, height);
    }

    @Override
    protected void createEdges() {
        int slope = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                Coord c0 = null, c1 = null;
                if (slope == 0) {
                    c0 = new Coord(j, i + 1);
                    c1 = new Coord(j + 1, i);
                } else if (slope == 1) {
                    c0 = new Coord(j, i);
                    c1 = new Coord(j + 1, i + 1);
                }
                edges[j][i] = new Edge(slope, c0, c1);
                slope = (slope + 1) % 2;
            }
            slope = (slope + 1) % 2;
        }
    }
}
