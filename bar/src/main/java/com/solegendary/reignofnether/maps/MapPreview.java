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
        final int[] cols = colours;
        final int fgw = gw, fgh = gh;
        gg.drawManaged(() -> {
            for (int i = 0; i < fgw; i++)
                for (int k = 0; k < fgh; k++) {
                    int c = cols[i * fgh + k];
                    if (c != 0)
                        gg.fill(x + i * CELL, y + k * CELL, x + i * CELL + CELL, y + k * CELL + CELL, c);
                }
            double scale = (Math.min(fgw, fgh) * CELL / 2.0 - 2) / (layout.radius + 4);
            int ox = x + fgw * CELL / 2, oy = y + fgh * CELL / 2;
            for (MapLayout.Mex m : layout.mexes) {
                int px = ox + (int) Math.round(m.dx() * scale), py = oy + (int) Math.round(m.dz() * scale);
                int c = m.value() >= 1.75f ? 0xFFFFD21E : m.value() >= 1.25f ? 0xFFE0A040 : 0xFFE6EEF2;
                gg.fill(px, py, px + 1, py + 1, c);
            }
            for (MapLayout.Site s : layout.sites) {
                int px = ox + (int) Math.round(s.dx() * scale), py = oy + (int) Math.round(s.dz() * scale);
                gg.fill(px - 1, py - 1, px + 2, py + 2, 0xFF000000);
                gg.fill(px, py, px + 1, py + 1, 0xFFFF66FF);
            }
            for (MapLayout.Slot s : layout.slots) {
                int px = ox + (int) Math.round(s.dx() * scale), py = oy + (int) Math.round(s.dz() * scale);
                gg.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
                gg.fill(px - 1, py - 1, px + 2, py + 2, 0xFF000000 | MapMatch.teamColour(s.team()));
            }
        });
    }

    static void rebuild(MapPreset preset, int[] teams, int gw, int gh, long seed, int metalCode) {
        cachedPreset = preset;
        cachedMetalCode = metalCode;
        cachedSeed = seed;
        cachedTeams = teams.clone();
        cachedW = gw;
        cachedH = gh;
        layout = MapLayout.build(preset, teams, seed,
            com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket.premadeMetal(metalCode));
        colours = new int[gw * gh];
        MapLayout.Sample s = new MapLayout.Sample();
        double scale = (layout.radius + 4) / (Math.min(gw, gh) / 2.0 - 1);
        for (int i = 0; i < gw; i++)
            for (int k = 0; k < gh; k++) {
                double dx = (i - gw / 2.0 + 0.5) * scale, dz = (k - gh / 2.0 + 0.5) * scale;
                if (dx * dx + dz * dz > (double) layout.radius * layout.radius) {
                    colours[i * gh + k] = 0;
                    continue;
                }
                layout.sample(dx, dz, s);
                colours[i * gh + k] = 0xFF000000 | colourOf(layout, s);
            }
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
            case MESA -> deltas ? 0x2B6E6A : 0xB8653A;   // warped nylium on the deltas
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
