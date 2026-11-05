package com.solegendary.reignofnether.unit.modelling.animations;

import java.util.Arrays;
import java.util.function.Function;

/**
 * The Tithe Binder's rig: a port of tools/tithe_binder_anim.py (the clip driver, THE spec). RED stub: the API only;
 * step writes the rest pose and apply writes nothing.
 */
public final class TitheBinderRig {

    public enum Clip { IDLE, FIDGET, MOVE, VOLLEY, BIND, HIT, DEATH }

    /** The bones the driver moves (DSL names, the generated TitheBinderModel's). */
    public static final String[] BONES = {"hover", "body", "chest", "mantle", "head", "right_arm", "left_arm",
        "right_hand", "left_hand", "right_forearm", "left_forearm", "tatter_f", "tatter_b", "tatter_l", "tatter_r",
        "cape", "hem_chain_1", "hem_chain_1_b", "hem_chain_2", "hem_chain_2_b", "hem_chain_3", "hem_chain_3_b",
        "lantern_chain", "right_shackle_chain", "left_shackle_chain", "ring"};
    /** Weapon slots 1..MAX_WEAPONS (arrays are indexed by k, slot 0 unused). */
    public static final int MAX_WEAPONS = 12;
    public static final int FLY_NONE = 0;
    public static final String[] FLY_STATES = {"", "SLUNG", "FLYING", "EMBED", "REEL", "FALLING", "SHATTER"};

    private TitheBinderRig() {}

    public static int bone(String name) {
        for (int i = 0; i < BONES.length; i++)
            if (BONES[i].equals(name))
                return i;
        return -1;
    }

    public static double smooth(double x) {
        x = Math.max(0.0, Math.min(1.0, x));
        return x * x * (3.0 - 2.0 * x);
    }

    public static final class Inputs {
        public Clip clip = Clip.IDLE;
        public int t;
        public double speed;
        public final double[] target = {0.0, 20.0, -80.0};
        public final double[] hitDir = {0.0, -1.0};
    }

    public static final class State {
        private int n;

        public State(int n, long seed) {
            this.n = n;
        }

        public int n() {
            return n;
        }
    }

    public static final class Pose {
        public final double[][] rot = new double[BONES.length][3], off = new double[BONES.length][3];
        public final boolean[] slotOn = new boolean[MAX_WEAPONS + 1];
        public final double[] slotAngle = new double[MAX_WEAPONS + 1], slotRadial = new double[MAX_WEAPONS + 1],
            slotY = new double[MAX_WEAPONS + 1], slotPitch = new double[MAX_WEAPONS + 1],
            slotRoll = new double[MAX_WEAPONS + 1], slotScale = new double[MAX_WEAPONS + 1];
        public final int[] flyState = new int[MAX_WEAPONS + 1];
        public final double[][] flyPos = new double[MAX_WEAPONS + 1][3], flyRot = new double[MAX_WEAPONS + 1][3];
        public final double[] flyScale = new double[MAX_WEAPONS + 1];
        public final boolean[] weaponHidden = new boolean[MAX_WEAPONS + 1];
        public boolean lanternCoreHidden;
        public double alpha = 1.0, lantern = 1.0;
        public int ribbons;
        public final double[][] ribbonHub = new double[MAX_WEAPONS][3], ribbonEnd = new double[MAX_WEAPONS][3];
        public final double[] ribbonSag = new double[MAX_WEAPONS], ribbonWidth = new double[MAX_WEAPONS];
    }

    /** RED stub: the rest pose. */
    public static void step(State st, Inputs in, Pose out) {
        for (double[] r : out.rot)
            Arrays.fill(r, 0.0);
        for (double[] o : out.off)
            Arrays.fill(o, 0.0);
        Arrays.fill(out.slotOn, false);
        Arrays.fill(out.flyState, FLY_NONE);
        Arrays.fill(out.weaponHidden, false);
        out.lanternCoreHidden = false;
        out.alpha = 1.0;
        out.lantern = 1.0;
        out.ribbons = 0;
    }

    public interface Part {
        void addRotation(float xRad, float yRad, float zRad);

        void addOffset(float x, float y, float z);

        void setOffset(float x, float y, float z);

        void setRotation(float xRad, float yRad, float zRad);

        void setScale(float s);

        void setVisible(boolean visible);
    }

    public static final class Bones {
        public Bones(Function<String, ? extends Part> lookup) {
        }
    }

    /** RED stub: writes nothing. */
    public static void apply(Pose pose, Bones bones) {
    }
}
