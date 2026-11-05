package com.solegendary.reignofnether.barfx;

/** The client's rune circles (signature stub: the tests first; the pool lands in the next commit). */
public final class RunePool {
    public static final int MAX = 48;
    public static final float END_FADE = 0.25f;

    public int start(int id, float x, float y, float z, float radius, byte style, boolean follow, float clock, int ticks) { return -1; }

    public void end(int id, float clock) { }

    public boolean alive(int id, float clock) { return false; }

    public int count() { return 0; }
}
