package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.resources.ResourceCost;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * One faction hero's data (design/hero_mode.md section 5): its body stats at level 1, cost, the faction it belongs to, the
 * lang/texture id, its unit-card role and its four abilities. The seven kits live in {@link HeroKits}; the unit classes
 * (unit/units/heroes) only pick a body and point at their kit, so the numbers sit together where they can be balanced.
 */
public final class HeroKit {

    /** What an ability is aimed at. GROUND takes an entity too (its position); ENEMY needs a hostile unit. */
    public enum Target { SELF, ENEMY, GROUND }

    /**
     * How a bot-owned hero uses the ability (HeroBot): STRIKE at the nearest enemy in range, ENGAGE to close in on one
     * (gap closers, melee heroes only), FIGHT_SELF when enemies are near, FIGHT_GROUND at its own feet when enemies are
     * near, NONE never (movement a bot would only misuse).
     */
    public enum Ai { STRIKE, ENGAGE, FIGHT_SELF, FIGHT_GROUND, NONE }

    /** Unit-card role line under the hero's name (UnitInfoCard): what the hero does for the army. */
    public enum Role { VANGUARD, RAIDER, CASTER, SUPPORT, SKIRMISHER }

    /** An ability's effect, run on the server when the cast lands. False = nothing happened (no cooldown, no mana). */
    @FunctionalInterface
    public interface Effect {
        boolean cast(Cast c);
    }

    /** Everything an effect needs: the hero, the rank being cast, the aim. */
    public static final class Cast {
        public final ServerLevel level;
        public final Mob hero;
        public final FactionHero fh;
        public final AbilitySpec spec;
        public final int rank;   // 1-3
        @Nullable public final LivingEntity target;
        public final Vec3 at;    // the aimed point (the target's position for an entity cast, the hero's for SELF)

        public Cast(ServerLevel level, Mob hero, AbilitySpec spec, int rank, @Nullable LivingEntity target, Vec3 at) {
            this.level = level;
            this.hero = hero;
            this.fh = (FactionHero) hero;
            this.spec = spec;
            this.rank = Math.max(1, Math.min(3, rank));
            this.target = target;
            this.at = at;
        }

        /** The ability's main value at this rank (damage, heal, shield...). */
        public float a() { return spec.a[rank - 1]; }
        /** The ability's second value at this rank (duration, count, jumps...). */
        public float b() { return spec.b[rank - 1]; }
        public float range() { return spec.range[rank - 1]; }
        public String owner() { return fh.getOwnerName(); }
    }

    /** One of the four abilities. Values are per rank (index 0 = rank 1). */
    public static final class AbilitySpec {
        public final String id;          // lang: abilities.reignofnether.hero.<id>(.desc1/.desc2/.ranks)
        public final String icon;        // textures/icons/abilities/<icon>.png
        public final Target target;
        public final Ai ai;
        public final int[] cooldownSeconds;
        public final float[] range;
        public final float radius;
        public final int manaCost;
        public final int channelTicks;   // wind-up once in range (targeted abilities)
        public final float[] a, b;       // per-rank values for the effect and the rank tooltip lines
        public final Effect effect;
        /**
         * Stuns, roots or slows what it hits: the hero AI casts it before the damage (the combo order, lovish's hero AI
         * requirement 1: "stun/slow then damage").
         */
        public boolean cc = false;

        /** Marks the ability as crowd control ({@link #cc}); returns it, for the kit table. */
        public AbilitySpec cc() {
            this.cc = true;
            return this;
        }

        public AbilitySpec(String id, String icon, Target target, Ai ai, int[] cooldownSeconds, float[] range, float radius,
                           int manaCost, int channelTicks, float[] a, float[] b, Effect effect) {
            this.id = id;
            this.icon = icon;
            this.target = target;
            this.ai = ai;
            this.cooldownSeconds = cooldownSeconds;
            this.range = range;
            this.radius = radius;
            this.manaCost = manaCost;
            this.channelTicks = channelTicks;
            this.a = a;
            this.b = b;
            this.effect = effect;
        }
    }

    public final String id;               // entity <id>_unit, mob head <id>.png, lang entity.reignofnether.<id>_unit
    public final String itemName;         // the production item's name (EntityRegistrar.getEntityType)
    public final String factionName;      // Faction key path: villagers, monsters, piglins, verdant_court, ...
    public final Role role;
    public final float health, damage, armour, speed;
    public final float mana, manaPerLevel, manaRegen;
    public final ResourceCost cost;
    public final AbilitySpec[] abilities;
    /** True for a hero trained at a faction's T2 lab (H13): it skips the castle clamp on the hero cap. */
    public boolean t2Lab = false;
    /** The level a fresh hero spawns at (1 for the T1-lab heroes; a T2-lab hero starts higher, ability points included). */
    public int startLevel = 1;

    public HeroKit(String id, String itemName, String factionName, Role role, float health, float damage, float armour,
                   float speed, float mana, float manaPerLevel, float manaRegen, ResourceCost cost, AbilitySpec... abilities) {
        if (abilities.length != 4)
            throw new IllegalArgumentException(id + ": a faction hero has exactly four abilities");
        this.id = id;
        this.itemName = itemName;
        this.factionName = factionName;
        this.role = role;
        this.health = health;
        this.damage = damage;
        this.armour = armour;
        this.speed = speed;
        this.mana = mana;
        this.manaPerLevel = manaPerLevel;
        this.manaRegen = manaRegen;
        this.cost = cost;
        this.abilities = abilities;
    }

    /** Marks the kit as a T2-lab hero that spawns at {@code level} (H13: "the T2 lab is gate enough"). */
    public HeroKit t2Lab(int level) {
        this.t2Lab = true;
        this.startLevel = level;
        return this;
    }

    public String entityId() { return id + "_unit"; }
}
