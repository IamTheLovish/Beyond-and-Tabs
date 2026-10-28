package com.solegendary.reignofnether.maps;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Arrays;

/**
 * The lobby's picture of a pre-made map (design/maps.md): its terrain shaded by height and surface, the start slots
 * in team colours, metal patches (gold = rich) and capture sites, sampled from the same MapLayout the server will
 * stamp. Recomputed only when the map or the line-up changes.
 */
public final class MapPreview {
    private MapPreview() { }

    static final int CELL = 2;
    private static MapPreset cachedPreset = null;
    private static int[] cachedTeams = null;
    private static int cachedW, cachedH;
    private static long cachedSeed = 0;
    private static int cachedMetalCode = 1;
    private static int[] colours = new int[0];
    private static MapLayout layout = null;

    public static void draw(GuiGraphics gg, Font font, MapPreset preset, int[] teams, int x, int y, int w, int h) {
        draw(gg, font, preset, teams, x, y, w, h, 0L);
    }

    /** As below with the normal metal setting. */
    public static void draw(GuiGraphics gg, Font font, MapPreset preset, int[] teams, int x, int y, int w, int h, long seed) {
        draw(gg, font, preset, teams, x, y, w, h, seed, 1);
    }

    /**
     * The map with this seed and the lobby's metal setting (0 lean, 1 normal, 2 rich, 3 random), which reaches
     * pre-made maps since maps spec M1: MapLayout.build(preset, teams, seed, SkirmishServerboundPacket.premadeMetal).
     */
    public static void draw(GuiGraphics gg, Font font, MapPreset preset, int[] teams, int x, int y, int w, int h, long seed,
                            int metalCode) {
        if (teams.length < 2)
            teams = new int[] { 0, 1 };
        int gw = w / CELL, gh = h / CELL;
        if (preset != cachedPreset || !Arrays.equals(teams, cachedTeams) || gw != cachedW || gh != cachedH || seed != cachedSeed
                || metalCode != cachedMetalCode)
            rebuild(preset, teams, gw, gh, seed, metalCode);
        if (layout == null) {
            // a generated map is still being drawn on its worker (MapGenerator, about a second for a 4v4)
            gg.drawString(font, failed ? "Generation failed (see log)" : "Generating...", x + 4, y + h / 2 - 4, 0xFFC8D0C8, false);
            return;
        }
        final int[] cols = colours;
        final int fgw = gw, fgh = gh;
        final MapLayout l = layout;
        gg.drawManaged(() -> {
            for (int i = 0; i < fgw; i++)
                for (int k = 0; k < fgh; k++) {
                    int c = cols[i * fgh + k];
                    if (c != 0)
                        gg.fill(x + i * CELL, y + k * CELL, x + i * CELL + CELL, y + k * CELL + CELL, c);
                }
            double scale = (Math.min(fgw, fgh) * CELL / 2.0 - 2) / (l.radius + 4);
            int ox = x + fgw * CELL / 2, oy = y + fgh * CELL / 2;
            if (l.gen != null)   // the reserved wild ground (the future hero jungle): a faint violet speckle
                for (com.solegendary.reignofnether.maps.gen.GenMap.Jungle j : l.gen.jungle)
                    for (int[] d : j.discs()) {
                        int r = Math.max(1, (int) Math.round(d[2] * scale));
                        int px = ox + (int) Math.round(d[0] * scale), py = oy + (int) Math.round(d[1] * scale);
                        for (int a = -r; a <= r; a += 2)
                            for (int b = -r; b <= r; b += 2)
                                if (a * a + b * b <= r * r)
                                    gg.fill(px + a, py + b, px + a + 1, py + b + 1, 0x90B060E0);
                    }
            for (MapLayout.Mex m : l.mexes) {
                int px = ox + (int) Math.round(m.dx() * scale), py = oy + (int) Math.round(m.dz() * scale);
                int c = m.value() >= 1.75f ? 0xFFFFD21E : m.value() >= 1.25f ? 0xFFE0A040 : 0xFFE6EEF2;
                gg.fill(px, py, px + 1, py + 1, c);
            }
            for (MapLayout.Site s : l.sites) {
                int px = ox + (int) Math.round(s.dx() * scale), py = oy + (int) Math.round(s.dz() * scale);
                gg.fill(px - 1, py - 1, px + 2, py + 2, 0xFF000000);
                gg.fill(px, py, px + 1, py + 1, 0xFFFF66FF);
            }
            for (MapLayout.Slot s : l.slots) {
                int px = ox + (int) Math.round(s.dx() * scale), py = oy + (int) Math.round(s.dz() * scale);
                gg.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
                gg.fill(px - 1, py - 1, px + 2, py + 2, 0xFF000000 | MapMatch.teamColour(s.team()));
            }
        });
    }

    /** The newest generated-map request (older workers' results are dropped), and whether it failed. */
    private static long pendingKey = 0;
    private static boolean failed = false;

    static void rebuild(MapPreset preset, int[] teams, int gw, int gh, long seed, int metalCode) {
        cachedPreset = preset;
        cachedMetalCode = metalCode;
        cachedSeed = seed;
        cachedTeams = teams.clone();
        cachedW = gw;
        cachedH = gh;
        final int[] t = teams.clone();
        long key = ++pendingKey;   // whatever a worker still draws for an older request is dropped when it lands
        failed = false;
        if (!preset.generated()) {
            Object[] done = compute(preset, t, gw, gh, seed, metalCode);
            layout = (MapLayout) done[0];
            colours = (int[]) done[1];
            return;
        }
        // a generated map takes up to a second or so (the balancer builds several): draw it on a worker so a re-roll
        // never stalls the lobby
        layout = null;
        java.util.concurrent.CompletableFuture.supplyAsync(() -> compute(preset, t, gw, gh, seed, metalCode))
            .whenComplete((done, err) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (err != null) {
                    com.solegendary.reignofnether.ReignOfNether.LOGGER.error("[MapPreview] generating the map failed", err);
                    if (key == pendingKey)
                        failed = true;
                }
                if (key == pendingKey && done != null) {
                    layout = (MapLayout) done[0];
                    colours = (int[]) done[1];
                }
            }));
    }

    /** The layout and its picture: pure maths, safe on any thread. */
    private static Object[] compute(MapPreset preset, int[] teams, int gw, int gh, long seed, int metalCode) {
        MapLayout l = MapLayout.build(preset, teams, seed,
            com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket.premadeMetal(metalCode));
        int[] cols = new int[gw * gh];
        MapLayout.Sample s = new MapLayout.Sample();
        double scale = (l.radius + 4) / (Math.min(gw, gh) / 2.0 - 1);
        for (int i = 0; i < gw; i++)
            for (int k = 0; k < gh; k++) {
                double dx = (i - gw / 2.0 + 0.5) * scale, dz = (k - gh / 2.0 + 0.5) * scale;
                if (!l.insideArena(dx, dz)) {
                    cols[i * gh + k] = 0;
                    continue;
                }
                l.sample(dx, dz, s);
                cols[i * gh + k] = 0xFF000000 | colourOf(l, s);
            }
        return new Object[] { l, cols };
    }

    /**
     * The generated map's modifiers for the lobby line under the preview ("Modifiers: Rich vein, Ley line"), "Surprise
     * modifiers" when they are rolled but kept hidden, null when off, not a generated map or still being drawn.
     */
    public static String modifiersLine() {
        MapLayout l = layout;
        if (l == null || l.gen == null)
            return null;
        if (com.solegendary.reignofnether.maps.gen.GenSeed.modifiersIndex(l.seed) == 2)
            return "Surprise modifiers";
        if (l.gen.modifiers.isEmpty())
            return null;
        StringBuilder sb = new StringBuilder();
        for (com.solegendary.reignofnether.maps.gen.Modifier m : l.gen.modifiers)
            sb.append(sb.length() == 0 ? "" : ", ").append(m.displayName);
        return sb.toString();
    }

    /** The map's name in the player's language (lang key), the English name when the key is missing. */
    public static String nameOf(MapPreset preset) {
        String key = preset.langKey();
        return net.minecraft.client.resources.language.I18n.exists(key)
            ? net.minecraft.client.resources.language.I18n.get(key) : preset.displayName;
    }

    /** The map's one-line description in the player's language, the English blurb when the key is missing. */
    public static String blurbOf(MapPreset preset) {
        String key = preset.langKey() + ".blurb";
        return net.minecraft.client.resources.language.I18n.exists(key)
            ? net.minecraft.client.resources.language.I18n.get(key) : preset.blurb;
    }

    static int colourOf(MapLayout l, MapLayout.Sample s) {
        MapPreset preset = l.preset;
        boolean deltas = preset == MapPreset.SOULFIRE_DELTAS;
        int c = switch (s.surface) {
            case WATER -> s.height >= -1 ? 0x5FA8D8 : 0x2A5AA0;
            case LAVA -> 0xE05A10;
            case VOID -> 0x0A0510;
            case BRIDGE -> preset.theme == MapPreset.Theme.END ? 0xA87FA8 : deltas ? 0x2E2A33 : 0x5A5A60;
            case CLIFF -> 0x7A7A7A;
            case MESA -> deltas ? 0x2B6E6A : preset.generated() ? 0x7D6B52 : 0xB8653A;   // warped nylium; coarse dirt
            case SAND -> deltas ? 0x51402F : 0xDCCB8E;   // soul sand on the deltas
            case GRASS -> preset.theme == MapPreset.Theme.NETHER ? 0x8E1F22 : 0x4A8A3A;
            default -> switch (preset.theme) {
                case NETHER -> 0x7A2A26;
                case END -> 0xD8D8A0;
                default -> preset == MapPreset.DESERT_MESA && s.surface == MapLayout.Surface.LAND ? 0xD9C27E : 0x4A8A3A;
            };
        };
        if (s.forest)   // groves: crimson / warped fungi on the deltas, chorus on the isles, green trees elsewhere
            c = deltas ? (l.groveWarped(s.u, s.v) ? 0x14575C : 0x5A1018)
                : preset.theme == MapPreset.Theme.END ? 0x6A4A7C : 0x1F5A1F;
        if (s.surface != MapLayout.Surface.WATER && s.surface != MapLayout.Surface.VOID && s.surface != MapLayout.Surface.LAVA) {
            int add = Math.max(-6, Math.min(16, s.height)) * 5;
            int r = clamp(((c >> 16) & 255) + add), g = clamp(((c >> 8) & 255) + add), b = clamp((c & 255) + add);
            c = (r << 16) | (g << 8) | b;
        }
        return c;
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
