package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.player.CommanderAntiAir;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Commanders shoot flyers (player/CommanderAntiAir; lovish Oct 21 03:18: "they definitely should be able to shoot air,
 * maybe not completely massacre air but be able to stop things early game like a light swarm of air units"). Its own
 * holder; the shots are fired synchronously through CommanderAntiAir.shoot (the cooldown counted in shots), i-frames
 * cleared between them as the game's own ticks would.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommanderAntiAirGameTests {

    @Nullable
    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, double x, double y, double z, String owner, List<Mob> spawned) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(x, y, z, 0, 0);
        m.setNoGravity(true);
        ((Unit) m).setOwnerName(owner);
        ((Unit) m).setHoldPosition(true);
        level.addFreshEntity(m);
        spawned.add(m);
        return m;
    }

    /** Fires up to {@code shots} shots (one per cooldown); returns how many it took to kill every target, or -1. */
    static int volley(ServerLevel level, Mob commander, List<Mob> targets, int shots) {
        for (int i = 0; i < shots; i++) {
            if (targets.stream().noneMatch(Mob::isAlive))
                return i;
            for (Mob m : targets)
                m.invulnerableTime = 0;
            UnitGrid.invalidate();
            CommanderAntiAir.shoot(level, commander);
        }
        return targets.stream().noneMatch(Mob::isAlive) ? shots : -1;
    }

    /**
     * A commander shoots enemy flyers in reach and nothing else (not ground units, not its own side's flyers, not a
     * flyer out of reach); it stops a light early raid - two T2 gunships (90 hp each), which deal it ~6 dmg/s - within
     * 15 s, but four gunships survive 10 s of its fire: it answers a raid, it doesn't massacre an air army.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 60)
    public static void commander_shoots_flyers_stops_a_light_raid_but_not_an_air_army(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_cmdraa_" + helper.absolutePos(BlockPos.ZERO).asLong(), foe = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BlockPos o = helper.absolutePos(new BlockPos(7, 2, 7));
        double cx = o.getX() + 0.5, cy = o.getY(), cz = o.getZ() + 0.5;
        List<Mob> spawned = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int shotsIn10s = 200 / CommanderAntiAir.COOLDOWN_TICKS, shotsIn15s = 300 / CommanderAntiAir.COOLDOWN_TICKS;
        try {
            Mob cmdr = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), cx, cy, cz, owner, spawned);
            if (cmdr == null) {
                helper.fail("could not make a worker");
                return;
            }
            cmdr.setNoGravity(false);
            CommanderServerEvents.makeCommander(cmdr);
            if (!CommanderServerEvents.isCommander(cmdr)) {
                helper.fail("makeCommander did not make a commander");
                return;
            }
            // nothing to shoot: a ground foe, a friendly flyer, a foe flyer out of reach
            Mob ground = spawn(level, EntityRegistrar.CUTLASS_RAIDER_UNIT.get(), cx + 3, cy, cz, foe, spawned);
            Mob friend = spawn(level, EntityRegistrar.GULL_SPOTTER_UNIT.get(), cx - 3, cy + 5, cz, owner, spawned);
            Mob far = spawn(level, EntityRegistrar.GULL_SPOTTER_UNIT.get(), cx, cy + 5, cz + CommanderAntiAir.RANGE + 4, foe, spawned);
            UnitGrid.invalidate();
            if (CommanderAntiAir.shoot(level, cmdr) != null)
                problems.add("the commander shot at something that is not an enemy flyer in reach");
            for (Mob m : new Mob[] { ground, friend, far })
                if (m != null)
                    m.discard();
            // a light raid: two gunships
            List<Mob> raid = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Mob g = spawn(level, EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get(), cx + 3 + i, cy + 5, cz + 2, foe, spawned);
                if (g != null)
                    raid.add(g);
            }
            if (raid.size() < 2 || !((Unit) raid.get(0)).isFlyingUnit()) {
                helper.fail("could not make two flying gunships");
                return;
            }
            int took = volley(level, cmdr, raid, shotsIn15s);
            if (took < 0)
                problems.add("a commander should stop a light raid of two gunships within 15 s ("
                    + raid.stream().filter(Mob::isAlive).count() + " left)");
            else
                ReignOfNether.LOGGER.info("[AntiAir] a commander downed a 2-gunship raid in {} s", took * CommanderAntiAir.COOLDOWN_TICKS / 20f);
            // an air army: four gunships
            List<Mob> gunships = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Mob g = spawn(level, EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get(), cx - 3 - i, cy + 5, cz - 2, foe, spawned);
                if (g != null)
                    gunships.add(g);
            }
            if (gunships.size() < 4) {
                helper.fail("could not make four gunships");
                return;
            }
            volley(level, cmdr, gunships, shotsIn10s);
            long alive = gunships.stream().filter(Mob::isAlive).count();
            if (alive < 3)
                problems.add("a commander alone should not massacre an air army: only " + alive + " of 4 gunships left after 10 s");
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            for (Mob m : spawned)
                m.discard();
        }
    }

    /**
     * The shot is the commander's own (lovish 03:23, "cohesive to the commander"): every commander carries the card entry,
     * the card's switch and Hold Fire both stop it, it leaves from the off hand (beside and above the body, not the eyes),
     * and each faction's commander has its own way of doing it.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void commander_skyward_shot_is_on_its_card_switchable_and_from_the_hand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_cmdrcard_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos o = helper.absolutePos(new BlockPos(7, 2, 7));
        List<Mob> spawned = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try {
            Mob cmdr = spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), o.getX() + 0.5, o.getY(), o.getZ() + 0.5, owner, spawned);
            if (cmdr == null) {
                helper.fail("could not make a worker");
                return;
            }
            CommanderServerEvents.makeCommander(cmdr);
            Unit u = (Unit) cmdr;
            if (!com.solegendary.reignofnether.ability.abilities.SkywardShotAbility.has(u))
                problems.add("a commander should carry the Skyward Shot card entry");
            if (!CommanderAntiAir.mayShoot(cmdr))
                problems.add("a fresh commander should have its anti-air on");
            com.solegendary.reignofnether.ability.AutocastState.set(u,
                com.solegendary.reignofnether.ability.abilities.SkywardShotAbility.INSTANCE, false);
            if (CommanderAntiAir.mayShoot(cmdr))
                problems.add("switching the card entry off should stop the shot");
            com.solegendary.reignofnether.ability.AutocastState.set(u,
                com.solegendary.reignofnether.ability.abilities.SkywardShotAbility.INSTANCE, true);
            com.solegendary.reignofnether.unit.EngagementStance.set(cmdr, com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE);
            if (CommanderAntiAir.mayShoot(cmdr))
                problems.add("Hold Fire should stop the shot");
            var hand = CommanderAntiAir.hand(cmdr);
            double side = Math.hypot(hand.x - cmdr.getX(), hand.z - cmdr.getZ());
            if (side < 0.2 || side > 0.8 || hand.y < cmdr.getY() + cmdr.getBbHeight() * 0.7)
                problems.add("the shot should leave from the raised off hand (" + String.format("%.2f", side) + " to the side, "
                    + String.format("%.2f", hand.y - cmdr.getY()) + " up)");
            java.util.Set<String> names = new java.util.HashSet<>();
            int live = 0;
            for (var rl : com.solegendary.reignofnether.faction.Factions.PLAYABLE_FACTIONS) {
                var f = com.solegendary.reignofnether.faction.Factions.getFaction(rl);
                if (com.solegendary.reignofnether.faction.Factions.isLive(f)) {
                    live++;
                    names.add(CommanderAntiAir.styleOf(f).name());
                }
            }
            if (names.size() < live)
                problems.add("each faction's commander should shoot air its own way (" + names.size() + " styles: " + names + ")");
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            for (Mob m : spawned)
                m.discard();
        }
    }
}
