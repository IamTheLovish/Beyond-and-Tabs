package com.solegendary.reignofnether.ambient.mist;

/**
 * Where the valley mist lies, column by column round the camera (MistPass hands it to bat_mist.fsh in the fog
 * texture's free channels). lovish approved the look on Oct 29 18:05 ET from a paintover of the Verdant Valley RTS still:
 * the mist gathers on the river and the low banks beside it and in hollows, lit on top, streaming along the flow, and
 * the rest of the lawn stays clear - r6-r10b laid one height layer over the whole map, which read as a film.
 * <p>
 * Pure (no world): the caller samples a grid of columns ({@code step} blocks apart) - the ground height without leaves
 * (the water's surface where there is water) and whether the column is water. Per column it returns
 * <ul>
 *   <li>{@code cover} 0..1: 1 on the water, fading over ~{@link #WATER_REACH} blocks of bank and with the bank's
 *       height above that water (mist is low: a ridge beside the river stays clear), or a hollow's depth below its
 *       surroundings past a one-block dead zone (a dip of a block is not a valley);</li>
 *   <li>{@code smooth}: the ground height averaged over ~10 blocks, so the shader measures the mist from a ground
 *       without one-block terrace steps (r10b's edges drew every step).</li>
 * </ul>
 */
public final class ValleyField {

    /** Blocks over which the mist thins away from the water (cover = exp(-d / WATER_REACH)). */
    static final double WATER_REACH = 7.5;
    /** Blocks above the water's surface a bank may rise before the mist on it thins, and the e-fold after that. */
    static final double BANK_GRACE = 1.5, BANK_FOLD = 3.0;
    /** A hollow: blocks below its surroundings that count for nothing, then blocks to full cover. */
    static final double HOLLOW_DEAD = 1.0, HOLLOW_FULL = 1.8;
    /** Box radii in cells: the smoothed ground, and the wide surroundings a hollow is measured against. */
    static final int SMOOTH_R = 2, WIDE_R = 6;
    /** The height channel: blocks below the base it starts at, and the blocks its 256 levels span. */
    static final int HEIGHT_BELOW = 8, HEIGHT_SPAN = 32;

    public static final class Result {
        public final double[][] cover, smooth;

        Result(double[][] cover, double[][] smooth) {
            this.cover = cover;
            this.smooth = smooth;
        }
    }

    private ValleyField() {
    }

    public static Result compute(int[][] h, boolean[][] water, int step) {
        int n = h.length, m = h[0].length;
        double[][] hd = new double[n][m];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++)
                hd[i][j] = h[i][j];
        double[][] smooth = box(box(hd, SMOOTH_R / 2 + 1), SMOOTH_R / 2 + 1);   // two boxes: no corners, ~SMOOTH_R
        double[][] wide = box(hd, WIDE_R);

        // distance to the nearest water (chamfer 3-4, in cells x 3) carrying that water's surface height
        final int INF = Integer.MAX_VALUE / 4;
        int[][] dist = new int[n][m];
        double[][] level = new double[n][m];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++) {
                dist[i][j] = water[i][j] ? 0 : INF;
                level[i][j] = h[i][j];
            }
        int[][] fwd = { { -1, -1, 4 }, { -1, 0, 3 }, { -1, 1, 4 }, { 0, -1, 3 } };
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++)
                relax(dist, level, i, j, fwd, n, m);
        int[][] back = { { 1, 1, 4 }, { 1, 0, 3 }, { 1, -1, 4 }, { 0, 1, 3 } };
        for (int i = n - 1; i >= 0; i--)
            for (int j = m - 1; j >= 0; j--)
                relax(dist, level, i, j, back, n, m);

        double[][] cover = new double[n][m];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++) {
                double wet = 0;
                if (dist[i][j] < INF) {
                    double d = dist[i][j] / 3.0 * step;
                    double up = Math.max(0, h[i][j] - level[i][j] - BANK_GRACE);
                    wet = Math.exp(-d / WATER_REACH) * Math.exp(-up / BANK_FOLD);
                }
                double low = wide[i][j] - smooth[i][j];
                double hollow = clamp01((low - HOLLOW_DEAD) / HOLLOW_FULL);
                cover[i][j] = water[i][j] ? 1.0 : clamp01(Math.max(wet, hollow));
            }
        return new Result(cover, smooth);
    }

    private static void relax(int[][] dist, double[][] level, int i, int j, int[][] nb, int n, int m) {
        for (int[] o : nb) {
            int a = i + o[0], b = j + o[1];
            if (a < 0 || b < 0 || a >= n || b >= m || dist[a][b] >= Integer.MAX_VALUE / 4)
                continue;
            int d = dist[a][b] + o[2];
            if (d < dist[i][j]) {
                dist[i][j] = d;
                level[i][j] = level[a][b];
            }
        }
    }

    /** A box average of radius r (cells), the edge cells clamped. */
    static double[][] box(double[][] a, int r) {
        int n = a.length, m = a[0].length;
        double[][] t = new double[n][m], out = new double[n][m];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++) {
                double s = 0;
                for (int k = -r; k <= r; k++)
                    s += a[Math.max(0, Math.min(n - 1, i + k))][j];
                t[i][j] = s / (2 * r + 1);
            }
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++) {
                double s = 0;
                for (int k = -r; k <= r; k++)
                    s += t[i][Math.max(0, Math.min(m - 1, j + k))];
                out[i][j] = s / (2 * r + 1);
            }
        return out;
    }

    /** A height as the texture's byte: base - HEIGHT_BELOW .. + HEIGHT_SPAN over 0..255, clamped. */
    public static int encodeHeight(double y, int base) {
        double t = (y - (base - HEIGHT_BELOW)) / HEIGHT_SPAN;
        return (int) Math.max(0, Math.min(255, Math.round(t * 255)));
    }

    public static double decodeHeight(int b, int base) {
        return base - HEIGHT_BELOW + b / 255.0 * HEIGHT_SPAN;
    }

    private static double clamp01(double t) {
        return Math.max(0, Math.min(1, t));
    }
}
