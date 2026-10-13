package com.example.tejpphone.function;

import com.example.tejpphone.model.Fence;
import com.example.tejpphone.model.Strip;

import java.util.HashSet;

public abstract class TransformStripsModification extends Modification {

    protected Fence fence;
    protected HashSet<Strip> oldStrips, newStrips;

    protected int calcMiddle() {
        int maxX = 0;
        int minX = fence.width - 1;
        int midX = 0;
        for (Strip s : oldStrips) {
            minX = Math.min(minX, s.getStart().getLeft().getX());
            minX = Math.min(minX, s.getEnd().getLeft().getX());
            maxX = Math.max(maxX, s.getStart().getRight().getX());
            maxX = Math.max(maxX, s.getEnd().getRight().getX());
            midX = maxX - (maxX - minX) / 2;
        }
        return midX;
    }

    protected int calcRight() {
        int maxX = 0;
        for (Strip s : oldStrips) {
            maxX = Math.max(maxX, s.getStart().getRight().getX());
            maxX = Math.max(maxX, s.getEnd().getRight().getX());
        }
        return maxX;
    }

    protected int calcLeft() {
        int minX = fence.width - 1;
        for (Strip s : oldStrips) {
            minX = Math.min(minX, s.getStart().getLeft().getX());
            minX = Math.min(minX, s.getEnd().getLeft().getX());
        }
        return minX;
    }
}
