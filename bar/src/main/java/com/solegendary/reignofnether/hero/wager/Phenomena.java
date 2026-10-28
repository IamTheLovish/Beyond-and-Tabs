package com.solegendary.reignofnether.hero.wager;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.barfx.LightningFx;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What a granted wager does: one phenomenon per intent and tier, plus the intent's twist ("granted, mostly"), stitched
 * from effects the game already has (weather, the hero helpers in HeroFx, vanilla and our status effects, unit summons,
 * fog reveals, the telegraph helper). Every number lives in a {@link Spec} so the fairness caps can be checked in one
 * place: no phenomenon deals more than {@link #MAX_DAMAGE} to one target in total, a damaging one reaches no further than
 * {@link #MAX_RADIUS} (a buff or a reveal up to {@link #MAX_AREA}), nothing lasts past {@link #MAX_TICKS}, and none
 * damages a building - they only ever touch units. Twists are never worse than a T1 effect against the owner.
 * <p>
 * The design's table (hero-abilities-overhaul, "Intents ... the phenomenon pool per intent") is followed where the game
 * has the piece; where it does not yet (a projectile-stopping dome, map-wide fog lift, the wyrm's own body, swelling
 * rivers), the nearest existing effect stands in and the name says what it is. Numbers for lovish to tune.
 */
public final class Phenomena {
    private Phenomena() { }

    public static final float MAX_DAMAGE = 50;
    public static final float MAX_RADIUS = 6;
    public static final float MAX_AREA = 16;
    public static final int MAX_TICKS = 90 * 20;

    /** A phenomenon's name (for the book's reply) and its numbers: damage per target in total, radius, duration. */
    public record Spec(String intent, int tier, boolean twist, String name, float damage, float radius, int ticks) { }

    /** What a phenomenon is cast with. */
    record Ctx(ServerLevel level, Mob hero, String owner, Spec spec, Vec3 aim, int quantity, java.util.Random rng) {
        float dmg(int hits) { return spec.damage() / Math.max(1, hits); }
        float r() { return spec.radius(); }
        int t() { return spec.ticks(); }
        long end() { return level.getGameTime() + spec.ticks(); }
    }

    private static final Map<String, Spec> SPECS = new LinkedHashMap<>();
    private static final Map<String, Consumer<Ctx>> EFFECTS = new LinkedHashMap<>();

    private static String key(String intent, int tier, boolean twist) {
        return intent + (twist ? ":twist" : ":" + tier);
    }

    private static void def(String intent, int tier, String name, float damage, float radius, int ticks, Consumer<Ctx> effect) {
        boolean twist = tier == 0;
        String k = key(intent, tier, twist);
        SPECS.put(k, new Spec(intent, tier, twist, name, damage, radius, ticks));
        EFFECTS.put(k, effect);
    }

    private static void twist(String intent, String name, float damage, float radius, int ticks, Consumer<Ctx> effect) {
        def(intent, 0, name, damage, radius, ticks, effect);
    }

    public static List<Spec> specs() {
        return Collections.unmodifiableList(new ArrayList<>(SPECS.values()));
    }

    /** The phenomenon for an intent and tier (1-4), or the intent's twist; null for an unknown intent. */
    @Nullable
    public static Spec spec(String intent, int tier, boolean twist) {
        return SPECS.get(key(intent, Math.max(1, Math.min(4, tier)), twist));
    }

    /** True for the phenomena that change the world's weather (global: a GameTest leaves them out of its smoke cast). */
    public static boolean changesWeather(Spec s) {
        return s.intent().equals("rain") && !s.twist() || s.intent().equals("sun") && s.tier() == 1 && !s.twist();
    }

    /** Runs the phenomenon and returns its name for the reply ("" for an unknown intent: nothing happens). */
    public static String cast(ServerLevel level, Mob hero, String intent, int tier, boolean twist, Vec3 aim, int quantity) {
        return cast(level, hero, intent, tier, twist, aim, quantity, new java.util.Random());
    }

    /** As above with the wager's own seeded Random, so a logged seed replays the phenomenon's rolls too. */
    public static String cast(ServerLevel level, Mob hero, String intent, int tier, boolean twist, Vec3 aim, int quantity,
                              java.util.Random rng) {
        if (WagerIntents.NONSENSE.equals(intent)) {
            pebble(level, hero);
            return "A Pebble";
        }
        String k = key(intent, Math.max(1, Math.min(4, tier)), twist);
        Spec spec = SPECS.get(k);
        Consumer<Ctx> effect = EFFECTS.get(k);
        if (spec == null || effect == null)
            return "";
        String owner = hero instanceof Unit u && u.getOwnerName() != null ? u.getOwnerName() : "";
        effect.accept(new Ctx(level, hero, owner, spec, aim, quantity, rng));
        return spec.name();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    static int ticks(float seconds) { return Math.round(seconds * 20); }

    static List<LivingEntity> friends(Ctx c, Vec3 at, double r) { return HeroFx.friends(c.level, c.hero, at, r); }
    static List<LivingEntity> enemies(Ctx c, Vec3 at, double r) { return HeroFx.enemies(c.level, c.hero, at, r); }

    /** Damage from a phenomenon: magic, credited to the hero, never above the cap in one hit. */
    static void hurt(Ctx c, LivingEntity t, float amount) {
        HeroFx.damage(c.hero, t, Math.min(amount, MAX_DAMAGE), true);
    }

    static void effect(List<? extends LivingEntity> who, MobEffect effect, int ticks, int amp) {
        for (LivingEntity e : who)
            e.addEffect(new MobEffectInstance(effect, Math.min(ticks, MAX_TICKS), amp, false, true));
    }

    static void puff(Ctx c, ParticleOptions p, Vec3 at, int n, double spread) {
        HeroFx.particles(c.level, p, at.add(0, 0.8, 0), n, spread, 0.05);
    }

    /** The most-hurt friend within {@code r} of the hero (the hero included), null if everyone is whole. */
    @Nullable
    static LivingEntity mostHurt(List<LivingEntity> list) {
        LivingEntity best = null;
        float missing = 0.01f;
        for (LivingEntity e : list) {
            float m = e.getMaxHealth() - e.getHealth();
            if (m > missing) {
                missing = m;
                best = e;
            }
        }
        return best;
    }

    /** The owner's own units that may be moved: an ally's army or a garrisoned tower crew is not his to move. */
    static List<LivingEntity> ownMovable(Ctx c, List<LivingEntity> list) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : list)
            if (e instanceof Unit u && c.owner.equals(u.getOwnerName()) && !u.isGarrisoned())
                out.add(e);
        return out;
    }

    /** The owner and every ally (a reveal is for the team). */
    static List<String> team(Ctx c) {
        List<String> team = new ArrayList<>();
        if (c.owner.isBlank())
            return team;
        team.add(c.owner);
        for (com.solegendary.reignofnether.player.RTSPlayer p : com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers)
            if (!p.name.equals(c.owner) && AlliancesServerEvents.isAllied(c.owner, p.name))
                team.add(p.name);
        return team;
    }

    static void reveal(Ctx c, List<LivingEntity> who, int ticks) {
        List<String> team = team(c);
        for (LivingEntity e : who) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.min(ticks, MAX_TICKS), 0, false, false));
            for (String name : team)
                FogOfWarServerEvents.revealRangedUnit(e.getId(), name, ticks);
        }
    }

    static void pay(String owner, int metal, int energy) {
        if (owner != null && !owner.isBlank() && (metal != 0 || energy != 0))
            ResourcesServerEvents.addSubtractResources(new Resources(owner, 0, energy, metal));
    }

    static void pebble(ServerLevel level, Mob hero) {
        if (hero instanceof Unit u)
            pay(u.getOwnerName(), 1, 0);
        HeroFx.particles(level, ParticleTypes.CRIT, hero.position().add(0, 1, 0), 6, 0.2, 0.05);
        HeroFx.sound(level, hero.position(), SoundEvents.CHICKEN_EGG, 1.0f, 1.4f);
    }

    /** {@code n} temporary units of {@code type} round {@code at}, owned by {@code owner} ("" = nobody's), for the spec's time. */
    static int summon(Ctx c, EntityType<? extends Mob> type, int n, Vec3 at, String owner) {
        int made = 0;
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / Math.max(1, n);
            BlockPos bp = BlockPos.containing(at.x + Math.cos(ang) * 1.5, at.y, at.z + Math.sin(ang) * 1.5);
            Entity e;
            if (type == EntityType.CHICKEN || type == EntityType.PIG) {
                e = type.create(c.level);
                if (e != null) {
                    e.moveTo(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5, 0, 0);
                    c.level.addFreshEntity(e);
                }
            } else {
                e = UnitServerEvents.spawnMob(type, c.level, bp, owner);
            }
            if (e != null) {
                HeroFx.addSummon(c.hero, e, c.end());
                HeroFx.particles(c.level, ParticleTypes.POOF, e.position().add(0, 0.5, 0), 8, 0.3, 0.02);
                made++;
            }
        }
        return made;
    }

    /** The top of the ground column at x/z (a wall or a mound stands on it). */
    static BlockPos ground(ServerLevel level, double x, double z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
        return new BlockPos((int) Math.floor(x), y, (int) Math.floor(z));
    }

    /** A wall {@code length} long and {@code height} high across the hero's line to the aim, centred on it. */
    static void wall(Ctx c, int length, int height, BlockState state) {
        Vec3 d = c.aim.subtract(c.hero.position());
        Vec3 across = new Vec3(-d.z, 0, d.x);
        across = across.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : across.normalize();
        long until = c.end();
        for (int i = 0; i < length; i++) {
            double off = i - (length - 1) / 2.0;
            BlockPos base = ground(c.level, c.aim.x + across.x * off, c.aim.z + across.z * off);
            for (int h = 0; h < height; h++)
                WagerBlocks.place(c.level, base.above(h), state, until);
        }
        puff(c, ParticleTypes.CLOUD, c.aim, 20, 1.5);
        HeroFx.sound(c.level, c.aim, SoundEvents.STONE_PLACE, 1.4f, 0.7f);
    }

    /** Rain or a clear sky for the spec's time, ended by WagerWeather (Mode A B1); never vanilla thunder. */
    static void weather(Ctx c, boolean rain) {
        WagerWeather.start(c.level, rain, c.t());
    }

    static void blinkTo(Mob m, Vec3 to) {
        m.getNavigation().stop();
        m.teleportTo(to.x, to.y, to.z);
    }

    // ------------------------------------------------------------------------------------------------ the table

    static {
        // ---- rain (weather is global and symmetric; the owner gets the small rule)
        def("rain", 1, "Drizzle", 0, 6, ticks(60), c -> {
            weather(c, true);
            for (LivingEntity e : friends(c, c.hero.position(), c.r()))
                e.clearFire();
        });
        def("rain", 2, "Rain", 0, 6, ticks(90), c -> {
            weather(c, true);
            effect(enemies(c, c.hero.position(), c.r()), MobEffects.MOVEMENT_SLOWDOWN, ticks(6), 0);
        });
        def("rain", 3, "Thunderstorm", 30, 6, ticks(60), c -> {
            weather(c, true);
            // five strikes, a second apart, each on one unit (either side) under the storm; 6 a strike, 30 at most
            HeroFx.pulse(c.hero, 20, 100, () -> {
                List<LivingEntity> under = new ArrayList<>(friends(c, c.aim, c.r()));
                under.addAll(enemies(c, c.aim, c.r()));
                under.remove(c.hero);
                if (under.isEmpty())
                    return;
                LivingEntity t = under.get(c.rng.nextInt(under.size()));
                Vec3 p = t.position();
                LightningFx.bolt(c.level, p.x, p.y + 12, p.z, p.x, p.y, p.z);
                hurt(c, t, c.dmg(5));
                HeroFx.sound(c.level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
            });
        });
        def("rain", 4, "Monsoon", 0, 6, ticks(45), c -> {
            weather(c, true);
            effect(friends(c, c.hero.position(), c.r()), MobEffects.REGENERATION, ticks(10), 0);
            effect(enemies(c, c.hero.position(), c.r()), MobEffects.MOVEMENT_SLOWDOWN, ticks(10), 0);
        });
        twist("rain", "A Cloud of His Own", 0, 2, ticks(60), c ->
            HeroFx.pulse(c.hero, 10, c.t(), () -> HeroFx.particles(c.level, ParticleTypes.FALLING_WATER,
                c.hero.position().add(0, 3, 0), 12, 1.0, 0)));

        // ---- sun
        def("sun", 1, "Clear Sky", 0, 6, ticks(60), c -> {
            weather(c, false);
            effect(friends(c, c.hero.position(), c.r()), MobEffects.MOVEMENT_SPEED, ticks(10), 0);
        });
        def("sun", 2, "Sunburn", 20, 6, ticks(20), c -> {
            List<LivingEntity> dead = new ArrayList<>();
            for (LivingEntity e : enemies(c, c.aim, c.r()))
                if (HeroFx.isUndead(e))
                    dead.add(e);
            HeroFx.pulse(c.hero, 20, c.t(), () -> {
                for (LivingEntity e : dead)
                    if (e.isAlive()) {
                        hurt(c, e, c.dmg(20));
                        puff(c, ParticleTypes.FLAME, e.position(), 3, 0.2);
                    }
            });
        });
        def("sun", 3, "Sun Pillar", 20, 3, ticks(1), c ->
            HeroFx.telegraph(c.hero, c.aim, c.r(), 20, ParticleTypes.END_ROD, () -> {
                for (LivingEntity e : enemies(c, c.aim, c.r()))
                    hurt(c, e, c.dmg(1));
                HeroFx.particles(c.level, ParticleTypes.END_ROD, c.aim.add(0, 3, 0), 60, 0.4, 0.3);
                HeroFx.sound(c.level, c.aim, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.4f);
            }));
        def("sun", 4, "High Noon", 0, 6, ticks(30), c -> {
            effect(friends(c, c.hero.position(), c.r()), MobEffects.DAMAGE_BOOST, c.t(), 0);
            List<LivingEntity> dead = new ArrayList<>();
            for (LivingEntity e : enemies(c, c.hero.position(), c.r()))
                if (HeroFx.isUndead(e))
                    dead.add(e);
            effect(dead, MobEffects.WEAKNESS, c.t(), 0);
            HeroFx.ring(c.level, ParticleTypes.END_ROD, c.hero.position(), c.r());
        });
        twist("sun", "The Midnight Sun", 0, 3, ticks(10), c ->
            HeroFx.pulse(c.hero, 10, c.t(), () -> puff(c, ParticleTypes.END_ROD, c.hero.position().add(0, 2, 0), 6, 0.6)));

        // ---- night
        def("night", 1, "Dusk Veil", 0, 1, ticks(5), c -> {
            effect(List.of(c.hero), MobEffects.INVISIBILITY, c.t(), 0);
            puff(c, ParticleTypes.SQUID_INK, c.hero.position(), 15, 0.6);
        });
        def("night", 2, "Shadow Cloak", 0, 6, ticks(6), c ->
            effect(friends(c, c.hero.position(), c.r()), MobEffects.INVISIBILITY, c.t(), 0));
        def("night", 3, "Night Mending", 0, 6, ticks(30), c ->
            effect(friends(c, c.hero.position(), c.r()), MobEffects.REGENERATION, c.t(), 0));
        def("night", 4, "Moonlit Fog", 0, 10, ticks(20), c -> {
            effect(friends(c, c.hero.position(), c.r()), MobEffects.INVISIBILITY, c.t(), 0);
            effect(enemies(c, c.hero.position(), c.r()), MobEffects.INVISIBILITY, c.t(), 0);
            puff(c, ParticleTypes.CLOUD, c.hero.position(), 60, 4);
        });
        twist("night", "A Very Short Night", 0, 1, ticks(3), c -> {
            effect(List.of(c.hero), MobEffects.BLINDNESS, c.t(), 0);
            puff(c, ParticleTypes.SQUID_INK, c.hero.position(), 10, 0.4);
        });

        // ---- wind
        def("wind", 1, "Gust", 0, 6, ticks(1), c -> {
            for (LivingEntity e : enemies(c, c.hero.position(), c.r()))
                HeroFx.knockback(c.hero.position(), e, 0.8);
            puff(c, ParticleTypes.CLOUD, c.hero.position(), 20, 2);
        });
        def("wind", 2, "Gale", 0, 6, ticks(3), c -> {
            for (LivingEntity e : enemies(c, c.aim, c.r())) {
                HeroFx.knockback(c.hero.position(), e, 1.4);
                HeroFx.slow(e, c.t(), 0);
            }
            HeroFx.trail(c.level, ParticleTypes.CLOUD, c.hero.position().add(0, 1, 0), c.aim.add(0, 1, 0));
        });
        def("wind", 3, "Whirlwind", 12, 4, ticks(6), c ->
            HeroFx.pulse(c.hero, 20, c.t(), () -> {
                for (LivingEntity e : enemies(c, c.aim, c.r())) {
                    hurt(c, e, c.dmg(6));
                    HeroFx.slow(e, 20, 1);
                }
                HeroFx.ring(c.level, ParticleTypes.CLOUD, c.aim, c.r());
            }));
        def("wind", 4, "Tailwind", 0, 6, ticks(20), c -> {
            effect(friends(c, c.hero.position(), c.r()), MobEffects.MOVEMENT_SPEED, c.t(), 1);
            HeroFx.ring(c.level, ParticleTypes.CLOUD, c.hero.position(), c.r());
        });
        twist("wind", "The Wrong Way", 0, 6, ticks(1), c -> {
            for (LivingEntity e : friends(c, c.hero.position(), c.r()))
                if (e != c.hero)
                    HeroFx.knockback(c.hero.position(), e, 0.6);
        });

        // ---- earth (temporary blocks: WagerBlocks takes them down on time)
        def("earth", 1, "Stone Wall", 0, 2, ticks(20), c -> wall(c, 3, 2, Blocks.COBBLESTONE.defaultBlockState()));
        def("earth", 2, "Long Wall", 0, 4, ticks(30), c -> wall(c, 7, 2, Blocks.COBBLESTONE.defaultBlockState()));
        def("earth", 3, "The Pit", 30, 5, ticks(2), c ->
            HeroFx.telegraph(c.hero, c.aim, c.r(), 40, ParticleTypes.ANGRY_VILLAGER, () -> {
                for (LivingEntity e : enemies(c, c.aim, c.r())) {
                    hurt(c, e, c.dmg(1));
                    HeroFx.stun(e, 20);
                }
                puff(c, ParticleTypes.LARGE_SMOKE, c.aim, 30, 2.5);
                HeroFx.sound(c.level, c.aim, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.6f);
            }));
        def("earth", 4, "A Hill", 0, 3, ticks(60), c -> {
            long until = c.end();
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos base = ground(c.level, c.aim.x + dx, c.aim.z + dz);
                    int h = Math.abs(dx) == 2 || Math.abs(dz) == 2 ? 1 : 2;
                    for (int y = 0; y < h; y++)
                        WagerBlocks.place(c.level, base.above(y), Blocks.DIRT.defaultBlockState(), until);
                }
            puff(c, ParticleTypes.CLOUD, c.aim, 30, 2.5);
        });
        twist("earth", "Underfoot", 0, 1, ticks(1), c -> {
            c.hero.push(0, 0.8, 0);
            c.hero.hurtMarked = true;
            puff(c, ParticleTypes.CLOUD, c.hero.position(), 15, 0.5);
        });

        // ---- fire
        def("fire", 1, "Campfire", 0, 1, ticks(60), c -> {
            BlockPos at = ground(c.level, c.aim.x, c.aim.z);
            WagerBlocks.place(c.level, at, Blocks.CAMPFIRE.defaultBlockState(), c.end());
        });
        def("fire", 2, "Line of Fire", 32, 6, ticks(4), c -> {
            Vec3 from = c.hero.position();
            Vec3 d = c.aim.subtract(from);
            Vec3 to = from.add(d.lengthSqr() < 1e-6 ? new Vec3(c.r(), 0, 0) : d.normalize().scale(c.r()));
            HeroFx.pulse(c.hero, 10, c.t(), () -> {
                for (LivingEntity e : HeroFx.enemiesOnLine(c.level, c.hero, from, to, 1.0))
                    hurt(c, e, c.dmg(8));
                HeroFx.trail(c.level, ParticleTypes.FLAME, from.add(0, 0.3, 0), to.add(0, 0.3, 0));
            });
        });
        def("fire", 3, "Lava Geyser", 30, 3, ticks(2), c ->
            HeroFx.telegraph(c.hero, c.aim, c.r(), 30, ParticleTypes.FLAME, () -> {
                for (LivingEntity e : enemies(c, c.aim, c.r())) {
                    hurt(c, e, c.dmg(1));
                    e.push(0, 0.7, 0);
                    e.hurtMarked = true;
                }
                HeroFx.particles(c.level, ParticleTypes.LAVA, c.aim, 30, 1.0, 0.3);
                HeroFx.sound(c.level, c.aim, SoundEvents.LAVA_POP, 1.5f, 0.7f);
            }));
        def("fire", 4, "Firestorm", 36, 6, ticks(6), c ->
            HeroFx.telegraph(c.hero, c.aim, c.r(), 20, ParticleTypes.FLAME, () ->
                HeroFx.pulse(c.hero, 20, c.t(), () -> {
                    for (LivingEntity e : enemies(c, c.aim, c.r()))
                        hurt(c, e, c.dmg(6));
                    HeroFx.particles(c.level, ParticleTypes.FLAME, c.aim.add(0, 0.5, 0), 40, c.r() / 2, 0.05);
                })));
        twist("fire", "His Hat Is on Fire", 3, 0, ticks(2), c -> {
            c.hero.hurt(c.level.damageSources().onFire(), c.spec.damage());
            HeroFx.slow(c.hero, c.t(), 4);
            puff(c, ParticleTypes.FLAME, c.hero.position().add(0, 1.2, 0), 15, 0.2);
        });

        // ---- ice
        def("ice", 1, "Snowfall", 0, 6, ticks(30), c ->
            HeroFx.pulse(c.hero, 10, c.t(), () -> HeroFx.particles(c.level, ParticleTypes.SNOWFLAKE,
                c.hero.position().add(0, 3, 0), 20, c.r() / 2, 0)));
        def("ice", 2, "Frost Sheet", 0, 5, ticks(10), c -> {
            for (LivingEntity e : enemies(c, c.aim, c.r()))
                HeroFx.slow(e, c.t(), 0);
            HeroFx.ring(c.level, ParticleTypes.SNOWFLAKE, c.aim, c.r());
        });
        def("ice", 3, "Hailstorm", 18, 4, ticks(3), c ->
            HeroFx.telegraph(c.hero, c.aim, c.r(), 20, ParticleTypes.SNOWFLAKE, () -> {
                for (LivingEntity e : enemies(c, c.aim, c.r())) {
                    hurt(c, e, c.dmg(1));
                    HeroFx.slow(e, c.t(), 2);
                }
                HeroFx.particles(c.level, ParticleTypes.ITEM_SNOWBALL, c.aim.add(0, 2, 0), 40, c.r() / 2, 0.1);
            }));
        def("ice", 4, "Deep Freeze", 0, 6, ticks(2), c ->
            HeroFx.telegraph(c.hero, c.hero.position(), c.r(), 20, ParticleTypes.SNOWFLAKE, () -> {
                for (LivingEntity e : enemies(c, c.hero.position(), c.r()))
                    HeroFx.stun(e, 30);
                HeroFx.sound(c.level, c.hero.position(), SoundEvents.GLASS_BREAK, 1.5f, 0.6f);
            }));
        twist("ice", "Frozen Solid", 0, 0, ticks(1.5f), c -> {
            HeroFx.stun(c.hero, c.t());
            puff(c, ParticleTypes.SNOWFLAKE, c.hero.position(), 20, 0.4);
        });

        // ---- heal
        def("heal", 1, "Patch Job", 0, 8, ticks(1), c -> {
            LivingEntity t = mostHurt(friends(c, c.hero.position(), c.r()));
            if (t != null) {
                t.heal(15);
                puff(c, ParticleTypes.HEART, t.position(), 5, 0.3);
            }
        });
        def("heal", 2, "Mending Circle", 0, 8, ticks(1), c -> {
            for (LivingEntity e : friends(c, c.hero.position(), c.r()))
                e.heal(20);
            HeroFx.ring(c.level, ParticleTypes.HEART, c.hero.position(), c.r());
        });
        def("heal", 3, "Second Wind", 0, 10, ticks(1), c -> {
            for (LivingEntity e : friends(c, c.hero.position(), c.r())) {
                e.heal(35);
                for (MobEffectInstance inst : new ArrayList<>(e.getActiveEffects()))
                    if (inst.getEffect().getCategory() == MobEffectCategory.HARMFUL)
                        e.removeEffect(inst.getEffect());
            }
            HeroFx.ring(c.level, ParticleTypes.HEART, c.hero.position(), c.r());
        });
        def("heal", 4, "Wellspring", 0, 12, ticks(20), c ->
            effect(friends(c, c.hero.position(), c.r()), MobEffects.REGENERATION, c.t(), 1));
        twist("heal", "I Heal Who I Like", 0, 12, ticks(1), c -> {
            LivingEntity t = HeroFx.nearestEnemy(c.level, c.hero, c.hero.position(), c.r());
            if (t != null) {
                t.heal(15);
                puff(c, ParticleTypes.HEART, t.position(), 5, 0.3);
            }
        });

        // ---- shield (Absorption: 4 HP a level)
        def("shield", 1, "Spare Shield", 0, 8, ticks(10), c -> {
            List<LivingEntity> f = friends(c, c.hero.position(), c.r());
            LivingEntity t = mostHurt(f);
            effect(List.of(t != null ? t : c.hero), MobEffects.ABSORPTION, c.t(), 3);
        });
        def("shield", 2, "Shield Circle", 0, 8, ticks(10), c ->
            effect(friends(c, c.hero.position(), c.r()), MobEffects.ABSORPTION, c.t(), 4));
        def("shield", 3, "Lantern Ward", 0, 6, ticks(8), c -> {
            effect(friends(c, c.hero.position(), c.r()), MobEffects.DAMAGE_RESISTANCE, c.t(), 1);
            HeroFx.ring(c.level, ParticleTypes.END_ROD, c.hero.position(), c.r());
        });
        def("shield", 4, "Golden Moment", 0, 8, ticks(2), c ->
            HeroFx.telegraph(c.hero, c.hero.position(), c.r(), 20, ParticleTypes.END_ROD, () ->
                effect(friends(c, c.hero.position(), c.r()), MobEffects.DAMAGE_RESISTANCE, c.t(), 4)));
        twist("shield", "Mine, Actually", 0, 0, ticks(10), c ->
            effect(List.of(c.hero), MobEffects.ABSORPTION, c.t(), 3));

        // ---- speed
        def("speed", 1, "Quick Step", 0, 0, ticks(6), c -> effect(List.of(c.hero), MobEffects.MOVEMENT_SPEED, c.t(), 0));
        def("speed", 2, "Haste", 0, 8, ticks(8), c -> effect(friends(c, c.hero.position(), c.r()), MobEffects.MOVEMENT_SPEED, c.t(), 0));
        def("speed", 3, "Rush", 0, 10, ticks(6), c -> effect(friends(c, c.hero.position(), c.r()), MobEffects.MOVEMENT_SPEED, c.t(), 1));
        def("speed", 4, "Unstoppable", 0, 12, ticks(15), c -> {
            for (LivingEntity e : friends(c, c.hero.position(), c.r()))
                e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            effect(friends(c, c.hero.position(), c.r()), MobEffects.MOVEMENT_SPEED, c.t(), 1);
        });
        twist("speed", "Warm-up First", 0, 8, ticks(6), c -> {
            List<LivingEntity> f = friends(c, c.hero.position(), c.r());
            effect(f, MobEffects.MOVEMENT_SLOWDOWN, ticks(2), 0);
            HeroFx.after(c.hero, ticks(2), () -> effect(f, MobEffects.MOVEMENT_SPEED, ticks(4), 1));
        });

        // ---- strength
        def("strength", 1, "Lucky Punch", 0, 0, ticks(8), c -> effect(List.of(c.hero), MobEffects.DAMAGE_BOOST, c.t(), 0));
        def("strength", 2, "Battle Cry", 0, 8, ticks(8), c -> effect(friends(c, c.hero.position(), c.r()), MobEffects.DAMAGE_BOOST, c.t(), 0));
        def("strength", 3, "War Fever", 0, 10, ticks(10), c -> {
            List<LivingEntity> f = friends(c, c.hero.position(), c.r());
            effect(f, MobEffects.DAMAGE_BOOST, c.t(), 0);
            effect(f, MobEffects.MOVEMENT_SPEED, c.t(), 0);
        });
        def("strength", 4, "Turn the Tables", 0, 8, ticks(6), c -> {
            effect(friends(c, c.hero.position(), c.r()), MobEffects.DAMAGE_BOOST, c.t(), 1);
            effect(enemies(c, c.hero.position(), c.r()), MobEffects.WEAKNESS, c.t(), 1);
            HeroFx.ring(c.level, ParticleTypes.WAX_ON, c.hero.position(), c.r());
        });
        twist("strength", "A Fair Fight", 0, 6, ticks(4), c ->
            effect(enemies(c, c.hero.position(), c.r()), MobEffects.DAMAGE_BOOST, c.t(), 0));

        // ---- summon (temporary: they crumble on time or with the hero; counted in pop while alive)
        def("summon", 1, "A Chicken", 0, 2, ticks(30), c -> summon(c, EntityType.CHICKEN, 1, c.hero.position(), ""));
        def("summon", 2, "Hired Hands", 0, 2, ticks(30), c ->
            summon(c, EntityRegistrar.MILITIA_UNIT.get(), Math.max(1, Math.min(2, c.quantity == 0 ? 2 : c.quantity)), c.hero.position(), c.owner));
        def("summon", 3, "Hired Help", 0, 2, ticks(40), c ->
            summon(c, EntityRegistrar.MILITIA_UNIT.get(), Math.max(1, Math.min(4, c.quantity == 0 ? 4 : c.quantity)), c.hero.position(), c.owner));
        def("summon", 4, "Dragon Egg", 0, 2, ticks(40), c -> summon(c, EntityRegistrar.BONE_DRAGON_UNIT.get(), 1, c.hero.position(), c.owner));
        twist("summon", "Freelancers", 0, 2, ticks(30), c -> summon(c, EntityRegistrar.MILITIA_UNIT.get(), 2, c.hero.position(), ""));

        // ---- animal
        def("animal", 1, "A Pig", 0, 2, ticks(60), c -> summon(c, EntityType.PIG, 1, c.hero.position(), ""));
        def("animal", 2, "Wolf Pack", 0, 2, ticks(30), c -> summon(c, EntityRegistrar.WOLF_UNIT.get(), 3, c.hero.position(), c.owner));
        def("animal", 3, "Bison Charge", 25, 6, ticks(1), c -> {
            Vec3 from = c.hero.position();
            Vec3 d = c.aim.subtract(from);
            Vec3 to = from.add(d.lengthSqr() < 1e-6 ? new Vec3(c.r() * 2, 0, 0) : d.normalize().scale(c.r() * 2));
            for (LivingEntity e : HeroFx.enemiesOnLine(c.level, c.hero, from, to, 1.5)) {
                hurt(c, e, c.dmg(1));
                HeroFx.knockback(from, e, 1.0);
            }
            HeroFx.trail(c.level, ParticleTypes.CLOUD, from.add(0, 0.5, 0), to.add(0, 0.5, 0));
            HeroFx.sound(c.level, from, SoundEvents.RAVAGER_ROAR, 1.2f, 1.2f);
        });
        def("animal", 4, "A Flock of Birds", 0, 12, ticks(10), c -> {
            effect(friends(c, c.hero.position(), c.r()), MobEffects.INVISIBILITY, c.t(), 0);
            HeroFx.pulse(c.hero, 10, c.t(), () -> HeroFx.particles(c.level, ParticleTypes.CLOUD,
                c.hero.position().add(0, 4, 0), 15, 3, 0.1));
        });
        twist("animal", "A Loyal Chicken", 0, 1, ticks(90), c -> summon(c, EntityType.CHICKEN, 1, c.hero.position(), ""));

        // ---- light / vision (reveals go through the fog for the owner's team)
        def("light", 1, "A Lantern", 0, 3, ticks(30), c ->
            HeroFx.pulse(c.hero, 10, c.t(), () -> HeroFx.particles(c.level, ParticleTypes.END_ROD, c.aim.add(0, 1.5, 0), 4, 0.5, 0.01)));
        def("light", 2, "Reveal", 0, 10, ticks(10), c -> reveal(c, enemies(c, c.aim, c.r()), c.t()));
        def("light", 3, "Flare", 0, 16, ticks(8), c -> {
            reveal(c, enemies(c, c.hero.position(), c.r()), c.t());
            HeroFx.particles(c.level, ParticleTypes.FIREWORK, c.hero.position().add(0, 8, 0), 60, 1.5, 0.2);
        });
        def("light", 4, "The Fog Lifts", 0, 16, ticks(6), c -> {
            reveal(c, enemies(c, c.hero.position(), c.r()), c.t());
            reveal(c, enemies(c, c.aim, c.r()), c.t());
        });
        twist("light", "The Wrong Spotlight", 0, 8, ticks(5), c ->
            effect(friends(c, c.hero.position(), c.r()), MobEffects.GLOWING, c.t(), 0));

        // ---- hide / fog
        def("hide", 1, "Smoke Puff", 0, 3, ticks(5), c -> {
            effect(List.of(c.hero), MobEffects.INVISIBILITY, c.t(), 0);
            puff(c, ParticleTypes.LARGE_SMOKE, c.hero.position(), 30, 1.5);
        });
        def("hide", 2, "Fog Bank", 0, 6, ticks(8), c -> {
            effect(friends(c, c.hero.position(), c.r()), MobEffects.INVISIBILITY, c.t(), 0);
            puff(c, ParticleTypes.CLOUD, c.hero.position(), 50, 3);
        });
        def("hide", 3, "Thick Fog", 0, 10, ticks(12), c -> {
            effect(friends(c, c.hero.position(), c.r()), MobEffects.INVISIBILITY, c.t(), 0);
            puff(c, ParticleTypes.CLOUD, c.hero.position(), 80, 5);
        });
        def("hide", 4, "Still Cloak", 0, 12, ticks(15), c ->
            effect(friends(c, c.hero.position(), c.r()), MobEffects.INVISIBILITY, c.t(), 0));
        twist("hide", "Fog on Them", 0, 8, ticks(5), c ->
            effect(enemies(c, c.hero.position(), c.r()), MobEffects.INVISIBILITY, c.t(), 0));

        // ---- teleport / move
        def("teleport", 1, "Hop", 0, 5, ticks(1), c -> blinkTo(c.hero, HeroFx.landingSpot(c.level, c.hero, c.aim, c.r())));
        def("teleport", 2, "Blink", 0, 12, ticks(1), c -> {
            puff(c, ParticleTypes.PORTAL, c.hero.position(), 20, 0.5);
            blinkTo(c.hero, HeroFx.landingSpot(c.level, c.hero, c.aim, c.r()));
            puff(c, ParticleTypes.PORTAL, c.hero.position(), 20, 0.5);
        });
        def("teleport", 3, "Swap", 0, 14, ticks(1), c -> {
            LivingEntity other = null;
            double best = c.r() * c.r();
            // an enemy, or one of the owner's own units - never an ally's or a garrisoned one (Mode A note 5)
            List<LivingEntity> all = ownMovable(c, friends(c, c.aim, 3));
            for (LivingEntity e : enemies(c, c.aim, 3))
                if (!(e instanceof Unit u && u.isGarrisoned()))   // a tower's crew stays in its tower
                    all.add(e);
            for (LivingEntity e : all) {
                double d = e.distanceToSqr(c.hero);
                if (e != c.hero && d <= best) {
                    best = d;
                    other = e;
                }
            }
            if (other == null)
                return;
            LivingEntity o = other;
            HeroFx.trail(c.level, ParticleTypes.PORTAL, c.hero.position().add(0, 1, 0), o.position().add(0, 1, 0));
            HeroFx.after(c.hero, 20, () -> {
                if (!o.isAlive() || o.distanceToSqr(c.hero) > c.r() * c.r())
                    return;
                Vec3 a = c.hero.position(), b = o.position();
                blinkTo(c.hero, b);
                if (o instanceof Mob m)
                    blinkTo(m, a);
                else
                    o.teleportTo(a.x, a.y, a.z);
            });
        });
        def("teleport", 4, "Everyone, With Me", 0, 12, ticks(2), c -> {
            Vec3 to = HeroFx.landingSpot(c.level, c.hero, c.aim, c.r());
            HeroFx.telegraph(c.hero, c.hero.position(), 6, 30, ParticleTypes.PORTAL, () -> {
                Vec3 shift = to.subtract(c.hero.position());
                for (LivingEntity e : ownMovable(c, friends(c, c.hero.position(), 6)))
                    if (e instanceof Mob m)
                        blinkTo(m, HeroFx.landingSpot(c.level, m, m.position().add(shift), c.r() + 2));
            });
        });
        twist("teleport", "Alone Again", 0, 6, ticks(1), c -> {
            double a = c.rng.nextDouble() * Math.PI * 2;
            blinkTo(c.hero, HeroFx.landingSpot(c.level, c.hero, c.hero.position().add(Math.cos(a) * c.r(), 0, Math.sin(a) * c.r()), c.r()));
        });

        // ---- money / resources
        def("money", 1, "Loose Change", 0, 0, ticks(1), c -> pay(c.owner, 15, 0));
        def("money", 2, "A Purse", 0, 0, ticks(1), c -> {
            Resources r = null;
            for (Resources each : ResourcesServerEvents.resourcesList)
                if (each.ownerName.equals(c.owner))
                    r = each;
            if (r != null && r.wood < r.ore)
                pay(c.owner, 0, 60);
            else
                pay(c.owner, 60, 0);
        });
        def("money", 3, "A Strongbox", 0, 0, ticks(1), c -> pay(c.owner, 150, 0));
        def("money", 4, "The Jackpot", 0, 0, ticks(1), c -> {
            pay(c.owner, 300, 0);
            HeroFx.particles(c.level, ParticleTypes.WAX_ON, c.hero.position().add(0, 2, 0), 60, 1.5, 0.2);
        });
        twist("money", "Fair Is Fair", 0, 16, ticks(1), c -> {
            pay(c.owner, 15, 0);
            LivingEntity e = HeroFx.nearestEnemy(c.level, c.hero, c.hero.position(), c.r());
            if (e instanceof Unit u)
                pay(u.getOwnerName(), 60, 0);
        });

        // ---- trap (a hidden spot that waits for an enemy; each one goes off once)
        def("trap", 1, "Snare", 0, 2, ticks(30), c ->
            HeroFx.pulse(c.hero, 20, c.t(), () -> {
                for (LivingEntity e : enemies(c, c.aim, c.r()))
                    HeroFx.root(e, 20);
            }));
        def("trap", 2, "Three Mines", 15, 2, ticks(60), c -> {
            List<Vec3> mines = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3;
                mines.add(c.aim.add(Math.cos(a) * 2, 0, Math.sin(a) * 2));
            }
            HeroFx.pulse(c.hero, 5, c.t(), () -> {
                for (int i = mines.size() - 1; i >= 0; i--) {
                    Vec3 m = mines.get(i);
                    if (enemies(c, m, 1.5).isEmpty())
                        continue;
                    for (LivingEntity e : enemies(c, m, c.r()))
                        hurt(c, e, c.dmg(1));
                    HeroFx.particles(c.level, ParticleTypes.EXPLOSION, m, 3, 0.5, 0);
                    HeroFx.sound(c.level, m, SoundEvents.GENERIC_EXPLODE, 1.0f, 1.3f);
                    mines.remove(i);
                }
            });
        });
        def("trap", 3, "Spike Field", 40, 5, ticks(20), c ->
            HeroFx.pulse(c.hero, 20, c.t(), () -> {
                for (LivingEntity e : enemies(c, c.aim, c.r())) {
                    hurt(c, e, c.dmg(20));
                    HeroFx.slow(e, 20, 0);
                }
                HeroFx.particles(c.level, ParticleTypes.CRIT, c.aim, 20, c.r() / 2, 0);
            }));
        def("trap", 4, "A Hidden Pit", 30, 6, ticks(60), c -> {
            boolean[] sprung = { false };
            HeroFx.pulse(c.hero, 5, c.t(), () -> {
                if (sprung[0] || enemies(c, c.aim, 2).isEmpty())
                    return;
                sprung[0] = true;
                for (LivingEntity e : enemies(c, c.aim, c.r())) {
                    hurt(c, e, c.dmg(1));
                    HeroFx.stun(e, 20);
                }
                puff(c, ParticleTypes.LARGE_SMOKE, c.aim, 40, 3);
                HeroFx.sound(c.level, c.aim, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.5f);
            });
        });
        twist("trap", "The Trap Was Mine", 0, 0, ticks(1), c -> HeroFx.root(c.hero, 20));

        // ---- fear (our Feared status: the unit runs from what scared it)
        def("fear", 1, "Boo", 0, 4, ticks(1), c ->
            effect(enemies(c, c.hero.position(), c.r()), MobEffectRegistrar.FEARFUL.get(), c.t(), 0));
        def("fear", 2, "Terror", 0, 6, ticks(1.5f), c ->
            effect(enemies(c, c.hero.position(), c.r()), MobEffectRegistrar.FEARFUL.get(), c.t(), 0));
        def("fear", 3, "Dread", 0, 8, ticks(1.5f), c -> {
            List<LivingEntity> en = enemies(c, c.hero.position(), c.r());
            effect(en, MobEffectRegistrar.FEARFUL.get(), c.t(), 0);
            effect(en, MobEffects.MOVEMENT_SLOWDOWN, ticks(3), 0);
        });
        def("fear", 4, "Nightmare", 0, 10, ticks(10), c -> {
            List<LivingEntity> en = enemies(c, c.hero.position(), c.r());
            effect(en, MobEffectRegistrar.FEARFUL.get(), ticks(1.5f), 0);
            effect(en, MobEffects.WEAKNESS, c.t(), 0);
        });
        twist("fear", "Boo (to Us)", 0, 6, ticks(1), c ->
            effect(friends(c, c.hero.position(), c.r()), MobEffectRegistrar.FEARFUL.get(), c.t(), 0));

        // ---- party / joke
        def("party", 1, "Confetti", 0, 2, ticks(1), c -> {
            HeroFx.particles(c.level, ParticleTypes.FIREWORK, c.hero.position().add(0, 2, 0), 40, 1.0, 0.15);
            HeroFx.sound(c.level, c.hero.position(), SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 1.2f);
        });
        def("party", 2, "Marching Song", 0, 10, ticks(8), c -> {
            effect(friends(c, c.hero.position(), c.r()), MobEffects.MOVEMENT_SPEED, c.t(), 0);
            HeroFx.pulse(c.hero, 10, c.t(), () -> puff(c, ParticleTypes.NOTE, c.hero.position().add(0, 1.5, 0), 3, 0.8));
        });
        def("party", 3, "A Truce", 0, 10, ticks(1.5f), c -> {
            List<LivingEntity> all = new ArrayList<>(friends(c, c.hero.position(), c.r()));
            all.addAll(enemies(c, c.hero.position(), c.r()));
            for (LivingEntity e : all)
                HeroFx.stun(e, c.t());
            puff(c, ParticleTypes.NOTE, c.hero.position(), 20, 3);
        });
        def("party", 4, "Festival", 0, 12, ticks(15), c -> {
            List<LivingEntity> f = friends(c, c.hero.position(), c.r());
            effect(f, MobEffects.REGENERATION, c.t(), 0);
            effect(f, MobEffects.MOVEMENT_SPEED, c.t(), 0);
            HeroFx.particles(c.level, ParticleTypes.FIREWORK, c.hero.position().add(0, 4, 0), 80, 3, 0.2);
        });
        twist("party", "A Solo Dance", 0, 0, ticks(1.5f), c -> {
            HeroFx.stun(c.hero, c.t());
            puff(c, ParticleTypes.NOTE, c.hero.position().add(0, 1.5, 0), 8, 0.6);
        });
    }
}
