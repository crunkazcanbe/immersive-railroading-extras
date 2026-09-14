package com.dogpound.railmap.graph;

/** Undirected connection between two {@link RailNode}s (their centre-lines meet end to end). */
public final class RailSegment {
    private final int a, b;

    public RailSegment(int a, int b) {
        this.a = a;
        this.b = b;
    }

    public int a() { return a; }
    public int b() { return b; }

    /** Order-independent key so A-B and B-A dedupe to one edge. */
    public long key() {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xffffffffL);
    }
}
