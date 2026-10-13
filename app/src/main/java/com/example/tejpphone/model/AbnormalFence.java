package com.example.tejpphone.model;

/**
 * A grid of alternating - and | edges with gaps.
 * Ported for completeness. Like the desktop app, the toolbar has no button for it.
 */
public class AbnormalFence extends Fence {

    public static final String TYPE = "ABNORMAL";

    public AbnormalFence(int x, int y) {
        super(x, y);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected Fence createEmpty() {
        return new AbnormalFence(width, height);
    }

    @Override
    protected void createEdges() {
        for (int i = 0; i < height; i++) {
            for (int j = i % 2; j < width - 1; j += 2) {
                Coord c0, c1;
                int slope;
                if (i % 2 == 0) {
                    c0 = new Coord(j, i);
                    c1 = new Coord(j, i + 1);
                    slope = 3;
                } else {
                    c0 = new Coord(j, i);
                    c1 = new Coord(j + 1, i);
                    slope = 2;
                }
                if (i % 4 != 3) {
                    edges[j][i] = new Edge(slope, c0, c1);
                }
            }
        }
    }
}
