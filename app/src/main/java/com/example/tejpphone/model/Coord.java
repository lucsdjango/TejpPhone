package com.example.tejpphone.model;

/** A grid point. Ported unchanged from the desktop app. */
public class Coord {

    private final int x, y;

    public Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Coord)) return false;
        Coord c = (Coord) o;
        return c.x == x && c.y == y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }

    @Override
    public String toString() {
        return x + " " + y + " ";
    }
}
