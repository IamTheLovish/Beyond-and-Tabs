package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;
import com.solegendary.reignofnether.maps.MetalOptions;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Headless top-down render of generated maps (plain java.awt, no Minecraft): contact sheets of seeds x contrasts.
 * Usage: MapSheet outDir n cols seedsPerContrast contrast...   (one PNG per contrast, plus all-in-one for MIRROR control)
 */
public final class MapSheet {
    static final int TILE = 1; // px per block

    public static void main(String[] a) throws Exception {
        File out = new File(a[0]);
        out.mkdirs();
        int n = Integer.parseInt(a[1]), per = Integer.parseInt(a[2]);
        for (int k = 3; k < a.length; k++) {
            GenOptions.Contrast c = GenOptions.Contrast.valueOf(a[k]);
            List<GenMap> maps = new ArrayList<>();
            for (int i = 0; maps.size() < per; i++) {
                GenOptions o = GenOptions.DEFAULT.withContrast(c).withShape(maps.size() % 2 == 0 ? GenOptions.Shape.ROUND : GenOptions.Shape.SQUARE);
                maps.add(MapGenerator.generate(MapGenSim.teams(n), 0x5EED100L + 131L * i + c.ordinal() * 17L, o, MetalOptions.DEFAULT));
            }
            BufferedImage img = sheet(maps, 3);
            File f = new File(out, "sheet-" + n + "p-" + c.name().toLowerCase().replace('_', '-') + ".png");
            ImageIO.write(img, "png", f);
            System.out.println(f + " " + img.getWidth() + "x" + img.getHeight());
        }
    }

    static BufferedImage sheet(List<GenMap> maps, int cols) {
        int rows = (maps.size() + cols - 1) / cols;
        int cell = 2 * maps.stream().mapToInt(m -> m.half).max().getAsInt() + 1;
        int head = 34, pad = 8;
        BufferedImage img = new BufferedImage(cols * (cell + pad) + pad, rows * (cell + head + pad) + pad, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(24, 26, 30));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < maps.size(); i++) {
            int x0 = pad + (i % cols) * (cell + pad), y0 = pad + (i / cols) * (cell + head + pad);
            draw(g, maps.get(i), x0, y0 + head, cell);
        }
        g.dispose();
        return img;
    }

    static void draw(Graphics2D g, GenMap m, int x0, int y0, int cell) {
        int c = cell / 2;
        int e = m.half;
        MapLayout.Sample s = new MapLayout.Sample();
        BufferedImage t = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_ARGB);
        for (int dx = -e; dx <= e; dx++)
            for (int dz = -e; dz <= e; dz++) {
                if (!m.inside(dx, dz)) continue;
                m.sample(dx, dz, s);
                int h = s.height;
                Color col = switch (s.surface) {
                    case WATER -> h < -1 ? new Color(30, 80, 150) : new Color(70, 140, 190);
                    case LAVA -> new Color(220, 90, 20);
                    case VOID -> new Color(0, 0, 0);
                    case SAND -> new Color(214, 196, 130);
                    case CLIFF -> new Color(88, 84, 80);
                    case MESA -> new Color(170, 110, 70);
                    case BRIDGE -> new Color(130, 100, 60);
                    default -> s.forest ? new Color(40, 100, 50) : new Color(96, 150, 80);
                };
                // height tint + simple hillshade (light from north-west)
                int hl = m.heightAt(dx - 1, dz - 1), hr = m.heightAt(dx + 1, dz + 1);
                double shade = 1.0 + 0.10 * (hl - hr) + 0.035 * h;
                shade = Math.max(0.55, Math.min(1.6, shade));
                col = new Color(cl(col.getRed() * shade), cl(col.getGreen() * shade), cl(col.getBlue() * shade));
                if (m.treeAt(dx, dz)) col = new Color(24, 70, 34);
                t.setRGB(dx + e + (c - e), dz + e + (c - e), col.getRGB());
            }
        g.drawImage(t, x0, y0, null);
        // roads
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(200, 180, 130, 150));
        for (GenMap.Lane l : m.lanes)
            for (int k = 1; k < l.xs().length; k += 1)
                g.drawLine(x0 + c + l.xs()[k - 1], y0 + c + l.zs()[k - 1], x0 + c + l.xs()[k], y0 + c + l.zs()[k]);
        // jungle
        g.setColor(new Color(255, 255, 255, 140));
        for (GenMap.Jungle j : m.jungle)
            for (int[] d : j.discs())
                g.drawOval(x0 + c + d[0] - d[2], y0 + c + d[1] - d[2], 2 * d[2], 2 * d[2]);
        // arena outline
        g.setColor(new Color(240, 240, 240, 200));
        g.setStroke(new BasicStroke(2f));
        if (m.shape == GenMap.Shape.SQUARE) g.drawRect(x0 + c - e, y0 + c - e, 2 * e, 2 * e);
        else g.drawOval(x0 + c - e, y0 + c - e, 2 * e, 2 * e);
        // patches: size by value, colour by owner team
        Color[] team = { new Color(255, 215, 0), new Color(0, 220, 255) };
        for (MapLayout.Mex x : m.mexes) {
            int r = x.value() >= 2f ? 6 : x.value() > 1.2f ? 5 : x.value() < 0.9f ? 3 : 4;
            g.setColor(Color.BLACK);
            g.fillOval(x0 + c + x.dx() - r - 1, y0 + c + x.dz() - r - 1, 2 * r + 2, 2 * r + 2);
            g.setColor(team[Math.floorMod(m.teams[Math.max(0, x.slot())] , 2)]);
            g.fillOval(x0 + c + x.dx() - r, y0 + c + x.dz() - r, 2 * r, 2 * r);
        }
        // sites (diamonds, white), vents (orange squares), camps (magenta triangles)
        for (MapLayout.Site st : m.sites) {
            Polygon p = new Polygon(new int[] { 0, 8, 0, -8 }, new int[] { -8, 0, 8, 0 }, 4);
            p.translate(x0 + c + st.dx(), y0 + c + st.dz());
            g.setColor(Color.WHITE); g.fillPolygon(p); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.drawPolygon(p);
        }
        for (MapLayout.Spot v : m.vents) { g.setColor(new Color(255, 120, 0)); g.fillRect(x0 + c + v.dx() - 5, y0 + c + v.dz() - 5, 10, 10); }
        for (GenMap.Camp k : m.camps) {
            g.setColor(new Color(230, 60, 200));
            g.fillPolygon(new int[] { x0 + c + k.dx() - 6, x0 + c + k.dx() + 6, x0 + c + k.dx() }, new int[] { y0 + c + k.dz() + 5, y0 + c + k.dz() + 5, y0 + c + k.dz() - 7 }, 3);
        }
        // seats
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        for (MapLayout.Slot sl : m.slots) {
            int cx = x0 + c + sl.dx(), cy = y0 + c + sl.dz();
            g.setColor(Color.BLACK); g.fillOval(cx - 13, cy - 13, 26, 26);
            g.setColor(team[Math.floorMod(sl.team(), 2)]); g.drawOval(cx - 13, cy - 13, 26, 26);
            g.drawString(String.valueOf(m.seats[sl.index()].letter), cx - 5, cy + 5);
        }
        // caption
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        StringBuilder mods = new StringBuilder();
        for (Modifier mo : m.modifiers) mods.append(mo.displayName).append("; ");
        g.drawString(String.format("%x %s %s  spread %.1f%%  %d patches %d sites", m.seed & 0xFFFFFF, m.shape.name().substring(0, 2), m.contrast.name().replace("PROSPECTOR_WARDEN", "PROS/WARD").replace("OPEN_BASTION", "OPEN/BAST"), 100 * m.report.spread(), m.mexes.size(), m.sites.size()), x0, y0 - 18);
        g.drawString("mods: " + (mods.length() == 0 ? "none" : mods) , x0, y0 - 4);
    }

    static int cl(double v) { return (int) Math.max(0, Math.min(255, v)); }
}
