package com.solegendary.reignofnether.hero;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Possessed combat feel and first-person polish (design/hero_mode.md, hero slice 3), drawn into the possession HUD
 * (PossessionClientEvents calls in) plus the world-space damage numbers. Inspiration: Silica's first-person vehicle
 * combat (a reticle that tells you whether the target is in your gun's reach; a compass and radar to navigate by) and
 * Deadlock's ability feedback (a key press shows at once, a refused cast says why, a cooldown coming back chimes).
 *
 * <ul>
 *   <li><b>Crosshair</b> adapts to the unit: melee brackets that close in when a hostile is within reach; a ranged
 *       ring with "distance / range" that opens up when the target is out of range. Red = hostile in reach, pink =
 *       hostile out of reach, blue = friendly. The aim ray runs once a tick, not per frame.</li>
 *   <li><b>Hit marker</b> (an X round the crosshair, red and longer on a kill) and a tick sound on every HIT the server
 *       confirms; optional floating <b>damage numbers</b> (Mod config hero_damage_numbers, or the Esc menu).</li>
 *   <li><b>Cast feedback</b>: the slot flashes on the key press (no round trip), then gold when the server took the
 *       cast or red with the reason ("ON COOLDOWN", "NOT ENOUGH MANA", ...) when it didn't.</li>
 *   <li><b>Cooldown-ready cue</b>: a slot whose cooldown runs out pulses and chimes.</li>
 *   <li><b>Compass</b> strip along the top with the objective's bearing, and an arrow round the crosshair toward an
 *       objective that is off to the side.</li>
 *   <li><b>Radar terrain</b>: cliffs (2+ blocks of height change) and water outlined under the radar's unit dots,
 *       from the client's height map, rebuilt every half second (48 x 48 cells of 2 blocks).</li>
 * </ul>
 * All state is fixed-size arrays: nothing allocated per frame except the few strings the HUD already draws.
 */
public final class PossessionHudClient {
    private PossessionHudClient() { }

    private static final Minecraft MC = Minecraft.getInstance();

    /** The compass strip's bottom edge: the objective banner sits under it. */
    public static final int COMPASS_BOTTOM = 15;
    static final int COMPASS_HALF_W = 100;
    static final float COMPASS_SPAN_DEG = 75f;

    // ---- aim (per tick) ----
    private static boolean ranged;
    private static double reach, aimDist = -1;
    private static boolean aimHostile, inReach;
    private static String aimText = "";
    private static int aimTextDist = -1, aimTextRange = -1;

    // ---- hit marker / damage numbers ----
    static final long HIT_MS = 180, KILL_MS = 380, NUMBER_MS = 900;
    static final int NUMBERS = 32;
    private static long hitAt = 0, hitSoundAt = 0;
    private static boolean hitKill;
    private static final double[] numX = new double[NUMBERS], numY = new double[NUMBERS], numZ = new double[NUMBERS];
    private static final long[] numBorn = new long[NUMBERS];
    private static final String[] numText = new String[NUMBERS];
    private static final int[] numColour = new int[NUMBERS];
    private static int numNext = 0, numLive = 0;

    // ---- ability slots ----
    static final long PRESS_MS = 120, CAST_MS = 450, READY_MS = 600, MESSAGE_MS = 1100;
    private static final long[] pressAt = new long[4], castAt = new long[4], readyAt = new long[4];
    private static final int[] castResult = new int[4];
    private static final float[] prevCooldown = new float[4];
    private static String message = "";
    private static long messageAt = 0;

    // ---- radar terrain ----
    static final int GRID = 48, CELL = 2, TERRAIN_EVERY = 10;
    static final byte EDGE = 1, WATER = 2;
    private static final int[] heights = new int[GRID * GRID];
    private static final byte[] terrain = new byte[GRID * GRID];
    private static int gridX, gridZ, terrainAge = TERRAIN_EVERY;
    private static boolean terrainValid = false;
    private static final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    static void reset() {
        aimDist = -1;
        hitAt = 0;
        numLive = 0;
        for (int i = 0; i < NUMBERS; i++)
            numBorn[i] = 0;
        for (int i = 0; i < 4; i++) {
            pressAt[i] = castAt[i] = readyAt[i] = 0;
            prevCooldown[i] = 0;   // no "ready" chime for what was already ready when we took the unit
        }
        message = "";
        terrainValid = false;
        terrainAge = TERRAIN_EVERY;
    }

    // ---- options ----

    static boolean damageNumbers() {
        try {
            return ReignOfNetherClientConfigs.HERO_DAMAGE_NUMBERS.get();
        } catch (IllegalStateException e) {   // config not loaded yet
            return true;
        }
    }

    static void toggleDamageNumbers() {
        try {
            ReignOfNetherClientConfigs.HERO_DAMAGE_NUMBERS.set(!damageNumbers());
        } catch (IllegalStateException ignored) {
        }
    }

    static Component damageNumbersLabel() {
        return Component.literal("Damage numbers: " + (damageNumbers() ? "On" : "Off"));
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        MC.getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    // ---- server messages ----

    static void onHit(PossessionClientboundPacket p) {
        long now = System.currentTimeMillis();
        boolean kill = (p.objective & PossessionClientboundPacket.HIT_KILL) != 0;
        // a kill keeps its longer red marker even if a lesser hit lands in the same instant
        if (kill || !hitKill || now - hitAt > KILL_MS) {
            hitKill = kill;
            hitAt = now;
        }
        if (now - hitSoundAt > 50) {   // an area ability hitting a crowd: one tick, not a dozen
            hitSoundAt = now;
            if (kill)
                sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 0.55f);
            else
                sound(SoundEvents.ARROW_HIT_PLAYER, 1.4f, 0.35f);
        }
        if (!damageNumbers())
            return;
        int i = numNext;
        numNext = (numNext + 1) % NUMBERS;
        int tenths = p.targetId;
        numText[i] = tenths % 10 == 0 || tenths >= 100 ? String.valueOf(Math.round(tenths / 10f))
            : String.valueOf(tenths / 10f);
        numColour[i] = kill ? 0xFF5050 : tenths >= 100 ? 0xFFD27A : 0xFFFFFF;
        // a little scatter so a burst of hits on one target doesn't print on top of itself
        numX[i] = p.x + (i % 3 - 1) * 0.25;
        numY[i] = p.y + 0.2;
        numZ[i] = p.z + ((i / 3) % 3 - 1) * 0.25;
        numBorn[i] = now;
        numLive = NUMBERS;
    }

    static void onKeyPressed(int slot) {
        if (slot >= 0 && slot < 4)
            pressAt[slot] = System.currentTimeMillis();
    }

    static void onCast(int slot, int result) {
        if (slot < 0 || slot >= 4)
            return;
        long now = System.currentTimeMillis();
        castAt[slot] = now;
        castResult[slot] = result;
        if (result == PossessionClientboundPacket.CAST_OK) {
            sound(SoundEvents.EVOKER_CAST_SPELL, 1.5f, 0.3f);
            return;
        }
        message = switch (result) {
            case PossessionClientboundPacket.CAST_COOLDOWN -> "ON COOLDOWN";
            case PossessionClientboundPacket.CAST_MANA -> "NOT ENOUGH MANA";
            case PossessionClientboundPacket.CAST_RANK -> "NOT LEARNED - ALT + " + (slot + 1) + " TO RANK UP";
            case PossessionClientboundPacket.CAST_BUSY -> "STILL CASTING";
            default -> "CAN'T CAST THAT NOW";
        };
        messageAt = now;
        sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.5f);
    }

    // ---- per tick ----

    static void tick(LivingEntity mob, List<Ability> slots) {
        updateAim(mob);
        if (mob instanceof Unit unit) {
            long now = System.currentTimeMillis();
            boolean chimed = false;
            for (int i = 0; i < 4; i++) {
                float cd = i < slots.size() ? unit.getCooldown(slots.get(i)) : 0;
                if (prevCooldown[i] > 0 && cd <= 0) {
                    readyAt[i] = now;
                    if (!chimed) {
                        chimed = true;
                        sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f, 0.4f);
                    }
                }
                prevCooldown[i] = cd;
            }
        }
        if (++terrainAge >= TERRAIN_EVERY) {
            terrainAge = 0;
            rebuildTerrain(mob);
        }
    }

    /** What the crosshair is on (the server's own look-ray rules: PossessionServer.attack's reach). */
    static void updateAim(LivingEntity mob) {
        if (MC.level == null || MC.player == null)
            return;
        ranged = mob instanceof RangedAttackerUnit;
        float range = mob instanceof AttackerUnit a ? a.getAttackRange() : 0;
        reach = ranged ? Math.max(range, 4) : Math.max(range, 1.5f) + mob.getBbWidth() * 0.5 + 1.0;
        Vec3 eye = mob.getEyePosition();
        Vec3 dir = Vec3.directionFromRotation(MC.player.getXRot(), MC.player.getYRot());
        double maxD = Math.max(reach, 32);
        Vec3 end = eye.add(dir.scale(maxD));
        HitResult block = MC.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        if (block.getType() != HitResult.Type.MISS)
            end = block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(MC.level, mob, eye, end, new AABB(eye, end).inflate(1.0),
            e -> e instanceof LivingEntity && e != mob && e.isAlive() && !(e instanceof Player) && !e.isSpectator());
        if (hit == null) {
            aimDist = -1;
            inReach = false;
            return;
        }
        aimDist = eye.distanceTo(hit.getLocation());
        String mine = mob instanceof Unit u ? u.getOwnerName() : "";
        String theirs = hit.getEntity() instanceof Unit tu ? tu.getOwnerName() : "";
        aimHostile = theirs == null || theirs.isEmpty() || mine == null
            || (!theirs.equals(mine) && !AlliancesClient.isAllied(mine, theirs));
        inReach = aimDist <= reach;
        int d = (int) Math.round(aimDist), r = (int) Math.round(range);
        if (d != aimTextDist || r != aimTextRange) {   // rebuilt when the numbers change, not every frame
            aimTextDist = d;
            aimTextRange = r;
            aimText = d + " / " + r + "m";
        }
    }

    static void rebuildTerrain(LivingEntity mob) {
        if (MC.level == null)
            return;
        gridX = mob.getBlockX();
        gridZ = mob.getBlockZ();
        int half = GRID / 2;
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int x = gridX + (gx - half) * CELL, z = gridZ + (gz - half) * CELL;
                int i = gz * GRID + gx;
                if (!MC.level.hasChunk(x >> 4, z >> 4)) {
                    heights[i] = Integer.MIN_VALUE;
                    terrain[i] = 0;
                    continue;
                }
                int h = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                heights[i] = h;
                probe.set(x, h - 1, z);
                terrain[i] = MC.level.getFluidState(probe).is(FluidTags.WATER) ? WATER : 0;
            }
        }
        // a cliff edge: two or more blocks of height between neighbouring cells (a walker can't step that)
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int i = gz * GRID + gx;
                if (terrain[i] != 0 || heights[i] == Integer.MIN_VALUE)
                    continue;
                int h = heights[i];
                if ((gx + 1 < GRID && heights[i + 1] != Integer.MIN_VALUE && Math.abs(heights[i + 1] - h) >= 2)
                        || (gz + 1 < GRID && heights[i + GRID] != Integer.MIN_VALUE && Math.abs(heights[i + GRID] - h) >= 2))
                    terrain[i] = EDGE;
            }
        }
        terrainValid = true;
    }

    // ---- drawing (from PossessionClientEvents.onRenderGui) ----

    static void drawCrosshair(GuiGraphics gg, Font font, int w, int h) {
        int cx = w / 2, cy = h / 2;
        int col = aimDist < 0 ? 0xE0FFFFFF : !aimHostile ? 0xE080C0FF : inReach ? 0xFFFF4040 : 0xD0FFA0A0;
        gg.fill(cx, cy, cx + 1, cy + 1, col);
        if (!ranged) {
            // melee: brackets that close in on a hostile within reach
            int g = inReach && aimHostile ? 3 : 6;
            gg.fill(cx - g - 2, cy - 3, cx - g - 1, cy + 4, col);
            gg.fill(cx - g - 2, cy - 3, cx - g, cy - 2, col);
            gg.fill(cx - g - 2, cy + 3, cx - g, cy + 4, col);
            gg.fill(cx + g + 2, cy - 3, cx + g + 3, cy + 4, col);
            gg.fill(cx + g + 1, cy - 3, cx + g + 3, cy - 2, col);
            gg.fill(cx + g + 1, cy + 3, cx + g + 3, cy + 4, col);
        } else {
            // ranged: four ticks that open up when the aimed target is beyond the unit's range
            int r = aimDist >= 0 && !inReach ? 9 : 5;
            gg.fill(cx - r - 3, cy, cx - r, cy + 1, col);
            gg.fill(cx + r + 1, cy, cx + r + 4, cy + 1, col);
            gg.fill(cx, cy - r - 3, cx + 1, cy - r, col);
            gg.fill(cx, cy + r + 1, cx + 1, cy + r + 4, col);
            if (aimDist >= 0)
                gg.drawString(font, aimText, cx - font.width(aimText) / 2, cy + r + 7, col, true);
        }
        long age = System.currentTimeMillis() - hitAt;
        long life = hitKill ? KILL_MS : HIT_MS;
        if (age >= 0 && age < life) {
            int c = hitKill ? 0xFFFF3030 : 0xFFFFFFFF;
            int from = 4, to = hitKill ? 9 : 7;
            for (int d = from; d <= to; d++) {
                gg.fill(cx - d, cy - d, cx - d + 1, cy - d + 1, c);
                gg.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, c);
                gg.fill(cx - d, cy + d, cx - d + 1, cy + d + 1, c);
                gg.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, c);
            }
        }
    }

    /** Compass bearing (0 = north / -Z, 90 = east / +X) of a direction. */
    static float bearingOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(dx, -dz));
    }

    static void drawCompass(GuiGraphics gg, Font font, LivingEntity mob, int w, int h, boolean hasObjective,
                            double objX, double objZ) {
        if (MC.player == null)
            return;
        int cx = w / 2, top = 2, half = COMPASS_HALF_W;
        float bearing = Mth.wrapDegrees(MC.player.getYRot() + 180f);   // yaw 180 faces north
        gg.fill(cx - half, top, cx + half, COMPASS_BOTTOM - 1, 0x80101418);
        for (int a = 0; a < 360; a += 15) {
            float rel = Mth.wrapDegrees(a - bearing);
            if (Math.abs(rel) > COMPASS_SPAN_DEG)
                continue;
            int x = cx + Math.round(rel * half / COMPASS_SPAN_DEG);
            if (a % 90 == 0) {
                String l = a == 0 ? "N" : a == 90 ? "E" : a == 180 ? "S" : "W";
                gg.drawString(font, l, x - font.width(l) / 2, top + 2, a == 0 ? 0xFFFF6060 : 0xFFE0E0E0, false);
            } else {
                int th = a % 45 == 0 ? 5 : 3;
                gg.fill(x, COMPASS_BOTTOM - 1 - th, x + 1, COMPASS_BOTTOM - 1, 0xC0B0B0B0);
            }
        }
        gg.fill(cx, COMPASS_BOTTOM - 1, cx + 1, COMPASS_BOTTOM + 2, 0xFFFFFFFF);   // where you face
        if (!hasObjective)
            return;
        double dx = objX - mob.getX(), dz = objZ - mob.getZ();
        float rel = Mth.wrapDegrees(bearingOf(dx, dz) - bearing);
        if (Math.abs(rel) <= COMPASS_SPAN_DEG) {
            int x = cx + Math.round(rel * half / COMPASS_SPAN_DEG);
            gg.fill(x - 2, top + 4, x + 3, top + 9, 0xFFFFD000);
        } else {
            String arrow = rel > 0 ? ">" : "<";
            gg.drawString(font, arrow, rel > 0 ? cx + half - 6 : cx - half + 2, top + 2, 0xFFFFD000, false);
        }
        // an objective off to the side: a chevron round the crosshair pointing the way to turn
        if (Math.abs(rel) > 12) {
            float th = rel * Mth.DEG_TO_RAD;
            float sx = Mth.sin(th), sy = -Mth.cos(th);
            int ccx = w / 2, ccy = h / 2;
            for (int k = 0; k < 3; k++) {
                int rr = 30 + k * 4;
                int px = ccx + Math.round(sx * rr), py = ccy + Math.round(sy * rr);
                int s = k == 2 ? 2 : 1;
                gg.fill(px - s, py - s, px + s + 1, py + s + 1, 0xE0FFD000);
            }
        }
    }

    static void drawSlotFx(GuiGraphics gg, int i, int x, int y, int size) {
        if (i < 0 || i >= 4)
            return;
        long now = System.currentTimeMillis();
        long press = now - pressAt[i];
        if (press >= 0 && press < PRESS_MS)
            gg.fill(x, y, x + size, y + size, alpha(0x60, press, PRESS_MS) | 0xFFFFFF);
        long cast = now - castAt[i];
        if (cast >= 0 && cast < CAST_MS) {
            if (castResult[i] == PossessionClientboundPacket.CAST_OK)
                border(gg, x, y, size, 0, alpha(0xFF, cast, CAST_MS) | 0xFFD27A);
            else
                gg.fill(x, y, x + size, y + size, alpha(0x90, cast, CAST_MS) | 0xD02020);
        }
        long ready = now - readyAt[i];
        if (ready >= 0 && ready < READY_MS) {
            int grow = (int) (ready * 4 / READY_MS);
            border(gg, x, y, size, grow, alpha(0xFF, ready, READY_MS) | 0xFFFFFF);
        }
    }

    static void drawCastMessage(GuiGraphics gg, Font font, int w, int h) {
        long age = System.currentTimeMillis() - messageAt;
        if (message.isEmpty() || age < 0 || age > MESSAGE_MS)
            return;
        int c = alpha(0xFF, Math.max(0, age - 600), MESSAGE_MS - 600) | 0xFF6050;
        gg.drawString(font, message, w / 2 - font.width(message) / 2, h / 2 + 24, c, true);
    }

    private static int alpha(int max, long age, long life) {
        int a = (int) (max * (1f - age / (float) life));
        return Math.max(8, Math.min(255, a)) << 24;
    }

    private static void border(GuiGraphics gg, int x, int y, int size, int grow, int c) {
        int x0 = x - grow, y0 = y - grow, x1 = x + size + grow, y1 = y + size + grow;
        gg.fill(x0, y0, x1, y0 + 1, c);
        gg.fill(x0, y1 - 1, x1, y1, c);
        gg.fill(x0, y0, x0 + 1, y1, c);
        gg.fill(x1 - 1, y0, x1, y1, c);
    }

    /** Cliffs and water under the radar dots, rotated with the view like them. */
    static void drawRadarTerrain(GuiGraphics gg, LivingEntity mob, int cx, int cy, int r, float scale,
                                 float fx, float fz, float rx, float rz) {
        if (!terrainValid)
            return;
        int half = GRID / 2;
        double offX = mob.getX() - gridX, offZ = mob.getZ() - gridZ;
        float range2 = (r / scale) * (r / scale);
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                byte t = terrain[gz * GRID + gx];
                if (t == 0)
                    continue;
                float dx = (float) ((gx - half) * CELL + 0.5 - offX), dz = (float) ((gz - half) * CELL + 0.5 - offZ);
                if (dx * dx + dz * dz > range2)
                    continue;
                int px = cx + Math.round((dx * rx + dz * rz) * scale);
                int py = cy - Math.round((dx * fx + dz * fz) * scale);
                if (t == WATER)
                    gg.fill(px - 1, py - 1, px + 1, py + 1, 0x80305A9C);
                else
                    gg.fill(px, py, px + 1, py + 1, 0xA0A8B0B8);
            }
        }
    }

    // ---- damage numbers, in the world ----

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
        if (numLive == 0 || evt.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || MC.level == null)
            return;
        if (!PossessionClientEvents.isPossessing()) {
            numLive = 0;
            return;
        }
        long now = System.currentTimeMillis();
        Camera cam = evt.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = evt.getPoseStack();
        MultiBufferSource.BufferSource buf = MC.renderBuffers().bufferSource();
        Font font = MC.font;
        boolean any = false;
        for (int i = 0; i < NUMBERS; i++) {
            long age = now - numBorn[i];
            if (numBorn[i] == 0 || age < 0 || age > NUMBER_MS || numText[i] == null)
                continue;
            any = true;
            float t = age / (float) NUMBER_MS;
            double x = numX[i] - cp.x, y = numY[i] + t * 0.9 - cp.y, z = numZ[i] - cp.z;
            // a bit bigger with distance, so a ranged hit 20 blocks out still reads
            float s = 0.022f + (float) Math.sqrt(x * x + y * y + z * z) * 0.0012f;
            int a = t < 0.6f ? 255 : (int) (255 * (1f - (t - 0.6f) / 0.4f));
            ps.pushPose();
            ps.translate(x, y, z);
            ps.mulPose(cam.rotation());
            ps.scale(-s, -s, s);
            Matrix4f m = ps.last().pose();
            String text = numText[i];
            font.drawInBatch(text, -font.width(text) / 2f, 0, (Math.max(8, a) << 24) | numColour[i], true, m, buf,
                Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            ps.popPose();
        }
        buf.endBatch();
        if (!any)
            numLive = 0;
    }
}
