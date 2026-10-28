package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 20_combat: combat feel (lovish, Oct 28) seen up close, on the scars pad (scene 16) before the fog scene. The tour's
 * fights are busy and far - a death or a spark there is a few pixels among many - so this stages them where nothing
 * else moves:
 * <ul>
 *   <li>one of ours with an iron sword east of a row of five still dummies (AI off): two vindicators in iron armour,
 *   two ghouls, a paladin body; two light blows each - sparks on the armoured ones, dust on the others
 *   (20_combat_hits);</li>
 *   <li>then a killing blow each from the same side: every body should fall west, away from the sword, fold and settle
 *   (20_combat_collapse_falling, 20_combat_collapse_down) and sink away; the log says how many collapsed;</li>
 *   <li>then a radius-4 blast a few blocks south: the RTS shake (the log gives the trauma it earned; 20_combat_shake).</li>
 * </ul>
 * Each still gets its ten-second clip like every other. Wall-clock timeline (a slow client draws a few frames a
 * second); the damage goes through the real server path, so the client sees what a player would.
 */
final class EyesCombatScene {
    private EyesCombatScene() { }

    private static final String[] DUMMIES = { "vindicator_unit", "zombie_unit", "iron_golem_unit", "zombie_unit",
        "vindicator_unit" };
    private static final String DUMMY_OWNER = "eyes_dummies";

    private static int phase = 0;
    private static long phaseNanos = 0;
    private static Vec3 centre;
    private static volatile String serverNote = null;

    private static double seconds() {
        return (System.nanoTime() - phaseNanos) / 1e9;
    }

    private static void next(int p) {
        phase = p;
        phaseNanos = System.nanoTime();
    }

    /** One client tick; true when the scene is over. {@code site}: the scars pad's centre (null: beside our capitol). */
    static boolean tick(Minecraft mc, BlockPos site) {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            ReignOfNether.LOGGER.warn("[Eyes] 20_combat: no level, player or integrated server - skipped");
            return true;
        }
        double t = seconds();
        switch (phase) {
            case 0 -> {
                setUp(mc, site);
                next(1);
            }
            case 1 -> {
                if (t >= 3) {
                    server(mc, false);
                    next(2);
                }
            }
            case 2 -> {   // the second light blow, then the still while the sparks fly
                if (t >= 0.4) {
                    server(mc, false);
                    next(3);
                }
            }
            case 3 -> {
                if (t >= 0.1) {
                    ReignOfNether.LOGGER.info("[Eyes] 20_combat_hits: {}", serverNote);
                    EyesDirector.shot(mc, "20_combat_hits");
                    next(4);
                }
            }
            case 4 -> {   // the killing blows, all from the east
                if (t >= 1.5) {
                    server(mc, true);
                    next(5);
                }
            }
            case 5 -> {
                if (t >= 0.45) {
                    log("20_combat_collapse_falling");
                    EyesDirector.shot(mc, "20_combat_collapse_falling");
                    next(6);
                }
            }
            case 6 -> {
                if (t >= 0.75) {
                    log("20_combat_collapse_down");
                    EyesDirector.shot(mc, "20_combat_collapse_down");
                    next(7);
                }
            }
            case 7 -> {   // a blast a few blocks south of the bodies: the RTS view's shake
                if (t >= 2) {
                    var server = mc.getSingleplayerServer();
                    Vec3 at = centre.add(0, 0, 6);
                    server.execute(() -> com.solegendary.reignofnether.barfx.BarFx.explosion(server.overworld(), at, 4f,
                        com.solegendary.reignofnether.barfx.BarFx.K_TNT));
                    next(8);
                }
            }
            case 8 -> {
                if (t >= 0.25) {
                    ReignOfNether.LOGGER.info("[Eyes] 20_combat_shake: a radius-4 blast 6 blocks from the screen centre at zoom {}: "
                        + "trauma {} (setting {}, offset {} / {} blocks)", OrthoviewClientEvents.getZoom(),
                        String.format("%.2f", com.solegendary.reignofnether.barfx.CameraShakeClient.trauma()),
                        com.solegendary.reignofnether.config.DisplaySettings.cameraShake(),
                        String.format("%.3f", com.solegendary.reignofnether.barfx.CameraShakeClient.offsetX()),
                        String.format("%.3f", com.solegendary.reignofnether.barfx.CameraShakeClient.offsetY()));
                    EyesDirector.shot(mc, "20_combat_shake");
                    next(9);
                }
            }
            case 9 -> {
                if (t >= 3) {
                    cleanUp(mc);
                    phase = 0;
                    return true;
                }
            }
            default -> {
                return true;
            }
        }
        return seconds() > 60 && phase > 0 && phase < 9 ? giveUp(mc) : false;
    }

    private static boolean giveUp(Minecraft mc) {
        ReignOfNether.LOGGER.warn("[Eyes] 20_combat: stuck in phase {} - ending the scene", phase);
        cleanUp(mc);
        phase = 0;
        return true;
    }

    private static void log(String still) {
        ReignOfNether.LOGGER.info("[Eyes] {}: {} collapses running (cap {}); death collapse setting {}", still,
            com.solegendary.reignofnether.unit.modelling.motion.DeathCollapseClient.running(),
            com.solegendary.reignofnether.unit.modelling.motion.DeathCollapse.MAX_CONCURRENT,
            com.solegendary.reignofnether.config.DisplaySettings.deathCollapse());
    }

    private static void setUp(Minecraft mc, BlockPos site) {
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        mc.options.hideGui = true;
        BlockPos home = site != null ? site
            : EyesDirector.capitolCentre != null ? EyesDirector.capitolCentre.offset(0, 0, 30) : mc.player.blockPosition();
        centre = EyesDirector.ground(mc, home.getX(), home.getZ(), home.getY());
        String me = mc.player.getName().getString().replace("\"", "");
        for (int i = 0; i < DUMMIES.length; i++) {
            int x = (int) Math.floor(centre.x) - 1, z = (int) Math.floor(centre.z) - 4 + 2 * i;
            Vec3 at = EyesDirector.ground(mc, x, z, (int) centre.y);
            EyesDirector.runCommand(mc, String.format("rtsapi-summon \"%s\" %s:%s %d %d %d {NoAI:1b}", DUMMY_OWNER,
                ReignOfNether.MOD_ID, DUMMIES[i], x, (int) at.y, z));
        }
        Vec3 a = EyesDirector.ground(mc, (int) Math.floor(centre.x) + 2, (int) Math.floor(centre.z), (int) centre.y);
        EyesDirector.runCommand(mc, String.format("rtsapi-summon \"%s\" %s:militia_unit %d %d %d {NoAI:1b}", me,
            ReignOfNether.MOD_ID, (int) Math.floor(a.x), (int) a.y, (int) Math.floor(a.z)));
        EyesDirector.setZoom(12);
        OrthoviewClientEvents.centreCameraOnPosInstant(centre);
        ReignOfNether.LOGGER.info("[Eyes] 20_combat: five dummies and a swordsman summoned at {}", EyesDirector.fmt(centre));
    }

    /** On the server thread: every dummy takes a blow from our swordsman (east of them); {@code kill}: a lethal one. */
    private static void server(Minecraft mc, boolean kill) {
        var server = mc.getSingleplayerServer();
        String me = mc.player.getName().getString();
        Vec3 c = centre;
        server.execute(() -> {
            try {
                var level = server.overworld();
                LivingEntity sword = null;
                List<LivingEntity> dummies = new ArrayList<>();
                for (LivingEntity le : new ArrayList<>(UnitServerEvents.getAllUnits())) {
                    if (!(le instanceof Unit u) || le.distanceToSqr(c) > 12 * 12 || !le.isAlive())
                        continue;
                    if (DUMMY_OWNER.equals(u.getOwnerName()))
                        dummies.add(le);
                    else if (me.equals(u.getOwnerName()) && le.getType().toString().contains("militia"))
                        sword = le;
                }
                if (sword == null || dummies.isEmpty()) {
                    serverNote = "missing units: swordsman " + (sword != null) + ", dummies " + dummies.size();
                    return;
                }
                sword.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                if (sword instanceof Mob m)
                    m.setNoAi(true);
                int armoured = 0, died = 0;
                for (LivingEntity d : dummies) {
                    if (d.getType().toString().contains("vindicator")) {
                        d.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                        d.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                        armoured++;
                    }
                    d.invulnerableTime = 0;
                    d.hurt(level.damageSources().mobAttack(sword), kill ? 10000f : 1f);
                    if (kill && d.isAlive()) {
                        d.kill();   // the blow did not land (a rule refused it): it still dies, without a direction
                        died--;
                    }
                    if (!d.isAlive())
                        died++;
                }
                serverNote = String.format("%d dummies (%d in iron armour) hit by a swordsman with %s; %d killed by the blow",
                    dummies.size(), armoured, sword.getMainHandItem().getItem(), died);
                if (kill)
                    ReignOfNether.LOGGER.info("[Eyes] 20_combat: {}", serverNote);
            } catch (Exception e) {
                serverNote = "failed: " + e;
                ReignOfNether.LOGGER.error("[Eyes] 20_combat: the server side failed", e);
            }
        });
    }

    private static void cleanUp(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        Vec3 c = centre;
        if (server == null || c == null)
            return;
        server.execute(() -> {
            for (LivingEntity le : new ArrayList<>(UnitServerEvents.getAllUnits()))
                if (le instanceof Unit && le.distanceToSqr(c) < 12 * 12 && le.getType().toString().contains("militia"))
                    le.discard();
        });
    }
}
