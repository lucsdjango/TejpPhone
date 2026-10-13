package com.example.tejpphone.model;

/** A grid of vertical | edges. */
public class VerticalFence extends Fence {

    public static final String TYPE = "VERTICAL";

    public VerticalFence(int x, int y) {
        super(x, y);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected Fence createEmpty() {
        return new VerticalFence(width, height);
    }

    @Override
    protected void createEdges() {
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                edges[j][i] = new Edge(3, new Coord(j, i), new Coord(j, i + 1));
            }
        }
    }
}
