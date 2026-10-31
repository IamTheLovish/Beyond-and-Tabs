package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.AbilityServerboundPacket;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.FactionHeroAbility;
import com.solegendary.reignofnether.hero.HeroKit;
import com.solegendary.reignofnether.hero.HeroKits;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 19_heroes: how heroes read from the RTS camera (eyes.yml input {@code heroes}: after 02, nothing else of the tour runs).
 * Eyes-only, off by default, no gameplay change: the hero PRs' stills (design-draft-heroes-better-2026-10-30 #4 - the
 * Ash Shaman's own skin, PR 422; the player-colour ring, crest and unspent-point badge, PR 424 - which the GameTests
 * cannot show, since the server runs no renderer). On a build without those PRs the same stills are the "before".
 * <p>
 * Beside our base, every hero summoned with the game's own op command (rtsapi-summon, NoAI so they hold their pose):
 * <ul>
 * <li>ours: the Storm Witch holding her first ability point, the Warchief and the Ash Shaman side by side (the Horde's two
 *     heroes), and a cloaked Corsair Captain;</li>
 * <li>the enemy's: a Dawn Paladin, a cloaked Thornheart Druid, an Arch-Engineer standing in a thicket of theirs (the
 *     game's own cloak, ThicketCover), and a Gravecaller 34 blocks out, beyond the sight of
 *     everything of ours, for the fog still. (A client knows another player's points only for its own side: an enemy
 *     hero's count there is the client's default, so the badge rule - our heroes only - never reads it.)</li>
 * </ul>
 * Stills: 19_heroes_mid (all of them at a mid zoom; the Witch and the Paladin each hold a point), 19_heroes_spent (the
 * Witch's point spent, as the rank-up button spends it), 19_heroes_horde_pair (close on the Warchief and the Ash Shaman),
 * 19_heroes_selected (ours selected: the crest against the health bars) and 19_heroes_fog (fog of war on, the camera on
 * the far Gravecaller). Each still logs every hero's owner, points, cloak and fog state. Wall-clock timeline, like
 * scenes 16-18 (a shader-lane client runs a few ticks a second).
 */
final class EyesHeroesScene {
    private EyesHeroesScene() { }

    private static final int ZOOM_MID = 30, ZOOM_CLOSE = 14;
    private static final int OFFSET = 60, FAR = 34;
    private static final String CLOAK = "ActiveEffects:[{Id:14,Amplifier:0b,Duration:999999,ShowParticles:0b}]";

    private static int phase = 0;
    private static long phaseNanos = 0;
    private static String me, enemy;
    private static Vec3 centre, far, hordePair, bush;

    private static double seconds() {
        return (System.nanoTime() - phaseNanos) / 1e9;
    }

    private static void next(int p) {
        phase = p;
        phaseNanos = System.nanoTime();
    }

    /** One client tick of the scene; true when it is over (the caller ends the tour). */
    static boolean tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            ReignOfNether.LOGGER.warn("[Eyes] 19_heroes: no level or player - skipped");
            return true;
        }
        switch (phase) {
            case 0 -> setUp(mc);
            case 1 -> {   // the camera moved: wait for the ground (the server summons only on loaded ground)
                boolean loaded = EyesDirector.surfaceY(mc, (int) Math.floor(centre.x), (int) Math.floor(centre.z)) != Integer.MIN_VALUE
                    && EyesDirector.surfaceY(mc, (int) Math.floor(far.x), (int) Math.floor(far.z)) != Integer.MIN_VALUE;
                if ((!loaded && seconds() < 25) || seconds() < 4)
                    return false;
                summonAll(mc);
                next(2);
            }
            case 2 -> {   // wait for them on the client and the enemy's thicket to grow; then the Engineer stops fighting
                if ((ownHeroes().size() < 4 && seconds() < 30) || seconds() < 6)
                    return false;
                holdFire(mc);
                next(7);
            }
            case 7 -> {   // a fighting unit is never hidden (ThicketCover: "engaged"); past its 3 s reveal, then the first still
                if (seconds() < 5)
                    return false;
                EyesDirector.shot(mc, "19_heroes_mid");
                log("19_heroes_mid");
                spendWitchPoint(mc);
                next(3);
            }
            case 3 -> {
                if (seconds() < 4)
                    return false;
                EyesDirector.shot(mc, "19_heroes_spent");
                log("19_heroes_spent");
                EyesDirector.setZoom(ZOOM_CLOSE);
                OrthoviewClientEvents.centreCameraOnPosInstant(hordePair);
                next(4);
            }
            case 4 -> {
                if (seconds() < 4)
                    return false;
                EyesDirector.shot(mc, "19_heroes_horde_pair");
                log("19_heroes_horde_pair");
                EyesDirector.setZoom(ZOOM_MID);
                OrthoviewClientEvents.centreCameraOnPosInstant(centre);
                selectOwnHeroes();
                next(5);
            }
            case 5 -> {
                if (seconds() < 4)
                    return false;
                EyesDirector.shot(mc, "19_heroes_selected");
                log("19_heroes_selected");
                UnitClientEvents.clearSelectedUnits();
                // on the server's thread, as scene 17 does (the eyes match may already run with fog on: then nothing changes)
                var server = mc.getSingleplayerServer();
                if (server != null)
                    server.execute(() -> FogOfWarServerEvents.setEnabled(true));
                OrthoviewClientEvents.centreCameraOnPosInstant(far);
                next(6);
            }
            case 6 -> {   // fog on: give the client time to darken the chunks
                if (seconds() < 10)
                    return false;
                EyesDirector.shot(mc, "19_heroes_fog");
                log("19_heroes_fog");
                return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    private static void setUp(Minecraft mc) {
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        me = mc.player.getName().getString();
        enemy = EyesDirector.findEnemyName(mc);
        BlockPos home = EyesDirector.capitolCentre != null ? EyesDirector.capitolCentre : EyesDirector.findCapitol(mc);
        // beside our base, across the line to the map's middle (as scene 18): out of the bot's way; the far Gravecaller
        // further out along the same line
        double len = Math.sqrt((double) home.getX() * home.getX() + (double) home.getZ() * home.getZ());
        double dirX = 0, dirZ = 1;
        if (len > 32) {
            dirX = home.getZ() / len;
            dirZ = -home.getX() / len;
        }
        centre = new Vec3(Math.floor(home.getX() + dirX * OFFSET) + 0.5, home.getY(), Math.floor(home.getZ() + dirZ * OFFSET) + 0.5);
        far = centre.add(dirX * FAR, 0, dirZ * FAR);
        hordePair = centre.add(1, 0, 0);
        bush = centre.add(10, 0, -6);   // 10+ blocks from every hero of ours: beyond a thicket's 4-block reveal
        EyesDirector.setZoom(ZOOM_MID);
        OrthoviewClientEvents.centreCameraOnPosInstant(centre);
        next(1);
    }

    private static void summon(Minecraft mc, String owner, HeroKit kit, Vec3 at, String nbt) {
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z);
        int y = EyesDirector.surfaceY(mc, x, z);
        if (y == Integer.MIN_VALUE)
            y = (int) at.y;
        EyesDirector.runCommand(mc, String.format("rtsapi-summon \"%s\" %s:%s %d %d %d {NoAI:1b%s}", owner.replace("\"", ""),
            ReignOfNether.MOD_ID, kit.entityId(), x, y, z, nbt.isEmpty() ? "" : "," + nbt));
    }

    private static void summonAll(Minecraft mc) {
        // a summoned hero loads its points from the summon's nbt (none written = 0): the Witch is given her first point
        summon(mc, me, HeroKits.STORM_WITCH, centre.add(-4, 0, 0), "skillPoints:1");
        summon(mc, me, HeroKits.HORDE_WARCHIEF, hordePair.add(-1, 0, 0), "");
        summon(mc, me, HeroKits.ASH_SHAMAN, hordePair.add(1, 0, 0), "");
        summon(mc, me, HeroKits.CORSAIR_CAPTAIN, centre.add(-4, 0, 5), CLOAK);
        summon(mc, enemy, HeroKits.DAWN_PALADIN, centre.add(4, 0, 5), "");
        summon(mc, enemy, HeroKits.THORNHEART_DRUID, centre.add(8, 0, 5), CLOAK);
        summon(mc, enemy, HeroKits.GRAVECALLER, far, "");
        // the game's own cloak on a hero (review r1 on PR 443): an enemy thicket (Verdant cover, ThicketCover) with
        // the enemy's Arch-Engineer standing in it - hidden from us by the server once the bush is grown (4 s)
        var server = mc.getSingleplayerServer();
        if (server != null) {
            BlockPos at = BlockPos.containing(bush.x, EyesDirector.surfaceY(mc, (int) Math.floor(bush.x), (int) Math.floor(bush.z)), bush.z);
            String owner = enemy;
            server.execute(() -> {
                int planted = com.solegendary.reignofnether.ability.abilities.PlantThicket.plantPatch(server.overworld(), owner, at, false);
                ReignOfNether.LOGGER.info("[Eyes] 19_heroes: planted {} thicket blocks for '{}' round {}", planted, owner, at.toShortString());
            });
        }
        summon(mc, enemy, HeroKits.ARCH_ENGINEER, bush, "");
        ReignOfNether.LOGGER.info("[Eyes] 19_heroes: summoned 4 heroes for '{}' round {}, 4 for '{}' ({} far out at {}, {} in a thicket at {})",
            me, EyesDirector.fmt(centre), enemy, HeroKits.GRAVECALLER.itemName, EyesDirector.fmt(far),
            HeroKits.ARCH_ENGINEER.itemName, EyesDirector.fmt(bush));
    }

    /**
     * The enemy's heroes on Hold fire with no target, on the server's thread: the run before showed them "engaged"
     * (ThicketCover.diagnose) - a unit with a target is never hidden - so the thicket could not take the Engineer.
     */
    private static void holdFire(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null)
            return;
        server.execute(() -> {
            for (var e : server.overworld().getAllEntities())
                if (e instanceof HeroUnit && e instanceof Unit u && enemy.equals(u.getOwnerName()) && e instanceof LivingEntity le) {
                    com.solegendary.reignofnether.unit.EngagementStance.set(le, com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE);
                    if (le instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit a)
                        a.setUnitAttackTarget(null);
                    var feet = server.overworld().getBlockState(le.blockPosition());
                    ReignOfNether.LOGGER.info("[Eyes] 19_heroes: {} put on Hold fire; block at its feet {}", le.getType().getDescriptionId(), feet);
                }
        });
    }

    private static List<LivingEntity> ownHeroes() {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : UnitClientEvents.getAllUnits())
            if (le instanceof HeroUnit && le instanceof Unit u && le.isAlive() && me.equals(u.getOwnerName()))
                out.add(le);
        return out;
    }

    /** The Witch's first ability, ranked as the rank-up button ranks it (client rank, then the server's packet). */
    private static void spendWitchPoint(Minecraft mc) {
        for (LivingEntity le : ownHeroes())
            if (le instanceof FactionHero fh && fh.kit() == HeroKits.STORM_WITCH) {
                FactionHeroAbility a = HeroKits.ability(fh, 0);
                boolean ranked = a.rankUp(fh);
                if (ranked)
                    AbilityServerboundPacket.rankUpAbility(le.getId(), a.action);
                ReignOfNether.LOGGER.info("[Eyes] 19_heroes: the Witch's point {} (points now {})", ranked ? "spent" : "NOT spent",
                    fh.getSkillPoints());
                return;
            }
        ReignOfNether.LOGGER.warn("[Eyes] 19_heroes: no Witch of ours on the client to spend a point on");
    }

    private static void selectOwnHeroes() {
        UnitClientEvents.clearSelectedUnits();
        for (LivingEntity le : ownHeroes())
            UnitClientEvents.addSelectedUnitNoSort(le);
        UnitClientEvents.sortedSelectedUnits();
        if (!UnitClientEvents.getSelectedUnits().isEmpty())
            HudClientEvents.setHudSelectedEntity(UnitClientEvents.getSelectedUnits().get(0));
    }

    /** One line per hero the client holds: what a still of it should show. */
    private static void log(String still) {
        int n = 0;
        for (LivingEntity le : UnitClientEvents.getAllUnits()) {
            if (!(le instanceof HeroUnit h) || !(le instanceof Unit u) || !le.isAlive())
                continue;
            n++;
            Minecraft mc = Minecraft.getInstance();
            ReignOfNether.LOGGER.info("[Eyes] {}: {} of '{}' at {}: points {}, cloaked {}, invisible to us {}, hidden from us {}, in sight {}",
                still, le.getType().getDescriptionId(), u.getOwnerName(), EyesDirector.fmt(le.position()), h.getSkillPoints(),
                le.isInvisible(), mc.player != null && le.isInvisibleTo(mc.player), UnitClientEvents.isHiddenFromLocalPlayer(le),
                FogOfWarClientEvents.isInBrightChunk(le));
        }
        // a spectator or creative viewer may see invisible units: say which this one is (review r1 on PR 443)
        var gm = Minecraft.getInstance().gameMode;
        ReignOfNether.LOGGER.info("[Eyes] {}: {} heroes on the client, zoom {}, our game mode {}, fog on {}", still, n,
            Math.round(OrthoviewClientEvents.getZoom()), gm == null ? "?" : gm.getPlayerMode().getName(), FogOfWarClientEvents.isEnabled());
        // what the server holds (one machine: the integrated server), so a hero missing on the client is told from one
        // that was never summoned
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            int s = 0;
            for (var e : server.overworld().getAllEntities())
                if (e instanceof HeroUnit && e instanceof Unit u && e.isAlive() && e.position().distanceTo(centre) < 64) {
                    s++;
                    ReignOfNether.LOGGER.info("[Eyes] {}: server has {} of '{}' at {}, concealed {}{}", still, e.getType().getDescriptionId(),
                        u.getOwnerName(), EyesDirector.fmt(e.position()), com.solegendary.reignofnether.unit.Concealment.isConcealed(e),
                        me.equals(u.getOwnerName()) || !(e instanceof LivingEntity le2) ? ""
                            : " (cover: " + com.solegendary.reignofnether.blocks.ThicketCover.diagnose(server.overworld(), le2) + ")");
                }
            ReignOfNether.LOGGER.info("[Eyes] {}: {} heroes on the server within 64 of the scene", still, s);
        }
    }
}
