package com.solegendary.reignofnether.maps.gen;

import com.solegendary.reignofnether.maps.MapLayout;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/** Offline renders of generated maps (lab only, not shipped): hill-shaded tiers, water, forest, roads, economy. */
public final class GenRender {
    static final Color[] TEAM = { new Color(60, 220, 60), new Color(230, 60, 60), new Color(60, 120, 255),
        new Color(240, 210, 60) };

    public static BufferedImage render(GenMap m, int scale) {
        int W = m.width;
        BufferedImage img = new BufferedImage(W * scale, W * scale, BufferedImage.TYPE_INT_RGB);
        int[] px = new int[W * W];
        for (int x = -m.half; x <= m.half; x++)
            for (int z = -m.half; z <= m.half; z++) {
                int i = m.idx(x, z);
                int c;
                if (!m.inside(x, z)) {
                    c = 0x161616;
                } else {
                    int h = m.height[i];
                    MapLayout.Surface s = GenMap.SURFACES[m.surface[i]];
                    c = switch (s) {
                        case WATER -> h <= -3 ? 0x24508C : 0x4C8CC4;
                        case SAND -> 0xD6C896;
                        case CLIFF -> 0x7C766E;
                        case MESA -> 0x9C8C6C;
                        default -> h <= 2 ? 0x568648 : h <= 5 ? 0x68964E : h <= 9 ? 0x88A058 : h <= 13 ? 0xA09A64 : 0xB4A48A;
                    };
                    if (s != MapLayout.Surface.WATER) {
                        int hx = m.heightAt(Math.max(-m.half, x - 1), z), hz = m.heightAt(x, Math.max(-m.half, z - 1));
                        double shade = 1 + 0.07 * ((h - hx) + (h - hz));
                        shade = Math.max(0.55, Math.min(1.35, shade));
                        c = mul(c, shade);
                        int hx2 = m.heightAt(Math.min(m.half, x + 1), z), hz2 = m.heightAt(x, Math.min(m.half, z + 1));
                        if (Math.abs(h - hx) >= 2 || Math.abs(h - hz) >= 2 || Math.abs(h - hx2) >= 2 || Math.abs(h - hz2) >= 2)
                            c = mul(c, 0.62);   // cliff edges drawn dark, so the tiers read
                        if (m.forest.get(i))
                            c = mul(mix(c, 0x2E5A26, 0.55), 1.0);
                        if (m.tree.get(i))
                            c = 0x1A3A16;
                    }
                }
                px[(z + m.half) * W + (x + m.half)] = c;
            }
        for (int y = 0; y < W; y++)
            for (int x = 0; x < W; x++)
                for (int a = 0; a < scale; a++)
                    for (int b = 0; b < scale; b++)
                        img.setRGB(x * scale + a, y * scale + b, px[y * W + x]);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double o = m.half;
        // roads
        g.setStroke(new BasicStroke(Math.max(1f, scale * 1.2f)));
        for (GenMap.Lane l : m.lanes) {
            g.setColor(l.main() ? new Color(235, 220, 170, 120) : new Color(235, 220, 170, 80));
            for (int k = 1; k < l.xs().length; k += 1)
                g.drawLine((int) ((l.xs()[k - 1] + o) * scale), (int) ((l.zs()[k - 1] + o) * scale),
                    (int) ((l.xs()[k] + o) * scale), (int) ((l.zs()[k] + o) * scale));
        }
        // jungle
        g.setStroke(new BasicStroke(scale * 1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f,
            new float[] { 4f * scale, 3f * scale }, 0f));
        for (GenMap.Jungle j : m.jungle) {
            g.setColor(new Color(200, 90, 255, 220));
            for (int[] d : j.discs())
                g.draw(new Ellipse2D.Double((d[0] - d[2] + o) * scale, (d[1] - d[2] + o) * scale, 2 * d[2] * scale, 2 * d[2] * scale));
            g.setColor(new Color(255, 255, 255));
            g.fillOval((int) ((j.entry()[0] + o) * scale) - 3 * scale, (int) ((j.entry()[1] + o) * scale) - 3 * scale, 6 * scale, 6 * scale);
            g.setColor(new Color(40, 40, 40));
            g.fillOval((int) ((j.exit()[0] + o) * scale) - 3 * scale, (int) ((j.exit()[1] + o) * scale) - 3 * scale, 6 * scale, 6 * scale);
        }
        g.setStroke(new BasicStroke(Math.max(1f, scale)));
        // patches
        for (MapLayout.Mex mx : m.mexes) {
            double r = mx.value() >= 1.75 ? 5 : mx.value() >= 1.25 ? 4.2 : mx.value() >= 0.9 ? 3.4 : 2.8;
            Color c = mx.value() >= 1.75 ? new Color(255, 215, 0) : mx.value() >= 1.25 ? new Color(255, 150, 20)
                : mx.value() >= 0.9 ? new Color(240, 240, 240) : new Color(150, 150, 150);
            double cx = (mx.dx() + o) * scale, cz = (mx.dz() + o) * scale;
            g.setColor(c);
            g.fill(new Ellipse2D.Double(cx - r * scale, cz - r * scale, 2 * r * scale, 2 * r * scale));
            g.setColor(TEAM[Math.floorMod(m.teams[mx.slot()], TEAM.length)].darker());
            g.draw(new Ellipse2D.Double(cx - r * scale, cz - r * scale, 2 * r * scale, 2 * r * scale));
        }
        for (MapLayout.Spot v : m.vents) {
            g.setColor(new Color(255, 80, 0));
            g.fillRect((int) ((v.dx() + o - 3) * scale), (int) ((v.dz() + o - 3) * scale), 6 * scale, 6 * scale);
            g.setColor(Color.BLACK);
            g.drawRect((int) ((v.dx() + o - 3) * scale), (int) ((v.dz() + o - 3) * scale), 6 * scale, 6 * scale);
        }
        Font f = new Font("DejaVu Sans", Font.BOLD, Math.max(9, 8 * scale));
        g.setFont(f);
        for (MapLayout.Site s : m.sites) {
            int cx = (int) ((s.dx() + o) * scale), cz = (int) ((s.dz() + o) * scale);
            int r = 6 * scale;
            g.setColor(new Color(255, 70, 230));
            g.fillRect(cx - r, cz - r, 2 * r, 2 * r);
            g.setColor(Color.BLACK);
            g.drawRect(cx - r, cz - r, 2 * r, 2 * r);
            String l = letter(s.kind());
            g.setColor(Color.WHITE);
            g.drawString(l, cx - g.getFontMetrics().stringWidth(l) / 2, cz + f.getSize() / 2 - 1);
        }
        for (GenMap.Camp c : m.camps) {
            int cx = (int) ((c.dx() + o) * scale), cz = (int) ((c.dz() + o) * scale);
            int r = (c.kind() == GenMap.CampKind.HYDRA ? 10 : 5) * scale;
            Polygon tri = new Polygon(new int[] { cx, cx + r, cx - r }, new int[] { cz - r, cz + r, cz + r }, 3);
            g.setColor(c.kind() == GenMap.CampKind.RELIC ? new Color(60, 230, 230) : new Color(220, 20, 20));
            g.fill(tri);
            g.setColor(Color.BLACK);
            g.draw(tri);
        }
        // seats
        for (MapLayout.Slot s : m.slots) {
            int cx = (int) ((s.dx() + o) * scale), cz = (int) ((s.dz() + o) * scale);
            int r = 9 * scale;
            g.setColor(TEAM[Math.floorMod(s.team(), TEAM.length)]);
            g.fillRect(cx - r, cz - r, 2 * r, 2 * r);
            g.setColor(Color.BLACK);
            g.drawRect(cx - r, cz - r, 2 * r, 2 * r);
            String l = String.valueOf(m.seats[s.index()].letter);
            g.drawString(l, cx - g.getFontMetrics().stringWidth(l) / 2, cz + f.getSize() / 2 - 1);
            if (m.report != null) {
                String sc = String.format("%.1f", m.report.total(s.index()));
                g.setColor(Color.WHITE);
                g.drawString(sc, cx - g.getFontMetrics().stringWidth(sc) / 2, cz + r + f.getSize() + 1);
            }
        }
        g.setColor(new Color(220, 220, 220));
        g.setStroke(new BasicStroke(scale));
        if (m.shape == GenMap.Shape.ROUND)
            g.draw(new Ellipse2D.Double(0, 0, (2 * m.half + 1) * scale - 1, (2 * m.half + 1) * scale - 1));
        else
            g.drawRect(0, 0, (2 * m.half + 1) * scale - 1, (2 * m.half + 1) * scale - 1);
        g.dispose();
        return img;
    }

    static String letter(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind k) {
        return switch (k) {
            case MINE -> "M";
            case SHRINE -> "S";
            case CAMP -> "C";
            case BELL -> "B";
            case WATCHTOWER -> "W";
            case FORGE -> "F";
            case VENT -> "V";
            case OASIS -> "O";
            case GARRISON -> "G";
        };
    }

    static int mul(int c, double f) {
        int r = clamp((int) (((c >> 16) & 255) * f)), g = clamp((int) (((c >> 8) & 255) * f)), b = clamp((int) ((c & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bb;
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static String caption(GenMap m) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("#%04X %s %s n%d", m.seed & 0xFFFF, m.shape, m.contrast, m.n));
        if (!m.modifiers.isEmpty()) {
            sb.append(" [");
            for (int i = 0; i < m.modifiers.size(); i++)
                sb.append(i > 0 ? "," : "").append(m.modifiers.get(i).name().toLowerCase());
            sb.append("]");
        }
        if (m.report != null)
            sb.append(String.format(" spread %.1f%%", 100 * m.report.spread()));
        if (!m.valid)
            sb.append(" INVALID:").append(m.invalidWhy);
        return sb.toString();
    }

    /** The key under a sheet: what every mark means. */
    static void legend(Graphics2D g, int x, int y, int w) {
        g.setFont(new Font("DejaVu Sans", Font.PLAIN, 13));
        int cx = x + 8, cy = y + 18;
        Object[][] items = {
            { "patch 0.75 / 1.0 / 1.5 / 2.0", null }, { "capture site (M mine S shrine C camp B bell W tower F forge V vent O oasis G garrison)", Color.MAGENTA },
            { "creep camp / hydra (new mechanics, data only)", Color.RED }, { "relic (new)", new Color(60, 230, 230) },
            { "jungle (reserved), white = entry, dark = exit", new Color(200, 90, 255) }, { "geothermal vent", new Color(255, 80, 0) },
            { "seat: team colour, archetype letter ($ Prospector W Warden O Open B Bastion M Moated K Banks P Plain), score in metal/s", Color.GREEN } };
        for (Object[] it : items) {
            String t = (String) it[0];
            if (it[1] == null) {
                double[] rs = { 2.8, 3.4, 4.2, 5 };
                Color[] cs = { new Color(150, 150, 150), new Color(240, 240, 240), new Color(255, 150, 20), new Color(255, 215, 0) };
                for (int k = 0; k < 4; k++) {
                    g.setColor(cs[k]);
                    g.fill(new Ellipse2D.Double(cx + k * 14, cy - 8, rs[k] * 2, rs[k] * 2));
                }
                g.setColor(Color.WHITE);
                g.drawString(t, cx + 62, cy);
                cx += 62 + g.getFontMetrics().stringWidth(t) + 24;
            } else {
                g.setColor((Color) it[1]);
                g.fillRect(cx, cy - 10, 12, 12);
                g.setColor(Color.WHITE);
                g.drawString(t, cx + 16, cy);
                cx += 16 + g.getFontMetrics().stringWidth(t) + 24;
            }
            if (cx > x + w - 400) {
                cx = x + 8;
                cy += 20;
            }
        }
        g.setColor(new Color(200, 200, 200));
        g.drawString("ground: hill-shaded by height tier (valley 1, lowland 4, T1 8, T2 12, crest 16), dark lines = cliffs, blue = water (light = ford), dark green = forest/trees, pale lines = roads", x + 8, cy + 20);
    }

    /** A sheet with a title, captions and the legend. */
    public static void sheetWithLegend(List<GenMap> maps, int cols, int cell, String title, File out) throws Exception {
        int rows = (maps.size() + cols - 1) / cols;
        int capH = 34, titleH = 30, legendH = 90;
        BufferedImage sheet = new BufferedImage(cols * cell, titleH + rows * (cell + capH) + legendH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(18, 18, 20));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.setFont(new Font("DejaVu Sans", Font.BOLD, 20));
        g.drawString(title, 8, 23);
        g.setFont(new Font("DejaVu Sans", Font.PLAIN, 13));
        for (int i = 0; i < maps.size(); i++) {
            GenMap m = maps.get(i);
            BufferedImage im = render(m, 2);
            int x = (i % cols) * cell, y = titleH + (i / cols) * (cell + capH);
            g.drawImage(im, x + 3, y + capH, cell - 6, cell - 6, null);
            g.setColor(Color.WHITE);
            String cap = caption(m);
            g.drawString(cap.length() > 90 ? cap.substring(0, 90) : cap, x + 4, y + 14);
            if (m.report != null) {
                StringBuilder sb = new StringBuilder();
                for (int s = 0; s < m.n; s++)
                    sb.append(m.seats[s].letter).append(String.format("%.1f ", m.report.total(s)));
                sb.append(String.format(" | team %.1f%%", 100 * m.report.teamSpread()));
                g.setColor(new Color(200, 200, 200));
                g.drawString(sb.toString(), x + 4, y + 29);
            }
        }
        legend(g, 0, titleH + rows * (cell + capH), sheet.getWidth());
        g.dispose();
        ImageIO.write(sheet, "png", out);
    }

    /** A sheet of maps with captions, {@code cols} wide, each scaled to {@code cell} pixels. */
    public static void sheet(List<GenMap> maps, int cols, int cell, String title, File out) throws Exception {
        int rows = (maps.size() + cols - 1) / cols;
        int capH = 34, titleH = title == null ? 0 : 30;
        BufferedImage sheet = new BufferedImage(cols * cell, titleH + rows * (cell + capH), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(18, 18, 20));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (title != null) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("DejaVu Sans", Font.BOLD, 18));
            g.drawString(title, 8, 22);
        }
        g.setFont(new Font("DejaVu Sans", Font.PLAIN, 12));
        for (int i = 0; i < maps.size(); i++) {
            GenMap m = maps.get(i);
            BufferedImage im = render(m, 2);
            int x = (i % cols) * cell, y = titleH + (i / cols) * (cell + capH);
            g.drawImage(im, x + 3, y + capH, cell - 6, cell - 6, null);
            g.setColor(Color.WHITE);
            String cap = caption(m);
            g.drawString(cap.length() > 70 ? cap.substring(0, 70) : cap, x + 4, y + 14);
            if (m.report != null) {
                StringBuilder sb = new StringBuilder();
                for (int s = 0; s < m.n; s++)
                    sb.append(m.seats[s].letter).append(String.format("%.1f ", m.report.total(s)));
                g.setColor(new Color(200, 200, 200));
                String l2 = sb.toString();
                g.drawString(l2.length() > 80 ? l2.substring(0, 80) : l2, x + 4, y + 29);
            }
        }
        g.dispose();
        ImageIO.write(sheet, "png", out);
    }
}
