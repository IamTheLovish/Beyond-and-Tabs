package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.HeroAbility;
import com.solegendary.reignofnether.hero.ai.HeroBrain;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.unit.goals.GenericTargetedSpellGoal;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;
import oshi.util.tuples.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The per-hero state every faction hero carries (FactionHero's defaults read and write it): level, ability points, ranks,
 * mana, the four abilities, the shared cast goal and the short-lived things abilities start - a scripted leap/dash, a
 * frenzy, temporary summons. One object per hero so the seven unit classes stay one-liners over their bodies.
 * <p>
 * Created lazily by the unit's heroCore() - the body's constructor already asks for abilities before the subclass's
 * fields exist (the Harbourmaster pattern), so the field has no initializer and the first call builds it.
 */
public final class HeroCore {

    private static final Keybinding[] KEYS = { Keybindings.abilitySlot1, Keybindings.abilitySlot2,
        Keybindings.abilitySlot3, Keybindings.abilitySlot4 };
    static final UUID FRENZY_SPEED_ID = UUID.fromString("7b1f8f5e-3c2a-4e4f-9a51-1d0b6f2e9c11");

    public final HeroKit kit;
    final Mob mob;

    public int experience = 0;
    public int skillPoints = 1;   // the first point comes with the hero (Reign of Nether's heroes do the same)
    public boolean rankUpMenuOpen = false;
    public boolean needsStatSync = false;
    boolean clientSynced = false; // the first experience sync on a client is a load, not a boon
    public float mana;
    public float maxMana;
    float lastSentMana = -1;

    public final Object2ObjectArrayMap<HeroAbility, Integer> ranks = new Object2ObjectArrayMap<>();
    public final List<FactionHeroAbility> abilities = new ArrayList<>(4);
    public final Abilities abilitySet;

    // the one aimed cast in flight (FactionHeroAbility.use -> startCast)
    GenericTargetedSpellGoal castGoal;
    @Nullable FactionHeroAbility castGoalAbility;
    int castChannel = 0;

    // timed effects
    public int frenzyTicks = 0;
    public float damageTakenMult = 1f;

    // scripted motion: a leap / dash / grapple flown along an arc, then onLand
    Vec3 motionFrom, motionTo;
    int motionTick, motionTicks;
    double motionApex;
    @Nullable Consumer<Mob> onLand;

    // a hero that spawns above level 1 (H13) gets its level's stats once its attributes exist (first server tick)
    boolean needsStartStats = false;

    // the v2 hero AI's state (hero/ai/HeroBrain), made on first use: it lives and dies with the hero
    @Nullable HeroBrain brain;

    // timed ground effects (Firewall, Lava Geyser, Ember Totem): each returns true when finished; run every server tick
    final List<java.util.function.BooleanSupplier> timers = new ArrayList<>();

    // temporary summons (Raise Dead): entity + game time it crumbles
    final List<Entity> summons = new ArrayList<>();
    final List<Long> summonEnds = new ArrayList<>();

    public HeroCore(HeroKit kit, Mob mob) {
        this.kit = kit;
        this.mob = mob;
        this.maxMana = kit.mana;
        this.mana = kit.mana;
        if (kit.startLevel > 1) {
            // a T2-lab hero (H13) comes in at its start level with the points its levels would have given: the first
            // point plus one per level, as HeroUnit.addExperience grants them (a loaded hero overwrites both from NBT)
            this.experience = HeroProgression.xpForLevel(kit.startLevel);
            this.skillPoints = 1 + (kit.startLevel - 1);
            this.needsStartStats = true;
        }
        List<Pair<Ability, Keybinding>> list = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            FactionHeroAbility a = new FactionHeroAbility(kit.abilities[i], i);
            abilities.add(a);
            list.add(new Pair<>(a, KEYS[i]));
        }
        this.abilitySet = new Abilities(list);
    }

    GenericTargetedSpellGoal castGoal() {
        if (castGoal == null) {
            castGoal = new GenericTargetedSpellGoal(mob, 0, 8, null, null, null) {
                @Override
                public int getChannelTicksMax() { return castChannel; }
            };
        }
        return castGoal;
    }

    /** Hands the cast goal an aimed cast: walk into range of the target (an entity, or a block), wind up, land. */
    void startCast(FactionHero fh, FactionHeroAbility ability, @Nullable LivingEntity target, @Nullable BlockPos bp) {
        GenericTargetedSpellGoal g = castGoal();
        g.stopExceptAnimations();
        castGoalAbility = ability;
        castChannel = ability.spec.channelTicks;
        g.range = Math.max(1.5f, ability.range);
        g.setAbility(null);   // FactionHeroAbility.castNow pays the cooldown/mana, and only when the effect worked
        g.onEntityCast = t -> ability.castNow(fh, t, t.position());
        g.onGroundCast = p -> ability.castNow(fh, null, new Vec3(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5));
        g.onBuildingCast = null;
        if (target != null)
            g.setTarget(target);
        else if (bp != null)
            g.setTarget(bp);
    }

    void stopCast() {
        if (castGoal != null)
            castGoal.stopExceptAnimations();
        castGoalAbility = null;
    }

    /** Starts a scripted arc to {@code to} over {@code ticks} ticks peaking {@code apex} blocks up; onLand at the end. */
    void startMotion(Vec3 to, int ticks, double apex, @Nullable Consumer<Mob> onLand) {
        this.motionFrom = mob.position();
        this.motionTo = to;
        this.motionTick = 0;
        this.motionTicks = Math.max(2, ticks);
        this.motionApex = apex;
        this.onLand = onLand;
    }

    public boolean inMotion() { return motionTo != null; }

    /** The v2 brain's state for this hero (made on first use). */
    public HeroBrain brain() {
        if (brain == null)
            brain = new HeroBrain(mob.getId());
        return brain;
    }

    /** The brain if one was ever made (MiscUtil asks this for every target pick: never makes one). */
    @Nullable
    public HeroBrain brainIfAny() {
        return brain;
    }

    void addSummon(Entity e, long endTick) {
        summons.add(e);
        summonEnds.add(endTick);
    }

    /** Server tick: the cast goal, the scripted motion, the frenzy and the summons' lifetimes. */
    void tickServer() {
        if (needsStartStats) {
            needsStartStats = false;
            // a fresh spawn is at full health: lift it to the start level's maximum (a loaded hero keeps its saved health)
            boolean full = mob.getHealth() >= mob.getMaxHealth() - 0.01f;
            ((com.solegendary.reignofnether.unit.interfaces.HeroUnit) mob).setStatsForLevel(full);
        }
        for (int i = timers.size() - 1; i >= 0; i--)
            if (timers.get(i).getAsBoolean())
                timers.remove(i);
        if (castGoal != null)
            castGoal.tick();
        if (motionTo != null) {
            motionTick++;
            double t = Math.min(1.0, motionTick / (double) motionTicks);
            double x = motionFrom.x + (motionTo.x - motionFrom.x) * t;
            double z = motionFrom.z + (motionTo.z - motionFrom.z) * t;
            double y = motionFrom.y + (motionTo.y - motionFrom.y) * t + motionApex * 4 * t * (1 - t);
            Vec3 prev = mob.position();
            mob.getNavigation().stop();
            mob.moveTo(x, y, z, mob.getYRot(), mob.getXRot());
            mob.setDeltaMovement(x - prev.x, y - prev.y, z - prev.z);
            mob.hurtMarked = true;
            mob.fallDistance = 0;
            if (t >= 1.0) {
                motionTo = null;
                Consumer<Mob> land = onLand;
                onLand = null;
                mob.setDeltaMovement(Vec3.ZERO);
                if (land != null)
                    land.accept(mob);
            }
        }
        if (frenzyTicks > 0 && --frenzyTicks == 0)
            endFrenzy();
        if (!summons.isEmpty() && mob.tickCount % 10 == 0) {
            long now = mob.level().getGameTime();
            for (int i = summons.size() - 1; i >= 0; i--) {
                Entity e = summons.get(i);
                if (e.isRemoved() || !e.isAlive() || now >= summonEnds.get(i)) {
                    if (!e.isRemoved() && e.isAlive())
                        e.discard();   // crumbles: no death event, no wreck, no kill credit
                    summons.remove(i);
                    summonEnds.remove(i);
                }
            }
        }
    }

    void startFrenzy(int ticks, float attackSpeedBonus, float damageTaken) {
        AttributeInstance aps = mob.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get());
        if (aps != null) {
            aps.removeModifier(FRENZY_SPEED_ID);
            aps.addTransientModifier(new AttributeModifier(FRENZY_SPEED_ID, "hero_frenzy", attackSpeedBonus,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        frenzyTicks = ticks;
        damageTakenMult = damageTaken;
    }

    void endFrenzy() {
        AttributeInstance aps = mob.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get());
        if (aps != null)
            aps.removeModifier(FRENZY_SPEED_ID);
        frenzyTicks = 0;
        damageTakenMult = 1f;
    }

    /** On death / removal: summons crumble with their caster. */
    void clearSummons() {
        for (Entity e : summons)
            if (!e.isRemoved() && e.isAlive())
                e.discard();
        summons.clear();
        summonEnds.clear();
        timers.clear();   // a dead shaman's fires go out
    }
}
