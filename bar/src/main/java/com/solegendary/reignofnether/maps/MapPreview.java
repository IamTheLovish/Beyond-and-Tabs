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
    private static int[] colours = new int[0];
    private static MapLayout layout = null;

    public static void draw(GuiGraphics gg, Font font, MapPreset preset, int[] teams, int x, int y, int w, int h) {
        if (teams.length < 2)
            teams = new int[] { 0, 1 };
        int gw = w / CELL, gh = h / CELL;
        if (preset != cachedPreset || !Arrays.equals(teams, cachedTeams) || gw != cachedW || gh != cachedH)
            rebuild(preset, teams, gw, gh);
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

    static void rebuild(MapPreset preset, int[] teams, int gw, int gh) {
        cachedPreset = preset;
        cachedTeams = teams.clone();
        cachedW = gw;
        cachedH = gh;
        layout = MapLayout.build(preset, teams);
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
                colours[i * gh + k] = 0xFF000000 | colourOf(preset, s);
            }
    }

    static int colourOf(MapPreset preset, MapLayout.Sample s) {
        int c = switch (s.surface) {
            case WATER -> s.height >= -1 ? 0x5FA8D8 : 0x2A5AA0;
            case LAVA -> 0xE05A10;
            case VOID -> 0x0A0510;
            case BRIDGE -> preset == MapPreset.END_SPIRES ? 0xA87FA8 : 0x5A5A60;
            case CLIFF -> 0x7A7A7A;
            case MESA -> 0xB8653A;
            case SAND -> 0xDCCB8E;
            default -> switch (preset.theme) {
                case NETHER -> 0x7A2A26;
                case END -> 0xD8D8A0;
                default -> preset == MapPreset.DESERT_MESA && s.surface == MapLayout.Surface.LAND ? 0xD9C27E : 0x4A8A3A;
            };
        };
        if (s.forest)
            c = 0x1F5A1F;
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
