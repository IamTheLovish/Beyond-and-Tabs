package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.abilities.PlantThicket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.barfx.LightningFx;
import com.solegendary.reignofnether.brass.MachineRepair;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.hero.HeroKit.AbilitySpec;
import com.solegendary.reignofnether.hero.HeroKit.Ai;
import com.solegendary.reignofnether.hero.HeroKit.Cast;
import com.solegendary.reignofnether.hero.HeroKit.Role;
import com.solegendary.reignofnether.hero.HeroKit.Target;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.tide.TidepoolServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.WindcallerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The seven faction heroes' kits (design/hero_mode.md section 5): body stats, cost and four abilities each, and the
 * abilities' effects. Each hero has a movement ability of its own kind - the Paladin charges, the Gravecaller blinks
 * through the grave, the Warchief leaps, the Druid walks the roots, the Corsair grapples, the Storm Witch flies on her
 * cloud, the Arch-Engineer rocket-jumps - and its fourth ability is team utility (a shield wall, vision, war drums, a
 * hiding thicket, a tidepool, an energy refund, field repairs) rather than more damage: what a commander can't do from
 * above, so a hero stays worth having after tier 2.5.
 * <p>
 * Balance (design/hero_mode.md section 6): at level 1 a hero has ~2x a T2 unit's health and a T1 unit's damage; with
 * its abilities a level-5 hero trades with two or three T2 units and never with a whole army. Every stun or root is at
 * most 1.5 s (HeroFx caps them), nothing is map-wide or permanent. Costs are BAR metal/energy (no config entry: the
 * hero's price is part of its kit), the revive is Reign of Nether's level-scaled one.
 */
public final class HeroKits {
    private HeroKits() { }

    static final ResourceCost HERO_COST = ResourceCost.Unit(0, 300, 300, 45, 4);   // 300 metal, 300 energy, 45 s, 4 pop

    static int[] cd(int r1, int r2, int r3) { return new int[] { r1, r2, r3 }; }
    static float[] v(float r1, float r2, float r3) { return new float[] { r1, r2, r3 }; }
    static float[] v(float all) { return new float[] { all, all, all }; }

    // ------------------------------------------------------------------------------------------------ Sunforged
    public static final HeroKit DAWN_PALADIN = new HeroKit("dawn_paladin", "Dawn Paladin", "villagers", Role.VANGUARD,
        200, 9, 4, 0.28f, 100, 8, 0.8f, HERO_COST,   // 160/7 -> 200/9 (balance-pass: a level-5 hero lost to two Iron Golems; design/balance.md "Heroes")
        new AbilitySpec("shield_charge", "hero_shield_charge", Target.GROUND, Ai.ENGAGE, cd(14, 12, 10), v(8, 10, 12), 2.5f,
            30, 2, v(6, 9, 12), v(0.5f, 0.75f, 1.0f), HeroKits::shieldCharge),
        new AbilitySpec("judgement", "hero_judgement", Target.ENEMY, Ai.STRIKE, cd(12, 10, 8), v(10), 0,
            35, 6, v(10, 15, 20), v(50), HeroKits::judgement),
        new AbilitySpec("sun_banner", "hero_sun_banner", Target.SELF, Ai.FIGHT_SELF, cd(30, 26, 22), v(0), 8,
            50, 0, v(6, 8, 10), v(0, 0, 10), HeroKits::sunBanner),
        new AbilitySpec("aegis_of_dawn", "hero_aegis_of_dawn", Target.SELF, Ai.FIGHT_SELF, cd(75, 65, 55), v(0), 14,
            80, 0, v(8, 12, 16), v(12), HeroKits::aegisOfDawn));

    // ------------------------------------------------------------------------------------------------ Gravebound
    public static final HeroKit GRAVECALLER = new HeroKit("gravecaller", "Gravecaller", "monsters", Role.CASTER,
        165, 8, 0, 0.26f, 140, 12, 1.2f, HERO_COST,   // 120/6 -> 165/8 (balance-pass)
        new AbilitySpec("bone_spear", "hero_bone_spear", Target.GROUND, Ai.STRIKE, cd(8, 7, 6), v(14), 0,
            30, 6, v(8, 12, 16), v(1, 1, 1.3f), HeroKits::boneSpear),
        new AbilitySpec("grave_step", "hero_grave_step", Target.GROUND, Ai.NONE, cd(16, 13, 10), v(8, 11, 14), 3,
            25, 2, v(8, 11, 14), v(2, 2.5f, 3), HeroKits::graveStep),
        new AbilitySpec("raise_the_fallen", "hero_raise_the_fallen", Target.GROUND, Ai.FIGHT_GROUND, cd(40, 35, 30), v(10), 2,
            60, 10, v(2, 3, 4), v(20, 25, 30), HeroKits::raiseTheFallen),
        new AbilitySpec("grave_sight", "hero_grave_sight", Target.SELF, Ai.FIGHT_SELF, cd(60, 50, 40), v(0), 24,
            70, 0, v(24, 30, 36), v(8, 11, 14), HeroKits::graveSight));

    // ------------------------------------------------------------------------------------------------ Horde
    public static final HeroKit HORDE_WARCHIEF = new HeroKit("horde_warchief", "Warchief", "piglins", Role.RAIDER,
        180, 9.5f, 2, 0.30f, 100, 8, 0.8f, HERO_COST,   // 150/8 -> 180/9.5 (balance-pass; at the hero test's 2.5-Tesla-Walker cap)
        new AbilitySpec("savage_leap", "hero_savage_leap", Target.GROUND, Ai.ENGAGE, cd(14, 12, 10), v(10, 12, 14), 3,
            30, 2, v(6, 9, 12), v(2, 2, 3), HeroKits::savageLeap),
        new AbilitySpec("frenzy", "hero_frenzy", Target.SELF, Ai.FIGHT_SELF, cd(24, 20, 16), v(0), 0,
            40, 0, v(40, 50, 60), v(6, 7, 8), HeroKits::frenzy),
        new AbilitySpec("cleave", "hero_cleave", Target.SELF, Ai.FIGHT_SELF, cd(9, 8, 7), v(0), 4,
            25, 0, v(8, 12, 16), v(4, 4, 4.5f), HeroKits::cleave),
        new AbilitySpec("war_drums", "hero_war_drums", Target.SELF, Ai.FIGHT_SELF, cd(70, 60, 50), v(0), 14,
            80, 0, v(8, 10, 12), v(14, 14, 16), HeroKits::warDrums));

    // ------------------------------------------------------------------------------------------------ Verdant Court
    public static final HeroKit THORNHEART_DRUID = new HeroKit("thornheart_druid", "Thornheart Druid", "verdant_court", Role.SUPPORT,
        165, 7, 0, 0.28f, 140, 12, 1.4f, HERO_COST,   // 120/5 -> 165/7 (balance-pass)
        new AbilitySpec("thorn_volley", "hero_thorn_volley", Target.GROUND, Ai.STRIKE, cd(10, 9, 8), v(12), 3,
            30, 6, v(5, 8, 11), v(1, 1.25f, 1.5f), HeroKits::thornVolley),
        new AbilitySpec("root_walk", "hero_root_walk", Target.GROUND, Ai.NONE, cd(18, 15, 12), v(10, 14, 18), 3,
            30, 4, v(10, 14, 18), v(1), HeroKits::rootWalk),
        new AbilitySpec("rejuvenate", "hero_rejuvenate", Target.GROUND, Ai.FIGHT_GROUND, cd(20, 17, 14), v(10), 6,
            50, 6, v(8, 12, 16), v(5), HeroKits::rejuvenate),
        new AbilitySpec("grove_seed", "hero_grove_seed", Target.GROUND, Ai.FIGHT_GROUND, cd(60, 50, 40), v(10), 3,
            60, 10, v(1, 3, 5), v(3), HeroKits::groveSeed));

    // ------------------------------------------------------------------------------------------------ Tidewrought
    public static final HeroKit CORSAIR_CAPTAIN = new HeroKit("corsair_captain", "Corsair Captain", "tidewrought", Role.RAIDER,
        185, 9, 1, 0.31f, 100, 8, 0.9f, HERO_COST,   // 140/7 -> 185/9 (balance-pass)
        new AbilitySpec("pistol_shot", "hero_pistol_shot", Target.ENEMY, Ai.STRIKE, cd(7, 6, 5), v(12), 0,
            20, 6, v(10, 14, 18), v(0.3f, 0.4f, 0.5f), HeroKits::pistolShot),
        new AbilitySpec("grapple_hook", "hero_grapple_hook", Target.GROUND, Ai.ENGAGE, cd(14, 11, 8), v(10, 13, 16), 0,
            25, 2, v(10, 13, 16), v(0, 1, 1.5f), HeroKits::grappleHook),
        new AbilitySpec("riptide", "hero_riptide", Target.SELF, Ai.FIGHT_SELF, cd(16, 14, 12), v(0), 4,
            40, 0, v(6, 9, 12), v(4, 5, 5), HeroKits::riptide),
        new AbilitySpec("tidepool", "hero_tidepool", Target.GROUND, Ai.FIGHT_GROUND, cd(60, 50, 40), v(10), 2,
            60, 8, v(30, 40, 60), v(2, 2, 3), HeroKits::tidepool));

    // ------------------------------------------------------------------------------------------------ Stormcallers
    public static final HeroKit STORM_WITCH = new HeroKit("storm_witch", "Storm Witch", "stormcallers", Role.CASTER,
        130, 7, 0, 0.27f, 160, 14, 1.6f, HERO_COST,   // 110/6 -> 130/7 (balance-pass)
        new AbilitySpec("chain_lightning", "hero_chain_lightning", Target.ENEMY, Ai.STRIKE, cd(9, 8, 7), v(12), 5,
            35, 6, v(10, 14, 18), v(2, 3, 4), HeroKits::chainLightning),
        new AbilitySpec("cloud_ride", "hero_cloud_ride", Target.SELF, Ai.NONE, cd(8, 6, 4), v(0), 0,
            0, 0, v(8, 6, 4), v(0), HeroKits::cloudRide),
        new AbilitySpec("frost_nova", "hero_frost_nova", Target.SELF, Ai.FIGHT_SELF, cd(18, 15, 12), v(0), 4,
            45, 0, v(5, 8, 11), v(4, 5, 6), HeroKits::frostNova),
        new AbilitySpec("mana_font", "hero_mana_font", Target.SELF, Ai.FIGHT_SELF, cd(70, 60, 50), v(0), 12,
            40, 0, v(100, 175, 250), v(4, 6, 8), HeroKits::manaFont));

    // ------------------------------------------------------------------------------------------------ Brassworks
    public static final HeroKit ARCH_ENGINEER = new HeroKit("arch_engineer", "Arch-Engineer", "brassworks", Role.SKIRMISHER,
        130, 6, 2, 0.27f, 110, 10, 1.0f, HERO_COST,
        new AbilitySpec("rivet_burst", "hero_rivet_burst", Target.ENEMY, Ai.STRIKE, cd(8, 7, 6), v(10), 2,
            25, 6, v(3, 4.5f, 6), v(2, 2, 2.5f), HeroKits::rivetBurst),
        new AbilitySpec("rocket_jump", "hero_rocket_jump", Target.GROUND, Ai.NONE, cd(16, 13, 10), v(8, 11, 14), 2.5f,
            25, 2, v(3, 5, 7), v(8, 11, 14), HeroKits::rocketJump),
        new AbilitySpec("deploy_turret", "hero_deploy_turret", Target.GROUND, Ai.FIGHT_GROUND, cd(35, 30, 25), v(8), 0,
            50, 10, v(1, 1, 2), v(20), HeroKits::deployTurret),
        new AbilitySpec("field_repair", "hero_field_repair", Target.SELF, Ai.FIGHT_SELF, cd(50, 42, 35), v(0), 10,
            60, 0, v(30, 45, 60), v(10, 15, 20), HeroKits::fieldRepair));

    public static final List<HeroKit> ALL = List.of(DAWN_PALADIN, GRAVECALLER, HORDE_WARCHIEF, THORNHEART_DRUID,
        CORSAIR_CAPTAIN, STORM_WITCH, ARCH_ENGINEER);

    @Nullable
    public static HeroKit forFaction(String factionName) {
        for (HeroKit k : ALL)
            if (k.factionName.equals(factionName))
                return k;
        return null;
    }

    // ================================================================================================ effects

    static int ticks(float seconds) { return Math.round(seconds * 20); }

    // ---- Sunforged: Dawn Paladin ----

    /** Dash (low arc) to the spot; on arrival enemies round it are hit, stunned and shoved. */
    static boolean shieldCharge(Cast c) {
        Vec3 to = HeroFx.landingSpot(c.level, c.hero, c.at, c.range());
        double dist = to.distanceTo(c.hero.position());
        if (dist < 1.5)
            return false;
        float dmg = c.a();
        int stun = ticks(c.b());
        HeroFx.trail(c.level, ParticleTypes.CLOUD, c.hero.position().add(0, 0.3, 0), to.add(0, 0.3, 0));
        HeroFx.sound(c.level, c.hero.position(), SoundEvents.SHIELD_BLOCK, 1.2f, 0.7f);
        c.fh.heroCore().startMotion(to, (int) (dist * 0.8) + 3, 0.6, m -> {
            for (LivingEntity e : HeroFx.enemies(c.level, m, m.position(), c.spec.radius)) {
                HeroFx.damage(m, e, dmg, false);
                HeroFx.stun(e, stun);
                HeroFx.knockback(m.position(), e, 0.8);
            }
            HeroFx.particles(c.level, ParticleTypes.CRIT, m.position().add(0, 0.5, 0), 20, 1.0, 0.2);
            HeroFx.sound(c.level, m.position(), SoundEvents.ANVIL_LAND, 0.8f, 1.3f);
        });
        return true;
    }

    /** A pillar of sunlight on one enemy: magic damage, half again on the undead. */
    static boolean judgement(Cast c) {
        LivingEntity t = c.target;
        if (t == null || !t.isAlive() || !HeroFx.isEnemy(c.hero, t))
            return false;
        float dmg = c.a() * (HeroFx.isUndead(t) ? 1 + c.b() / 100f : 1);
        HeroFx.damage(c.hero, t, dmg, true);
        // Oct 22 (vfx foundation): the pillar of sunlight is drawn, not sketched in vanilla particles
        // (assets/reignofnether/vfx/judgement_pillar.json: sigil, shaft, sparks, a flash of light on the ground)
        com.solegendary.reignofnether.barfx.vfx.Vfx.at(c.level, "judgement_pillar", t.position());
        HeroFx.sound(c.level, t.position(), SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.6f);
        return true;
    }

    /** The banner raised: Resistance to every friend in the radius (and Regeneration at rank 3). */
    static boolean sunBanner(Cast c) {
        for (LivingEntity e : HeroFx.friends(c.level, c.hero, c.hero.position(), c.spec.radius)) {
            HeroFx.buff(e, MobEffects.DAMAGE_RESISTANCE, ticks(c.a()), 0);
            if (c.b() > 0)
                HeroFx.buff(e, MobEffects.REGENERATION, ticks(c.b()), 0);
        }
        HeroFx.ring(c.level, ParticleTypes.WAX_ON, c.hero.position(), c.spec.radius);
        HeroFx.sound(c.level, c.hero.position(), SoundEvents.ARMOR_EQUIP_GOLD, 1.2f, 0.9f);
        return true;
    }

    /** Utility: a shield of golden absorption on every friend in a wide radius. */
    static boolean aegisOfDawn(Cast c) {
        int amp = Math.max(0, Math.round(c.a() / 4f) - 1);   // absorption: 4 HP per level
        for (LivingEntity e : HeroFx.friends(c.level, c.hero, c.hero.position(), c.spec.radius))
            HeroFx.buff(e, MobEffects.ABSORPTION, ticks(c.b()), amp);
        // Oct 22 (vfx foundation showcase): the ultimate's cast circle and sky pillar ride on the paladin
        // (assets/reignofnether/vfx/sun_pillar.json - tiled sigil, climbing rune rings, a shaft of sunlight with a
        // double helix, embers, dust and the light it throws on the ground and the units round him). Scale 1: the
        // circle marks the paladin, not the 14-block aegis radius (a sigil that wide would hide the units it shields)
        com.solegendary.reignofnether.barfx.vfx.Vfx.on(c.level, "sun_pillar", c.hero, 1.0f);
        HeroFx.sound(c.level, c.hero.position(), SoundEvents.BEACON_ACTIVATE, 1.2f, 1.2f);
        return true;
    }

    // ---- Gravebound: Gravecaller ----

    /** A spear of bone along the aim to full range: every enemy on the line is hit. */
    static boolean boneSpear(Cast c) {
        Vec3 from = c.hero.position().add(0, 1.0, 0);
        Vec3 dir = new Vec3(c.at.x - from.x, 0, c.at.z - from.z);
        if (dir.lengthSqr() < 1e-4)
            dir = Vec3.directionFromRotation(0, c.hero.getYRot());
        Vec3 to = from.add(dir.normalize().scale(c.range()));
        for (LivingEntity e : HeroFx.enemiesOnLine(c.level, c.hero, from, to, c.b()))
            HeroFx.damage(c.hero, e, c.a(), true);
        HeroFx.trail(c.level, ParticleTypes.WHITE_ASH, from, to);
        HeroFx.trail(c.level, ParticleTypes.SOUL, from, to);
        HeroFx.sound(c.level, from, SoundEvents.SKELETON_SHOOT, 1.2f, 0.6f);
        return true;
    }

    /** Sinks into the grave and rises at the spot; the chill left behind slows enemies there. */
    static boolean graveStep(Cast c) {
        Vec3 from = c.hero.position();
        Vec3 to = HeroFx.landingSpot(c.level, c.hero, c.at, c.range());
        if (to.distanceToSqr(from) < 2)
            return false;
        for (LivingEntity e : HeroFx.enemies(c.level, c.hero, from, c.spec.radius))
            HeroFx.slow(e, ticks(c.b()), 1);
        HeroFx.particles(c.level, ParticleTypes.SOUL, from.add(0, 1, 0), 24, 0.5, 0.05);
        c.hero.getNavigation().stop();
        c.hero.teleportTo(to.x, to.y, to.z);
        HeroFx.particles(c.level, ParticleTypes.SCULK_SOUL, to.add(0, 1, 0), 16, 0.5, 0.05);
        HeroFx.sound(c.level, to, SoundEvents.SOUL_ESCAPE, 1.4f, 0.8f);
        return true;
    }

    /** Skeletons claw out of the ground at the spot and fight for the hero's owner until they crumble. */
    static boolean raiseTheFallen(Cast c) {
        int n = Math.round(c.a());
        long end = c.level.getGameTime() + ticks(c.b());
        int raised = 0;
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            BlockPos at = BlockPos.containing(c.at.x + Math.cos(ang) * 1.5, c.at.y, c.at.z + Math.sin(ang) * 1.5);
            Entity e = UnitServerEvents.spawnMob(EntityRegistrar.SKELETON_UNIT.get(), c.level, at, c.owner());
            if (e != null) {
                c.fh.heroCore().addSummon(e, end);
                HeroFx.particles(c.level, ParticleTypes.SOUL, e.position().add(0, 0.5, 0), 10, 0.3, 0.02);
                raised++;
            }
        }
        HeroFx.sound(c.level, c.at, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 1.0f, 0.6f);
        return raised > 0;
    }

    /** Utility: every enemy unit in a wide radius is revealed to the team through the fog and outlined. */
    static boolean graveSight(Cast c) {
        int t = ticks(c.b());
        String owner = c.owner();
        List<String> team = new ArrayList<>();
        team.add(owner);
        for (RTSPlayer p : PlayerServerEvents.rtsPlayers)
            if (!p.name.equals(owner) && AlliancesServerEvents.isAllied(owner, p.name))
                team.add(p.name);
        for (LivingEntity e : HeroFx.enemies(c.level, c.hero, c.hero.position(), c.a())) {
            for (String name : team)
                FogOfWarServerEvents.revealRangedUnit(e.getId(), name, t);
            HeroFx.buff(e, MobEffects.GLOWING, t, 0);
        }
        HeroFx.ring(c.level, ParticleTypes.SCULK_SOUL, c.hero.position(), 4);
        HeroFx.sound(c.level, c.hero.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 1.4f);
        return true;
    }

    // ---- Horde: Warchief ----

    /** A high leap onto the spot: the landing hits and slows every enemy round it. */
    static boolean savageLeap(Cast c) {
        Vec3 to = HeroFx.landingSpot(c.level, c.hero, c.at, c.range());
        double dist = to.distanceTo(c.hero.position());
        if (dist < 2)
            return false;
        float dmg = c.a();
        int slow = ticks(c.b());
        HeroFx.sound(c.level, c.hero.position(), SoundEvents.PIGLIN_BRUTE_ANGRY, 1.2f, 0.8f);
        c.fh.heroCore().startMotion(to, (int) (dist * 0.6) + 8, 3.5, m -> {
            for (LivingEntity e : HeroFx.enemies(c.level, m, m.position(), c.spec.radius)) {
                HeroFx.damage(m, e, dmg, false);
                HeroFx.slow(e, slow, 1);
            }
            HeroFx.particles(c.level, ParticleTypes.EXPLOSION, m.position(), 2, 0.5, 0);
            HeroFx.ring(c.level, ParticleTypes.LAVA, m.position(), c.spec.radius);
            HeroFx.sound(c.level, m.position(), SoundEvents.GENERIC_EXPLODE, 0.7f, 1.3f);
        });
        return true;
    }

    /** Attacks a lot faster and runs faster for a while - and takes a fifth more damage. */
    static boolean frenzy(Cast c) {
        int t = ticks(c.b());
        c.fh.heroCore().startFrenzy(t, c.a() / 100f, 1.2f);
        HeroFx.buff(c.hero, MobEffects.MOVEMENT_SPEED, t, 0);
        HeroFx.particles(c.level, ParticleTypes.ANGRY_VILLAGER, c.hero.position().add(0, 2, 0), 6, 0.4, 0);
        HeroFx.sound(c.level, c.hero.position(), SoundEvents.RAVAGER_ROAR, 1.0f, 1.2f);
        return true;
    }

    /** A wide swing: every enemy in the front arc within reach is hit and shoved back. */
    static boolean cleave(Cast c) {
        Vec3 pos = c.hero.position();
        Vec3 look = Vec3.directionFromRotation(0, c.hero.getYRot());
        double reach = c.b();
        for (LivingEntity e : HeroFx.enemies(c.level, c.hero, pos, reach)) {
            Vec3 d = e.position().subtract(pos);
            Vec3 flat = new Vec3(d.x, 0, d.z);
            // the front 120 degrees (cos 60 = 0.5); point blank always counts
            if (flat.lengthSqr() > 1 && flat.normalize().dot(look) < 0.5)
                continue;
            HeroFx.damage(c.hero, e, c.a(), false);
            HeroFx.knockback(pos, e, 0.6);
        }
        HeroFx.particles(c.level, ParticleTypes.SWEEP_ATTACK, pos.add(look.scale(1.5)).add(0, 1, 0), 3, 0.6, 0);
        HeroFx.sound(c.level, pos, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        return true;
    }

    /** Utility: drums that drive the warband on - Strength and Speed for every friend in the radius. */
    static boolean warDrums(Cast c) {
        int t = ticks(c.a());
        for (LivingEntity e : HeroFx.friends(c.level, c.hero, c.hero.position(), c.b())) {
            HeroFx.buff(e, MobEffects.DAMAGE_BOOST, t, 0);
            HeroFx.buff(e, MobEffects.MOVEMENT_SPEED, t, 0);
        }
        HeroFx.ring(c.level, ParticleTypes.FLAME, c.hero.position(), c.b());
        HeroFx.sound(c.level, c.hero.position(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1.5f, 0.6f);
        return true;
    }

    // ---- Verdant Court: Thornheart Druid ----

    /** A volley of thorns bursts over the spot: damage and a short root to every enemy there. */
    static boolean thornVolley(Cast c) {
        Vec3 at = c.at;
        for (LivingEntity e : HeroFx.enemies(c.level, c.hero, at, c.spec.radius)) {
            HeroFx.damage(c.hero, e, c.a(), false);
            HeroFx.root(e, ticks(c.b()));
        }
        HeroFx.trail(c.level, ParticleTypes.COMPOSTER, c.hero.position().add(0, 1.5, 0), at.add(0, 0.5, 0));
        HeroFx.ring(c.level, ParticleTypes.HAPPY_VILLAGER, at, c.spec.radius);
        HeroFx.sound(c.level, at, SoundEvents.SWEET_BERRY_BUSH_BREAK, 1.4f, 0.8f);
        return true;
    }

    /** Sinks into the roots and walks up out of the ground at the spot; enemies there are rooted for a moment. */
    static boolean rootWalk(Cast c) {
        Vec3 from = c.hero.position();
        Vec3 to = HeroFx.landingSpot(c.level, c.hero, c.at, c.range());
        if (to.distanceToSqr(from) < 2)
            return false;
        HeroFx.particles(c.level, ParticleTypes.SPORE_BLOSSOM_AIR, from.add(0, 1, 0), 20, 0.5, 0.02);
        c.hero.getNavigation().stop();
        c.hero.teleportTo(to.x, to.y, to.z);
        for (LivingEntity e : HeroFx.enemies(c.level, c.hero, to, c.spec.radius))
            HeroFx.root(e, ticks(c.b()));
        HeroFx.ring(c.level, ParticleTypes.COMPOSTER, to, c.spec.radius);
        HeroFx.sound(c.level, to, SoundEvents.ROOTED_DIRT_BREAK, 1.5f, 0.7f);
        return true;
    }

    /** Heals every friend round the spot at once and keeps mending them for a few seconds. */
    static boolean rejuvenate(Cast c) {
        List<LivingEntity> friends = HeroFx.friends(c.level, c.hero, c.at, c.spec.radius);
        for (LivingEntity e : friends) {
            e.heal(c.a());
            HeroFx.buff(e, MobEffects.REGENERATION, ticks(c.b()), 0);
        }
        HeroFx.ring(c.level, ParticleTypes.HEART, c.at, c.spec.radius);
        HeroFx.sound(c.level, c.at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 1.2f);
        return !friends.isEmpty();
    }

    /** Utility: grows thicket patches (Verdant Living Terrain) that hide the hero's side's units inside them. */
    static boolean groveSeed(Cast c) {
        int patches = Math.round(c.a());
        BlockPos centre = BlockPos.containing(c.at);
        int[][] offsets = { { 0, 0 }, { 3, 0 }, { -3, 0 }, { 0, 3 }, { 0, -3 } };
        int planted = 0;
        for (int i = 0; i < Math.min(patches, offsets.length); i++)
            planted += PlantThicket.plantPatch(c.level, c.owner(), centre.offset(offsets[i][0], 0, offsets[i][1]), false);
        HeroFx.particles(c.level, ParticleTypes.HAPPY_VILLAGER, c.at, 20, 1.5, 0);
        return planted > 0;
    }

    // ---- Tidewrought: Corsair Captain ----

    /** A flintlock shot: heavy damage to one enemy in range and a shove. */
    static boolean pistolShot(Cast c) {
        LivingEntity t = c.target;
        if (t == null || !t.isAlive() || !HeroFx.isEnemy(c.hero, t))
            return false;
        Vec3 muzzle = c.hero.position().add(0, 1.4, 0);
        HeroFx.damage(c.hero, t, c.a(), false);
        HeroFx.knockback(c.hero.position(), t, c.b());
        HeroFx.trail(c.level, ParticleTypes.SMOKE, muzzle, t.position().add(0, 1, 0));
        HeroFx.particles(c.level, ParticleTypes.LARGE_SMOKE, muzzle, 4, 0.1, 0.01);
        HeroFx.sound(c.level, muzzle, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.8f);
        return true;
    }

    /** A hook on a line to the spot and a fast, flat swing across; at rank 2+ the landing slows enemies round it. */
    static boolean grappleHook(Cast c) {
        Vec3 from = c.hero.position();
        Vec3 to = HeroFx.landingSpot(c.level, c.hero, c.at, c.range());
        double dist = to.distanceTo(from);
        if (dist < 2)
            return false;
        int slow = ticks(c.b());
        HeroFx.trail(c.level, ParticleTypes.CRIT, from.add(0, 1.2, 0), to.add(0, 0.5, 0));
        HeroFx.sound(c.level, from, SoundEvents.FISHING_BOBBER_THROW, 1.5f, 0.8f);
        c.fh.heroCore().startMotion(to, (int) (dist * 0.5) + 4, 1.2, m -> {
            if (slow > 0)
                for (LivingEntity e : HeroFx.enemies(c.level, m, m.position(), 2.5))
                    HeroFx.slow(e, slow, 1);
            HeroFx.particles(c.level, ParticleTypes.SPLASH, m.position(), 16, 0.6, 0.1);
        });
        return true;
    }

    /** A wave bursts out round the captain: damage, a hard shove and a slow to every enemy it reaches. */
    static boolean riptide(Cast c) {
        Vec3 pos = c.hero.position();
        for (LivingEntity e : HeroFx.enemies(c.level, c.hero, pos, c.b())) {
            HeroFx.damage(c.hero, e, c.a(), false);
            HeroFx.knockback(pos, e, 1.4);
            HeroFx.slow(e, 40, 1);
            e.clearFire();
        }
        HeroFx.ring(c.level, ParticleTypes.SPLASH, pos, c.b());
        HeroFx.ring(c.level, ParticleTypes.BUBBLE_POP, pos, c.b() * 0.6);
        HeroFx.sound(c.level, pos, SoundEvents.GENERIC_SPLASH, 1.5f, 0.7f);
        return true;
    }

    /** Utility: raises a tidepool at the spot (the Tidewrought's Tides bonus for every friend standing in it). */
    static boolean tidepool(Cast c) {
        var pool = TidepoolServerEvents.raise(c.level, c.owner(), BlockPos.containing(c.at), Math.round(c.b()), ticks(c.a()));
        if (pool != null)
            HeroFx.sound(c.level, c.at, SoundEvents.BUCKET_EMPTY, 1.5f, 0.8f);
        return pool != null;
    }

    // ---- Stormcallers: Storm Witch ----

    /** Lightning on one enemy that jumps to the next ones near it, weaker each jump; never a friend, never twice. */
    static boolean chainLightning(Cast c) {
        LivingEntity t = c.target;
        if (t == null || !t.isAlive() || !HeroFx.isEnemy(c.hero, t))
            return false;
        Set<Integer> hit = new HashSet<>();
        Vec3 from = c.hero.position().add(0, 1.5, 0);
        float dmg = c.a();
        int jumps = Math.round(c.b());
        LivingEntity cur = t;
        for (int i = 0; i <= jumps && cur != null; i++) {
            Vec3 to = cur.position().add(0, cur.getBbHeight() / 2, 0);
            LightningFx.bolt(c.level, from.x, from.y, from.z, to.x, to.y, to.z);
            HeroFx.damage(c.hero, cur, dmg, true);
            hit.add(cur.getId());
            from = to;
            dmg *= 0.75f;
            LivingEntity next = null;
            double best = c.spec.radius * c.spec.radius;
            for (LivingEntity e : HeroFx.enemies(c.level, c.hero, to, c.spec.radius)) {
                double d = e.position().distanceToSqr(cur.position());
                if (!hit.contains(e.getId()) && d <= best) {
                    best = d;
                    next = e;
                }
            }
            cur = next;
        }
        HeroFx.sound(c.level, t.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.4f);
        return true;
    }

    /** Takes to the air on her storm cloud, or lands again (the Windcaller's flight: a flyer to every anti-air). */
    static boolean cloudRide(Cast c) {
        if (!(c.hero instanceof WindcallerUnit w))
            return false;
        w.toggleFlying();
        return true;
    }

    /** A ring of frost bursts out round her: damage and a heavy slow to every enemy in the radius. */
    static boolean frostNova(Cast c) {
        Vec3 pos = c.hero.position();
        for (LivingEntity e : HeroFx.enemies(c.level, c.hero, pos, c.b())) {
            HeroFx.damage(c.hero, e, c.a(), true);
            HeroFx.slow(e, 60, 2);
        }
        HeroFx.ring(c.level, ParticleTypes.SNOWFLAKE, pos, c.b());
        HeroFx.ring(c.level, ParticleTypes.SNOWFLAKE, pos, c.b() * 0.5);
        HeroFx.sound(c.level, pos, SoundEvents.GLASS_BREAK, 1.2f, 0.6f);
        return true;
    }

    /**
     * Utility: the ley lines answer - her owner gets energy back and every friend nearby has its ability cooldowns cut
     * (the Stormcallers live on their spells). Her own cooldowns are not touched.
     */
    static boolean manaFont(Cast c) {
        String owner = c.owner();
        if (owner != null && !owner.isBlank())
            ResourcesServerEvents.addSubtractResources(new Resources(owner, 0, Math.round(c.a()), 0));
        float cut = c.b() * 20;
        for (LivingEntity e : HeroFx.friends(c.level, c.hero, c.hero.position(), c.spec.radius)) {
            if (e == c.hero || !(e instanceof Unit u) || u.getAbilities() == null)
                continue;
            for (Ability a : u.getAbilities().get()) {
                if (a == null || a.action == null)
                    continue;
                float cd = u.getCooldown(a);
                if (cd > 0)
                    AbilityClientboundPacket.sendSetCooldownPacket(e.getId(), a.action, Math.max(0, cd - cut));
            }
            HeroFx.particles(c.level, ParticleTypes.ENCHANT, e.position().add(0, 1.5, 0), 8, 0.3, 0.5);
        }
        HeroFx.ring(c.level, ParticleTypes.ENCHANT, c.hero.position(), c.spec.radius);
        HeroFx.sound(c.level, c.hero.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.4f, 1.0f);
        return true;
    }

    // ---- Brassworks: Arch-Engineer ----

    /** Three rivets into one enemy; the ricochets hit the enemies right round it for half. */
    static boolean rivetBurst(Cast c) {
        LivingEntity t = c.target;
        if (t == null || !t.isAlive() || !HeroFx.isEnemy(c.hero, t))
            return false;
        float total = c.a() * 3;
        HeroFx.damage(c.hero, t, total, false);
        for (LivingEntity e : HeroFx.enemies(c.level, c.hero, t.position(), c.b()))
            if (e != t)
                HeroFx.damage(c.hero, e, total / 2, false);
        HeroFx.trail(c.level, ParticleTypes.CRIT, c.hero.position().add(0, 1.4, 0), t.position().add(0, 1, 0));
        HeroFx.particles(c.level, ParticleTypes.SMOKE, t.position().add(0, 1, 0), 8, 0.4, 0.02);
        HeroFx.sound(c.level, c.hero.position(), SoundEvents.CROSSBOW_SHOOT, 1.5f, 1.6f);
        return true;
    }

    /** Rocket boots: a tall jump to the spot in a plume of smoke; the landing jolts enemies round it. */
    static boolean rocketJump(Cast c) {
        Vec3 to = HeroFx.landingSpot(c.level, c.hero, c.at, c.range());
        double dist = to.distanceTo(c.hero.position());
        if (dist < 2)
            return false;
        float dmg = c.a();
        HeroFx.particles(c.level, ParticleTypes.LARGE_SMOKE, c.hero.position(), 16, 0.4, 0.05);
        HeroFx.particles(c.level, ParticleTypes.FLAME, c.hero.position(), 10, 0.3, 0.05);
        HeroFx.sound(c.level, c.hero.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.5f, 0.7f);
        c.fh.heroCore().startMotion(to, (int) (dist * 0.6) + 8, 5.0, m -> {
            for (LivingEntity e : HeroFx.enemies(c.level, m, m.position(), c.spec.radius)) {
                HeroFx.damage(m, e, dmg, false);
                HeroFx.knockback(m.position(), e, 0.8);
            }
            HeroFx.particles(c.level, ParticleTypes.CAMPFIRE_COSY_SMOKE, m.position(), 8, 0.6, 0.02);
            HeroFx.sound(c.level, m.position(), SoundEvents.ANVIL_LAND, 0.6f, 1.6f);
        });
        return true;
    }

    /** Sets down the Guildmaster's auto-turret at the spot (two at rank 3), free - the hero carries the parts. */
    static boolean deployTurret(Cast c) {
        int n = Math.round(c.a());
        int placed = 0;
        for (int i = 0; i < n; i++) {
            BlockPos at = BlockPos.containing(c.at.x + i * 2, c.at.y, c.at.z).below();
            Entity e = UnitServerEvents.spawnMob(EntityRegistrar.DEPLOYED_TURRET_UNIT.get(), c.level, at, c.owner());
            if (e != null) {
                e.setYRot(c.hero.getYRot());
                placed++;
            }
        }
        if (placed > 0) {
            HeroFx.particles(c.level, ParticleTypes.CLOUD, c.at.add(0, 0.5, 0), 10, 0.4, 0.05);
            HeroFx.sound(c.level, c.at, SoundEvents.ANVIL_PLACE, 1.2f, 1.2f);
        }
        return placed > 0;
    }

    /** Utility: patches up every friendly machine nearby (and the crews a little), plating the machines for a moment. */
    static boolean fieldRepair(Cast c) {
        for (LivingEntity e : HeroFx.friends(c.level, c.hero, c.hero.position(), c.spec.radius)) {
            if (MachineRepair.isMachine(e)) {
                e.heal(c.a());
                HeroFx.buff(e, MobEffects.DAMAGE_RESISTANCE, 100, 0);
                HeroFx.particles(c.level, ParticleTypes.ELECTRIC_SPARK, e.position().add(0, 1, 0), 10, 0.4, 0.1);
            } else {
                e.heal(c.b());
            }
        }
        HeroFx.ring(c.level, ParticleTypes.ELECTRIC_SPARK, c.hero.position(), c.spec.radius);
        HeroFx.sound(c.level, c.hero.position(), SoundEvents.ANVIL_USE, 1.0f, 1.4f);
        return true;
    }

    // ---- shared ----

    /** The faction hero kit an entity carries, or null. */
    @Nullable
    public static HeroKit kitOf(Entity e) {
        return e instanceof FactionHero fh ? fh.kit() : null;
    }

    /** Used by the bot and tests: ability {@code slot} of a hero. */
    public static FactionHeroAbility ability(FactionHero hero, int slot) {
        return hero.heroCore().abilities.get(slot);
    }

}
