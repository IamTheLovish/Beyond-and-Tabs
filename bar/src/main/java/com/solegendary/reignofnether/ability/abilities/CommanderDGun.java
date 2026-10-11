package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The commander's D-gun (BAR's signature commander weapon), one per faction with the same rules:
 * aim at a spot, and everything hostile along a {@link #RANGE}-block line from the commander toward it takes
 * {@link #DAMAGE} - enough to delete any T1 unit. Enemy commanders only take {@link #COMMANDER_DAMAGE}, so
 * commander duels stay a fight, not a coin flip. Costs {@link #ENERGY_COST} energy per shot (refused, with the
 * usual "not enough" warning, when the pool can't pay) and has a {@link #CD_SECONDS} s cooldown.
 * <ul>
 *   <li>Sunforged Kingdom - <b>Sunfire Decree</b>: a lance of sunlight.</li>
 *   <li>Ironhide Horde - <b>Bloodstorm</b>: a hurled greataxe that cleaves the line.</li>
 *   <li>Gravebound - <b>Soulreaper</b>: a sweep of soul-fire.</li>
 *   <li>Verdant Court - <b>Thornburst</b>: a cone of thorns instead of a line ({@link #CONE_RANGE} blocks deep,
 *       {@link #CONE_HALF_ANGLE_DEG} degrees either side), and what it hits is rooted for {@link #ROOT_TICKS} ticks.
 *       Shorter than the line, wider up close: a brawler's D-gun for the faction that fights in the thickets.</li>
 *   <li>Tidewrought - <b>Broadside</b>: a walking cannonade instead of a beam - {@link #BROADSIDE_SHOTS} cannonball
 *       impacts stepping {@link #BROADSIDE_STEP} blocks forward, {@link #BROADSIDE_DELAY} ticks apart, each bursting
 *       {@link #BROADSIDE_RADIUS} blocks wide. Every enemy caught takes the D-gun's damage once per volley. Entity damage
 *       only: no explosion, so no terrain and no friend is touched. Same reach as the line, but a quick unit can step
 *       out of the later shots - the Admiral's gun rewards aiming at a crowd, not a runner.</li>
 *   <li>Stormcallers - <b>Heaven's Judgement</b>: a lightning <i>column</i> at the aimed spot (within {@link #RANGE})
 *       instead of a line. A {@link #JUDGEMENT_DELAY}-tick gold glyph ring telegraphs it first (BAR's rule: danger is
 *       shown before it lands), then every enemy within {@link #JUDGEMENT_RADIUS} of the spot takes the D-gun's damage.
 *       A particle bolt from the sky (barfx/LightningFx), no LightningBolt entity: no fire, no friend hit. Paid through
 *       the Mana core (ManaServerEvents.tryCast), so Overcharge doubles its cost and widens it to
 *       {@link #JUDGEMENT_RADIUS_OVERCHARGED}; refused (nothing paid, no cooldown) when the pool can't pay.</li>
 *   <li>Any faction without one designed (FactionTraits) - a plain <b>D-gun</b>, same rules.</li>
 * </ul>
 * Friendly units are never hit (BAR's D-gun does hit friends - kept off here until lovish says otherwise).
 */
public class CommanderDGun extends Ability {

    public static final int CD_SECONDS = 20;
    public static final int RANGE = 14;
    public static final float HALF_WIDTH = 1.5f;
    public static final float DAMAGE = 80f;
    public static final float COMMANDER_DAMAGE = 25f;
    public static final int ENERGY_COST = 150;
    /** Thornburst (Verdant Court): reach and half-angle of the cone, and the root on whatever it hits. */
    public static final float CONE_RANGE = 10f;
    public static final float CONE_HALF_ANGLE_DEG = 35f;
    public static final int ROOT_TICKS = 40;
    /** Broadside (Tidewrought): the walking cannonade's shots, spacing, cadence and burst radius. */
    public static final int BROADSIDE_SHOTS = 6;
    public static final float BROADSIDE_STEP = 2.5f;
    public static final int BROADSIDE_DELAY = 4;
    public static final float BROADSIDE_RADIUS = 2.0f;
    /** Heaven's Judgement (Stormcallers): telegraph ticks before the strike, and the column's radius (plain / overcharged). */
    public static final int JUDGEMENT_DELAY = 12;
    public static final float JUDGEMENT_RADIUS = 2.5f;
    public static final float JUDGEMENT_RADIUS_OVERCHARGED = 3.5f;

    public CommanderDGun() {
        super(UnitAction.COMMANDER_DGUN, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, false, true);
        this.showRangeLine = true;
        this.showRadiusCircle = false;
        this.showRangeCircle = true;
    }

    /** PLAIN: same rules, no faction flavour - factions whose D-gun is not designed yet (they used to get Sunfire). */
    public enum Kind { SUNFIRE, BLOODSTORM, SOULREAPER, THORNBURST, BROADSIDE, JUDGEMENT, PLAIN }

    public static Kind kindFor(Unit unit) {
        return FactionTraits.of(Factions.getFaction(unit)).dgunKind;
    }

    static String titleFor(Kind kind) {
        return switch (kind) {
            case BLOODSTORM -> "Bloodstorm";
            case SOULREAPER -> "Soulreaper";
            case THORNBURST -> "Thornburst";
            case BROADSIDE -> "Broadside";
            case JUDGEMENT -> "Heaven's Judgement";
            case PLAIN -> "D-gun";
            default -> "Sunfire Decree";
        };
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        Kind kind = kindFor(unit);
        String title = titleFor(kind);
        String flavour = switch (kind) {
            case BLOODSTORM -> "The Warlord's greataxe, thrown through the line.";
            case SOULREAPER -> "The Lich Regent's scythe of soul-fire.";
            case THORNBURST -> "The Grove Warden calls the bramble up in a fan.";
            case BROADSIDE -> "The Admiral's guns walk their fire up the beach.";
            case JUDGEMENT -> "The Thunder Sage points, and the sky answers.";
            case PLAIN -> "The commander's own weapon.";
            default -> "The Lord Marshal's lance of sunlight.";
        };
        ResourceLocation icon = switch (kind) {
            case BLOODSTORM -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/iron_axe.png");
            case SOULREAPER -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/netherite_hoe.png");
            case THORNBURST -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/sweet_berries.png");
            case BROADSIDE -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/fire_charge.png");
            case JUDGEMENT -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/lightning_rod.png");
            case PLAIN -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/iron_sword.png");
            default -> ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/blaze_rod.png");
        };
        // the cursor preview follows the kind: Judgement strikes a circle at the aimed spot, the others a line from the commander
        boolean column = kind == Kind.JUDGEMENT;
        this.showRangeLine = !column;
        this.showRadiusCircle = column;
        this.radius = column ? JUDGEMENT_RADIUS : 0;
        return new AbilityButton(title, icon, hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.COMMANDER_DGUN,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.COMMANDER_DGUN),
            null,
            List.of(
                FormattedCharSequence.forward(title + "  (commander D-gun)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(kind == Kind.THORNBURST
                    ? ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown, " + (int) CONE_RANGE
                        + "-block cone that roots. Destroys light units outright."
                    : kind == Kind.JUDGEMENT
                    ? ENERGY_COST + " energy (x2 overcharged), " + CD_SECONDS + " s cooldown: lightning strikes a "
                        + JUDGEMENT_RADIUS + "-block circle up to " + RANGE + " blocks away after a short warning."
                    : kind == Kind.BROADSIDE
                    ? ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown, " + BROADSIDE_SHOTS
                        + " cannon shots walking " + (int) (BROADSIDE_SHOTS * BROADSIDE_STEP) + " blocks. Destroys light units outright."
                    : ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown, " + RANGE
                        + "-block line. Destroys light units outright.", Style.EMPTY),
                FormattedCharSequence.forward(flavour, Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        String owner = unitUsing.getOwnerName();
        // Heaven's Judgement pays through the Mana core, not the pool directly (Overcharge, Mana Wells)
        if (kindFor(unitUsing) == Kind.JUDGEMENT) {
            if (judgement(sl, self, owner, targetBp, false)) {
                this.setToMaxCooldown(unitUsing);
                AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
            }
            return;
        }
        Resources pool = null;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                pool = r;
        if (pool == null || pool.getEnergy() < ENERGY_COST) {
            ResourcesClientboundPacket.warnInsufficientResources(owner, true, false, true, true);
            return;
        }
        pool.addEnergy(-ENERGY_COST);

        Vec3 from = self.position().add(0, 1, 0);
        Vec3 aim = Vec3.atCenterOf(targetBp).subtract(from);
        Vec3 flat = new Vec3(aim.x, 0, aim.z);
        Vec3 dir = flat.lengthSqr() < 0.01 ? self.getLookAngle().multiply(1, 0, 1).normalize() : flat.normalize();
        Vec3 to = from.add(dir.scale(RANGE));
        Kind kind = kindFor(unitUsing);
        boolean cone = kind == Kind.THORNBURST;

        if (kind == Kind.BROADSIDE) {
            broadside(sl, self, owner, from, dir);
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
            return;
        }

        for (LivingEntity le : cone ? coneHits(self, owner, from, dir) : hits(self, owner, from, to)) {
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            le.hurt(sl.damageSources().indirectMagic(self, self),
                CommanderServerEvents.isCommander(le) ? COMMANDER_DAMAGE : DAMAGE);
            if (cone && le.isAlive())   // Slowness VII, as Crypt Tide roots: the thorns hold what they don't kill
                le.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, ROOT_TICKS, 6));
        }

        if (cone) {
            coneFx(sl, self, from, dir);
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
            return;
        }
        ParticleOptions p = switch (kind) {
            case BLOODSTORM -> ParticleTypes.SWEEP_ATTACK;
            case SOULREAPER -> ParticleTypes.SOUL_FIRE_FLAME;
            case PLAIN -> ParticleTypes.CRIT;
            default -> ParticleTypes.END_ROD;
        };
        for (int i = 1; i <= RANGE * 2; i++) {
            Vec3 at = from.add(dir.scale(i * 0.5));
            sl.sendParticles(p, at.x, at.y, at.z, kind == Kind.BLOODSTORM && i % 3 != 0 ? 0 : 2, 0.15, 0.15, 0.15, 0.01);
            if (kind == Kind.SUNFIRE && i % 2 == 0)
                sl.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
        switch (kind) {
            case BLOODSTORM -> sl.playSound(null, self.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2f, 0.6f);
            case SOULREAPER -> sl.playSound(null, self.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 3f, 0.7f);
            case PLAIN -> sl.playSound(null, self.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 2f, 0.9f);
            default -> sl.playSound(null, self.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2f, 1.4f);
        }
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * Broadside: schedules the {@link #BROADSIDE_SHOTS} impacts down the flat direction {@code dir} from {@code from}
     * (one scheduled task each - a handful per cast, nothing per tick). The impact points are fixed now; the shots land
     * on whoever stands there when they come down. One hit set per volley, so a unit caught by two overlapping bursts
     * is only hurt once. Public for the game test.
     */
    public static void broadside(ServerLevel sl, LivingEntity self, String owner, Vec3 from, Vec3 dir) {
        java.util.Set<LivingEntity> already = new java.util.HashSet<>();
        sl.playSound(null, self.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5f, 0.6f);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, from.x + dir.x, from.y, from.z + dir.z, 8, 0.3, 0.2, 0.3, 0.02);
        for (int i = 1; i <= BROADSIDE_SHOTS; i++) {
            Vec3 at = from.add(dir.scale(i * BROADSIDE_STEP));
            com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents.schedule((i - 1) * BROADSIDE_DELAY + 2,
                () -> broadsideImpact(sl, self, owner, at, already));
        }
    }

    /** One cannonball landing at {@code at}: hurts every not-yet-hit enemy within the burst, smoke and a boom. */
    static int broadsideImpact(ServerLevel sl, LivingEntity self, String owner, Vec3 at, java.util.Set<LivingEntity> already) {
        List<LivingEntity> hit = broadsideHits(self, owner, at);
        hit.removeIf(already::contains);
        already.addAll(hit);
        for (LivingEntity le : hit)
            // indirect magic, not mobAttack (RoN rewrites mob-attack damage) and not an explosion (no terrain, no friends)
            le.hurt(sl.damageSources().indirectMagic(self, self),
                CommanderServerEvents.isCommander(le) ? COMMANDER_DAMAGE : DAMAGE);
        sl.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y - 0.5, at.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y - 0.6, at.z, 6, BROADSIDE_RADIUS * 0.4, 0.15, BROADSIDE_RADIUS * 0.4, 0.02);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.8f, 1.1f);
        return hit.size();
    }

    /** Every hostile unit within {@link #BROADSIDE_RADIUS} (flat, to its near edge) of one impact. Public for the game test. */
    public static List<LivingEntity> broadsideHits(LivingEntity self, String owner, Vec3 at) {
        List<LivingEntity> out = new ArrayList<>();
        if (!(self.level() instanceof ServerLevel sl))
            return out;
        double r = BROADSIDE_RADIUS + 2;
        for (LivingEntity le : com.solegendary.reignofnether.unit.UnitGrid.inBox(sl, at.x - r, at.z - r, at.x + r, at.z + r,
                new ArrayList<>())) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != self.level())
                continue;
            String other = u.getOwnerName();
            if (owner.equals(other) || AlliancesServerEvents.isAllied(owner, other))
                continue;
            if (Math.abs(le.getY() + le.getBbHeight() / 2 - at.y) > 4)
                continue;
            double dx = le.getX() - at.x, dz = le.getZ() - at.z;
            double reach = BROADSIDE_RADIUS + le.getBbWidth() / 2;
            if (dx * dx + dz * dz <= reach * reach)
                out.add(le);
        }
        return out;
    }

    /**
     * Heaven's Judgement: pays {@link #ENERGY_COST} through ManaServerEvents (quiet = no HUD warning, for the bot) or
     * refuses and returns false (nothing paid, nothing scheduled - the caller must not start the cooldown). On success
     * the strike point is fixed now (the aim, pulled in to {@link #RANGE}), the glyph ring pulses during the telegraph
     * and the bolt lands {@link #JUDGEMENT_DELAY} ticks later on whoever stands there then. A handful of scheduled
     * tasks per cast, nothing per tick. Public for the game test and the bot.
     */
    public static boolean judgement(ServerLevel sl, LivingEntity self, String owner, BlockPos aim, boolean quiet) {
        if (aim == null || owner == null)
            return false;
        float power = quiet ? com.solegendary.reignofnether.mana.ManaServerEvents.tryCastQuiet(self, owner, ENERGY_COST)
            : com.solegendary.reignofnether.mana.ManaServerEvents.tryCast(self, owner, ENERGY_COST);
        if (power <= 0)
            return false;
        BlockPos at = com.solegendary.reignofnether.util.MyMath.getXZRangeLimitedBlockPos(self.blockPosition(), aim, RANGE);
        // the clicked block is the ground: the column stands on top of it
        Vec3 centre = new Vec3(at.getX() + 0.5, aim.getY() + 1.0, at.getZ() + 0.5);
        // Overcharge widens the column (x1.5 power -> 3.5 blocks), never deepens the damage: one-shotting T1 is enough
        float radius = power > 1.01f ? JUDGEMENT_RADIUS_OVERCHARGED : JUDGEMENT_RADIUS;
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2f, 1.8f);
        for (int t = 0; t < JUDGEMENT_DELAY; t += 4)
            com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents.schedule(t, () -> glyphRing(sl, centre, radius));
        com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents.schedule(JUDGEMENT_DELAY,
            () -> judgementStrike(sl, self, owner, centre, radius));
        return true;
    }

    /** The telegraph: a ring of gold dust (and a few wax-on glints) on the ground round the strike point. */
    static void glyphRing(ServerLevel sl, Vec3 c, float radius) {
        var gold = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1.0f, 0.82f, 0.25f), 1.4f);
        int n = (int) (radius * 10);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            sl.sendParticles(gold, c.x + Math.cos(a) * radius, c.y + 0.1, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(ParticleTypes.WAX_ON, c.x, c.y + 0.2, c.z, 4, radius * 0.4, 0.05, radius * 0.4, 0.0);
    }

    /** The bolt lands: every enemy in the column takes the D-gun's damage; a bolt from the sky, a flash, a thunderclap. */
    static int judgementStrike(ServerLevel sl, LivingEntity self, String owner, Vec3 c, float radius) {
        List<LivingEntity> hit = judgementHits(self, owner, c, radius);
        for (LivingEntity le : hit)
            // indirect magic, not mobAttack (RoN rewrites mob-attack damage) and not a LightningBolt (fire, friends)
            le.hurt(sl.damageSources().indirectMagic(self, self),
                CommanderServerEvents.isCommander(le) ? COMMANDER_DAMAGE : DAMAGE);
        com.solegendary.reignofnether.barfx.LightningFx.bolt(sl, c.x, c.y + 14, c.z, c.x, c.y, c.z);
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.3, c.z, 24, radius * 0.5, 0.2, radius * 0.5, 0.2);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2.5f, 1.0f);
        sl.playSound(null, BlockPos.containing(c), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.2f, 1.3f);
        return hit.size();
    }

    /** Every hostile unit within {@code radius} (flat, to its near edge) of the column at {@code c}. Public for the game test. */
    public static List<LivingEntity> judgementHits(LivingEntity self, String owner, Vec3 c, float radius) {
        List<LivingEntity> out = new ArrayList<>();
        if (!(self.level() instanceof ServerLevel sl))
            return out;
        double r = radius + 2;
        for (LivingEntity le : com.solegendary.reignofnether.unit.UnitGrid.inBox(sl, c.x - r, c.z - r, c.x + r, c.z + r,
                new ArrayList<>())) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != self.level())
                continue;
            String other = u.getOwnerName();
            if (owner.equals(other) || AlliancesServerEvents.isAllied(owner, other))
                continue;
            // a column from the sky: anything standing in it, flyers over it included (a generous height window)
            if (le.getY() + le.getBbHeight() < c.y - 3 || le.getY() > c.y + 12)
                continue;
            double dx = le.getX() - c.x, dz = le.getZ() - c.z;
            double reach = radius + le.getBbWidth() / 2;
            if (dx * dx + dz * dz <= reach * reach)
                out.add(le);
        }
        return out;
    }

    /** Thornburst's look: thorny leaves fanning out along the cone, and a crunch of brambles. */
    static void coneFx(ServerLevel sl, LivingEntity self, Vec3 from, Vec3 dir) {
        double half = Math.toRadians(CONE_HALF_ANGLE_DEG);
        for (int ray = -2; ray <= 2; ray++) {
            double a = half * ray / 2.0, cos = Math.cos(a), sin = Math.sin(a);
            Vec3 d = new Vec3(dir.x * cos - dir.z * sin, 0, dir.x * sin + dir.z * cos);
            for (int i = 1; i <= (int) CONE_RANGE; i += 2) {
                Vec3 at = from.add(d.scale(i));
                sl.sendParticles(ParticleTypes.COMPOSTER, at.x, at.y - 0.5, at.z, 2, 0.2, 0.2, 0.2, 0.01);
                if (i % 4 == 1)
                    sl.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, at.y, at.z, 1, 0.3, 0.2, 0.3, 0.0);
            }
        }
        sl.playSound(null, self.blockPosition(), SoundEvents.SWEET_BERRY_BUSH_BREAK, SoundSource.HOSTILE, 3f, 0.6f);
        sl.playSound(null, self.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 3f, 0.7f);
    }

    /**
     * Every hostile unit inside Thornburst's cone: within {@link #CONE_RANGE} of {@code from} (measured flat, to the
     * unit's near edge) and within {@link #CONE_HALF_ANGLE_DEG} of the flat unit vector {@code dir}; units the
     * commander is all but standing on always count. Public for the game test.
     */
    public static List<LivingEntity> coneHits(LivingEntity self, String owner, Vec3 from, Vec3 dir) {
        List<LivingEntity> out = new ArrayList<>();
        double cosHalf = Math.cos(Math.toRadians(CONE_HALF_ANGLE_DEG));
        List<LivingEntity> candidates = UnitServerEvents.getAllUnits();
        if (self.level() instanceof ServerLevel sl) {
            double r = CONE_RANGE + 2;
            candidates = com.solegendary.reignofnether.unit.UnitGrid.inBox(sl, from.x - r, from.z - r, from.x + r,
                from.z + r, new ArrayList<>());
        }
        for (LivingEntity le : candidates) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != self.level())
                continue;
            String other = u.getOwnerName();
            if (owner.equals(other) || AlliancesServerEvents.isAllied(owner, other))
                continue;
            if (Math.abs(le.getY() + le.getBbHeight() / 2 - from.y) > 4)
                continue;
            double dx = le.getX() - from.x, dz = le.getZ() - from.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist - le.getBbWidth() / 2 > CONE_RANGE)
                continue;
            if (dist > 1.0 && (dx * dir.x + dz * dir.z) / dist < cosHalf)
                continue;
            out.add(le);
        }
        return out;
    }

    /** Every hostile unit within {@link #HALF_WIDTH} blocks of the segment from-to. Public for the game test. */
    public static List<LivingEntity> hits(LivingEntity self, String owner, Vec3 from, Vec3 to) {
        return hits(self, owner, from, to, HALF_WIDTH);
    }

    /** Same, with a custom half-width (the Sun Colossus' Solar Lance is wider). */
    public static List<LivingEntity> hits(LivingEntity self, String owner, Vec3 from, Vec3 to, float halfWidth) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3 seg = to.subtract(from);
        double len2 = seg.lengthSqr();
        // candidates from the grid cells under the line's bounding box (padded for wide units), not every unit
        List<LivingEntity> candidates = UnitServerEvents.getAllUnits();
        if (self.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            double pad = halfWidth + 2;
            candidates = com.solegendary.reignofnether.unit.UnitGrid.inBox(sl, Math.min(from.x, to.x) - pad,
                Math.min(from.z, to.z) - pad, Math.max(from.x, to.x) + pad, Math.max(from.z, to.z) + pad, new ArrayList<>());
        }
        for (LivingEntity le : candidates) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != self.level())
                continue;
            String other = u.getOwnerName();
            if (owner.equals(other) || AlliancesServerEvents.isAllied(owner, other))
                continue;
            // measured flat (the line hugs the ground over hills), with a generous height window
            if (Math.abs(le.getY() + le.getBbHeight() / 2 - from.y) > 4)
                continue;
            Vec3 c = new Vec3(le.getX(), from.y, le.getZ());
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, c.subtract(from).dot(seg) / len2));
            double reach = halfWidth + le.getBbWidth() / 2;
            if (c.distanceToSqr(from.add(seg.scale(t))) <= reach * reach)
                out.add(le);
        }
        return out;
    }
}
