package com.corrodinggames.rts.gameFramework.pathfinding;

/** 实验台用最小 PathPoint：只保留航点的格子坐标。 */
public final class PathPoint {
    public short tileX;
    public short tileY;

    public PathPoint() {
    }

    public PathPoint(short s, short s2) {
        this.tileX = s;
        this.tileY = s2;
    }

    @Override
    public String toString() {
        return tileX + "," + tileY;
    }
}
