package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Possession, server side (design/hero_mode.md, section 2): a player takes direct control of one of their team's
 * units - first person or over the shoulder - and the unit leaves RTS control until they let go.
 *
 * How the unit is driven: while possessed, Mob.serverAiStep is cancelled for that mob (PossessionMobMixin calls
 * {@link #drive}), so no goal, navigation or move control touches it; instead the possessor's latest input is
 * written into the same fields the AI would set (speed, zza/xxa/yya, jumping, rotation) and vanilla travel() moves
 * the unit at its own speed with its own collision. An ability hands the AI back for a moment so cast goals that
 * walk into range or channel still run.
 *
 * Every claim goes through {@link #mayPossess}; every input/attack/ability is dropped unless it comes from the
 * unit's current possessor (the client never names a target: the server finds it along the look ray). Orders the
 * RTS side gives a possessed unit become objectives for the possessor instead ({@link #divertOrders}).
 *
 * Cost at 8v8 scale: the mixin's per-mob check is an empty-map early-out when nobody possesses anything, one hash
 * lookup otherwise; the tick loop walks only the possessions (at most one per player).
 */
public final class PossessionServer {
    private PossessionServer() { }

    /** +10% damage while a human drives the unit: control, not stats, is the reward (hero_mode.md, balance 1). */
    public static final float DAMAGE_BONUS = 1.10f;
    /** Attack cooldown multiplier while possessed: a quicker wind-up for a human's aimed swing/shot. */
    public static final float COOLDOWN_MULT = 0.85f;
    /** How long an ability hands control back to the unit's AI (cast goals walk into range, then channel). */
    static final int ABILITY_AI_TICKS = 60;
    /** No input for this long (lag, alt-tab) and the unit stops instead of running on with the last keys. */
    static final int INPUT_STALE_TICKS = 40;
    /** The possessor's (invisible, spectating) player is kept within this of the unit, so its chunks and entities
     * keep streaming to them. */
    static final double PLAYER_LEASH = 16;

    // movement prediction (hero slice 3): the client moves its copy of the unit at once and claims where it got to;
    // the server moves the real unit there through vanilla collision, within what the unit could have walked
    /** Claims arrive every client tick while moving; this long without one and plain input drives the unit again. */
    static final int CLAIM_FRESH_TICKS = 5;
    /** At most this many ticks of movement banked (half a second of lag spike absorbed, no more). */
    static final int MOVE_BUDGET_MAX = 10;
    /** A claim may cover at most this many ticks more than really passed since the last one (claims bunched by the
     * network: two in one server tick, none in the next). */
    public static final int CLAIM_JITTER_TICKS = 2;
    /** Server and claim further apart than this after the move (blocks): tell the client where the unit really is. */
    static final double CORRECT_DIST = 0.35;
    /** A correction further than this is a snap, not a blend (a teleport, a wall the client walked through). */
    static final double SNAP_DIST = 3.0;
    /** At most one correction per this many ticks per possessor (the next claim re-reveals any remaining error). */
    static final int CORRECTION_GAP_TICKS = 4;
    /** A walker in the air longer than this whose claims don't come down is hovering: gravity wins. */
    static final int HOVER_TICKS = 40;
    /**
     * The velocity a claims tick leaves a walker for vanilla travel: a probe straight down, not zero. Travel's move is
     * the last write to onGround each tick and sets it only from a downward collision in that move, so a zero move
     * read every standing possessed unit as airborne - the hover rule then pushed it into the floor and the
     * sprint-jump boost was lost (Oct 27, claude/inbox-possession-onground-oct27.md). On the ground the probe collides
     * and moves nothing; in the air (a client claiming a hover) it drifts this far and onGround stays false, so it is
     * the server's own world that decides. Far under CORRECT_DIST, and the next claim is measured from where it is.
     */
    static final double GROUND_PROBE = 0.01;
    /** {@link #GROUND_PROBE} as the velocity itself: immutable, so one shared instance (no Vec3 per unit per tick). */
    static final Vec3 GROUND_PROBE_VEC = new Vec3(0, -GROUND_PROBE, 0);
    /** A mob's step height: how far a walker may rise in a claim without jumping - and only onto something it stands on. */
    static final double STEP_UP = 0.6;
    /** Vanilla's jump velocity (LivingEntity.getJumpPower), +0.1 per Jump Boost level. */
    static final double JUMP_VELOCITY = 0.42;
    /** A server shove (Possession.serverShove) above this is one the server really gave; it decays by 0.91 a tick. */
    static final float SHOVE_SEEN = 0.05f;
    /** Blocks a knocked-back field artillery's keyless claims may carry it each way on each axis beyond what its shoves
     * reach (a walk's slide just ended, a current, rounding). */
    static final double KNOCK_SLACK = 0.3;
    /** At most this many hit markers per possessor per tick (an area ability can hit a crowd at once). */
    static final int HITS_PER_TICK = 6;

    // "feels like Minecraft" (lovish, Oct 20 2026: "first person mode feels very clunky ... doesn't feel like minecraft
    // pov that allows players to sprint and move around freely")
    /** Sprinting multiplies a ground unit's drive speed by vanilla's sprint modifier (+30%). */
    public static final float SPRINT_MULT = 1.3f;
    /** Vanilla's sprint-jump push along the look (LivingEntity.jumpFromGround), allowed on a sprint-jump's claim. */
    public static final float SPRINT_JUMP_BOOST = 0.2f;
    /**
     * In the air a mob steers with 0.02 x its input, and a unit's input is MoveControl's "keys x speed" (~0.3 long),
     * so a jumping unit had a third of a player's air control: the jump felt like being thrown. Off the ground the
     * input is scaled to keep a player's air-to-ground ratio instead (a player: 0.02 in the air against 0.1 x 0.98 on
     * the ground, i.e. one fifth): air input = keys x 9.8 x speed x drive speed, capped at 1 by vanilla. In the air the
     * unit then settles at its own ground pace, never faster, so the server's walk budget still holds.
     */
    static final float AIR_CONTROL = 9.8f;

    /** What an order or ping on a possessed unit becomes for its possessor. Sent by ordinal: append only. */
    public enum Objective { NONE, MOVE, ATTACK, DESTROY, PROTECT, PING }

    public static final class Possession {
        public final UUID player;
        public final String playerName;
        public final int unitId;
        public final String ownerName;
        final ServerLevel level;
        final boolean headless;   // claimed by server code / a game test: no connection to check or send to
        float forward, strafe, yaw, pitch;
        boolean jump, descend;
        /** The possessor holds sprint (their client's vanilla sprint rules decide it; it only counts moving forward). */
        boolean sprint;
        int ticksSinceInput, attackCooldown, aiTicks, aiAge;
        public Objective objective = Objective.NONE;
        /** Ticks the possessor's input actually drove the unit (the game test reads it to tell "not driven" from
         * "driven but blocked"). */
        public int drivenTicks;
        /** Ticks before the next attack click may fire (a game test's failure message). */
        public int attackCooldownLeft() { return attackCooldown; }
        /** The pitch the last input or click carried (a game test's failure message). */
        public float aimPitch() { return pitch; }
        /** Where a click would aim now: the look ray's block hit (or its end), as attack() finds it (game test). */
        public Vec3 aimPoint(Mob mob) {
            return lookPoint(mob, yaw, pitch, mob instanceof AttackerUnit a ? Math.max(a.getAttackRange(), 4) : 4);
        }

        // ---- movement prediction (hero slice 3, see applyClaim) ----
        /** The newest position the client says its predicted unit reached, and that claim's sequence number. */
        double claimX, claimY, claimZ;
        int claimSeq;
        boolean claimFresh;
        /** The last claim applied (-1: none yet), server ticks since, ticks of movement still allowed (a token bucket
         * refilled one per server tick, so a client can't buy speed by inflating its sequence numbers). */
        int lastSeq = -1, ticksSinceClaim = 99, moveBudget = MOVE_BUDGET_MAX;
        /** Server ticks the unit has been off the ground (jump rise vs. hovering). */
        int airTicks;
        /**
         * The Y it last stood on (on the ground, climbing or swimming; where it was possessed until then) and whether it
         * left that ground launched - holding jump, carried by a server shove, or climbing / swimming: a claim never
         * takes a walker higher than a jump's apex plus a step above it, or, not launched, higher than a step it ends up
         * standing on. Each landing used to refill the grounded climb allowance once the unit read on-ground (Mode A
         * review of #308, round 1: ~4.6 blocks in 7 ticks).
         */
        double groundY = Double.NaN;
        boolean jumpedFromGround;
        /**
         * Some input packet since the last grounded claim held jump. Only the newest claim is applied, and network
         * jitter can bunch a tap-jump's two packets (jump on, then off) into one server tick: the jump still happened
         * (#308 review round 2).
         */
        boolean jumpSinceGround;
        /** Extra blocks per tick a claim may cover while a shove (knockback, explosion) carries the unit. */
        float shoveAllowance;
        /** The horizontal speed of the last shove the server itself gave the unit (knockback, a blast, a push), decaying at air drag: never from the client's flags. */
        float serverShove;
        /** The last drive ran the claims branch (which leaves only the ground probe): only then is this tick's velocity a shove. */
        boolean claimsLastTick;
        /** How far the server's shoves since serverShove last died out may still carry the unit east, west, south and
         * north (see applyClaim's knockback cap). */
        double knockEast, knockWest, knockSouth, knockNorth;
        int correctionCooldown;
        /** The server is moving the unit itself (an ability's AI window, a leap arc): claims are ignored. */
        boolean serverMoves;
        /** Claims applied as sent, and claims the server had to correct (read by the game test). */
        public int acceptedClaims, corrections;
        int hitsThisTick;

        Possession(UUID player, String playerName, int unitId, String ownerName, ServerLevel level, boolean headless) {
            this.player = player;
            this.playerName = playerName;
            this.unitId = unitId;
            this.ownerName = ownerName;
            this.level = level;
            this.headless = headless;
        }
    }

    private static final Map<Integer, Possession> BY_UNIT = new ConcurrentHashMap<>();
    private static final Map<UUID, Possession> BY_PLAYER = new ConcurrentHashMap<>();

    // ---- queries ----

    public static boolean isPossessed(Entity e) {
        return e != null && !BY_UNIT.isEmpty() && BY_UNIT.containsKey(e.getId());
    }

    public static boolean isPossessed(int unitId) {
        return !BY_UNIT.isEmpty() && BY_UNIT.containsKey(unitId);
    }

    /**
     * Whether {@code e} is possessed and its possessor holds a walk key (forward/back or strafe) with fresh input: a
     * possessed unit walked by hand. A knockback while the keys rest is not a walk (lovish, Oct 27: only walking costs
     * the artillery its set-up).
     */
    public static boolean keysHeld(Entity e) {
        if (e == null || BY_UNIT.isEmpty())
            return false;
        Possession p = BY_UNIT.get(e.getId());
        return p != null && p.ticksSinceInput <= INPUT_STALE_TICKS && (p.forward != 0 || p.strafe != 0);
    }

    /**
     * Whether {@code e} is possessed and its client's position claims move it with no shove the server gave it (no
     * knockback, blast or push of the server's own in the last few ticks - the client's sprint and jump flags do not
     * count, round-5 review of #222): the move is then the client's own doing, not a hit's. False for units the server
     * moves itself (AI, keys, an ability).
     */
    public static boolean claimedWithoutShove(Entity e) {
        if (e == null || BY_UNIT.isEmpty())
            return false;
        Possession p = BY_UNIT.get(e.getId());
        return p != null && !p.serverMoves && (p.claimFresh || p.ticksSinceClaim <= CLAIM_FRESH_TICKS)
                && p.serverShove <= SHOVE_SEEN;
    }

    /** The possession {@code player} holds, or null. */
    public static Possession of(UUID player) {
        return player == null ? null : BY_PLAYER.get(player);
    }

    /** The possession of this unit, or null. */
    public static Possession ofUnit(int unitId) {
        return BY_UNIT.get(unitId);
    }

    /**
     * Null when {@code name} may possess {@code e}, else why not (shown to them). The owner, an ally with control
     * or a sandbox player (PacketAuth.mayActFor); a Hero-role player on the owner's team for a hero unit, or for any
     * unit of an owner who lends theirs. Never an enemy unit, a dead or garrisoned one, or one somebody else holds.
     */
    public static String mayPossess(String name, Entity e) {
        if (!(e instanceof Unit unit) || !(e instanceof Mob mob))
            return "That can't be possessed.";
        if (!mob.isAlive() || mob.isRemoved())
            return "That unit is dead.";
        Possession other = BY_UNIT.get(e.getId());
        if (other != null && !other.playerName.equals(name))
            return other.playerName + " is already controlling that unit.";
        if (unit.isGarrisoned())
            return "Ungarrison the unit first.";
        String owner = unit.getOwnerName();
        if (owner == null || owner.isBlank())
            return "Only your team's units can be possessed.";
        if (PacketAuth.mayActFor(name, owner))
            return null;
        if (HeroRoles.servesOwner(name, owner) && (e instanceof HeroUnit || HeroRoles.lends(owner)))
            return null;
        return "That unit isn't yours to possess.";
    }

    // ---- claim / release ----

    /** A connected player asks to possess {@code unitId} (-1: their team's hero). Tells them why when refused. */
    public static void requestPossess(ServerPlayer sp, int unitId) {
        String why = claim(sp.serverLevel(), sp.getName().getString(), sp.getUUID(), unitId, sp instanceof FakePlayer);
        if (why != null) {
            sp.sendSystemMessage(Component.literal(why));
            return;
        }
        Possession p = BY_PLAYER.get(sp.getUUID());
        if (p == null)
            return;
        Entity e = p.level.getEntity(p.unitId);
        if (e != null && sp.level() == e.level() && sp.distanceToSqr(e) > PLAYER_LEASH * PLAYER_LEASH)
            sp.teleportTo(e.getX(), e.getY() + 12, e.getZ());
        PossessionClientboundPacket.start(sp, p.unitId);
    }

    /**
     * Claims {@code unitId} (or, for -1, the strongest hero of the claimant's team that nobody holds) for
     * {@code name}. Null on success, else the reason. A previous possession of theirs is released first. The unit's
     * orders are dropped so it doesn't resume them when let go.
     */
    public static String claim(ServerLevel level, String name, UUID uuid, int unitId, boolean headless) {
        if (level == null || name == null || uuid == null)
            return "Nobody to possess with.";
        Entity e = unitId < 0 ? findTeamHero(level, name) : level.getEntity(unitId);
        if (e == null)
            return unitId < 0 ? "Your team has no hero on the field - train or revive one at the capitol."
                : "That unit is gone.";
        String why = mayPossess(name, e);
        if (why != null)
            return why;
        Possession old = BY_PLAYER.get(uuid);
        if (old != null && old.unitId == e.getId())
            return null;   // already theirs
        if (old != null)
            release(uuid, null);
        Mob mob = (Mob) e;
        Unit unit = (Unit) e;
        Possession p = new Possession(uuid, name, e.getId(), unit.getOwnerName(), level, headless);
        p.yaw = mob.getYRot();
        p.pitch = 0;
        BY_UNIT.put(p.unitId, p);
        BY_PLAYER.put(uuid, p);
        dropOrders(mob, unit);
        PossessionClientboundPacket.broadcastState(p.unitId, name);
        com.solegendary.reignofnether.sounds.Sfx.POSSESS_ENTER.play(level, e.position());
        ReignOfNether.LOGGER.info("[Possession] {} took {} ({})", name, e.getName().getString(), unit.getOwnerName());
        return null;
    }

    /** Lets go of whatever {@code player} possesses (null reason: they chose to). True if they held something. */
    public static boolean release(UUID player, String reason) {
        Possession p = player == null ? null : BY_PLAYER.remove(player);
        if (p == null)
            return false;
        BY_UNIT.remove(p.unitId, p);
        Entity e = p.level.getEntity(p.unitId);
        if (e instanceof Mob mob) {
            mob.setZza(0);
            mob.setXxa(0);
            mob.setYya(0);
            mob.setJumping(false);
            setSprintFlag(mob, false);
            if (mob.isAlive() && e instanceof Unit unit)
                dropOrders(mob, unit);   // an ordinary idle RTS unit again (an ability's AI window may have left a goal)
        }
        PossessionClientboundPacket.broadcastState(p.unitId, "");
        if (e != null)
            com.solegendary.reignofnether.sounds.Sfx.POSSESS_EXIT.play(p.level, e.position());
        if (!p.headless) {
            MinecraftServer server = p.level.getServer();
            ServerPlayer sp = server.getPlayerList().getPlayer(player);
            if (sp != null)
                PossessionClientboundPacket.end(sp, p.unitId, reason == null ? "" : reason);
        }
        return true;
    }

    /**
     * Everything that would walk the unit on its own: its goals' targets (Unit.fullResetBehaviours - the instance
     * resetBehaviours() alone is a no-op for most units, so a possessed unit used to keep its last move order and
     * walk it during an ability's AI window and again after release), patrol and guard duty, and shift-queued orders
     * (CommandQueues starts the next one whenever the unit looks idle - and a possessed unit always does).
     */
    static void dropOrders(Mob mob, Unit unit) {
        Unit.fullResetBehaviours(unit);
        com.solegendary.reignofnether.unit.PatrolServerEvents.clear(mob.getId());
        com.solegendary.reignofnether.unit.GuardServerEvents.clear(mob.getId());
        com.solegendary.reignofnether.unit.command.CommandQueues.clear(mob.getId());
        mob.getNavigation().stop();
        mob.setTarget(null);
    }

    static Entity findTeamHero(ServerLevel level, String name) {
        List<String> owners = new ArrayList<>(HeroRoles.commandersOf(name));
        owners.add(name);
        LivingEntity best = null;
        int bestLevel = -1;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof HeroUnit hero) || le.level() != level || !le.isAlive())
                continue;
            if (!owners.contains(hero.getOwnerName()))
                continue;
            Possession other = BY_UNIT.get(le.getId());
            if (other != null && !other.playerName.equals(name))
                continue;
            if (hero.getHeroLevel() > bestLevel) {
                bestLevel = hero.getHeroLevel();
                best = le;
            }
        }
        return best;
    }

    // ---- input (PossessionServerboundPacket) ----

    /**
     * The possessor's movement keys and view. Dropped (false) unless {@code player} holds {@code unitId}. Values
     * are clamped: move -1..1, pitch -90..90; yaw wrapped.
     */
    public static boolean applyInput(UUID player, int unitId, float forward, float strafe, float yaw, float pitch,
                                     boolean jump, boolean descend) {
        return applyInput(player, unitId, forward, strafe, yaw, pitch, jump, descend, false);
    }

    /** {@link #applyInput} with the sprint key: it only ever counts while moving forward ({@link #sprints}). */
    public static boolean applyInput(UUID player, int unitId, float forward, float strafe, float yaw, float pitch,
                                     boolean jump, boolean descend, boolean sprint) {
        Possession p = player == null ? null : BY_PLAYER.get(player);
        if (p == null || p.unitId != unitId)
            return false;
        p.forward = Mth.clamp(forward, -1f, 1f);
        p.strafe = Mth.clamp(strafe, -1f, 1f);
        setLook(p, yaw, pitch);
        p.jump = jump;
        p.jumpSinceGround |= jump;
        p.descend = descend;
        p.sprint = sprint;
        p.ticksSinceInput = 0;
        return true;
    }

    /**
     * Input plus the client's predicted position after it (hero slice 3): {@code seq} is the client tick it was taken
     * on. Only the newest claim is kept; it is applied on the unit's next tick ({@link #applyClaim}). Dropped (false)
     * unless {@code player} holds {@code unitId}; a non-finite position is input only.
     */
    public static boolean applyMove(UUID player, int unitId, float forward, float strafe, float yaw, float pitch,
                                    boolean jump, boolean descend, double x, double y, double z, int seq) {
        return applyMove(player, unitId, forward, strafe, yaw, pitch, jump, descend, false, x, y, z, seq);
    }

    /** {@link #applyMove} with the sprint key (the claim may then cover a sprint's distance, no more). */
    public static boolean applyMove(UUID player, int unitId, float forward, float strafe, float yaw, float pitch,
                                    boolean jump, boolean descend, boolean sprint, double x, double y, double z, int seq) {
        if (!applyInput(player, unitId, forward, strafe, yaw, pitch, jump, descend, sprint))
            return false;
        Possession p = BY_PLAYER.get(player);
        if (p == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            return true;
        // old or replayed claims are ignored (packets can't reorder on one connection, but a client could lie)
        if (p.lastSeq >= 0 && seq <= p.lastSeq)
            return true;
        if (p.claimFresh && seq <= p.claimSeq)
            return true;
        p.claimX = x;
        p.claimY = y;
        p.claimZ = z;
        p.claimSeq = seq;
        p.claimFresh = true;
        return true;
    }

    /** Most a walker covers per tick at movement speed {@code speed}: vanilla's ground speed limit is about
     * 2.86 x speed^2 (MoveControl's convention: input and friction-scaled acceleration both scale with speed) on any
     * block friction; 3.2 x + a little keeps a quarter of headroom for rounding and server lag. */
    static double walkPerTick(double speed) {
        return walkPerTick(speed, false);
    }

    /** {@link #walkPerTick(double)} while sprinting: the drive speed (one of the two speed factors) is 1.3x. */
    public static double walkPerTick(double speed, boolean sprinting) {
        return 3.2 * speed * speed * (sprinting ? SPRINT_MULT : 1) + 0.06;
    }

    /**
     * How high {@code mob}'s jump reaches: vanilla's rise from {@link #JUMP_VELOCITY} (+0.1 per Jump Boost level), each
     * tick moving by the velocity, then -0.08 gravity and x0.98 drag - 1.252 blocks without the effect.
     */
    static double jumpApex(Mob mob) {
        MobEffectInstance boost = mob.getEffect(MobEffects.JUMP);
        double v = JUMP_VELOCITY + (boost != null ? 0.1 * (boost.getAmplifier() + 1) : 0);
        double h = 0;
        while (v > 0) {
            h += v;
            v = (v - 0.08) * 0.98;
        }
        return h;
    }

    /** Whether sprint applies: the key is held, the unit is moving forward and it walks (flyers have no sprint). */
    static boolean sprints(Mob mob, float forward, boolean sprint) {
        return sprint && forward > 0 && !isFlyer(mob);
    }

    static boolean isFlyer(Mob mob) {
        return mob.isNoGravity() || (mob instanceof Unit u && u.isFlyingUnit());
    }

    /** Vanilla's sprinting flag (entity flag 3) without LivingEntity.setSprinting's speed modifier; only on change. */
    static void setSprintFlag(Mob mob, boolean on) {
        if (mob.isSprinting() != on)
            ((com.solegendary.reignofnether.mixin.EntitySharedFlagInvoker) mob).reignofnether$setSharedFlag(3, on);
    }

    /**
     * The keys into the fields the AI would set - the one place both sides do it: PossessionServer.drive on the
     * server's copy, PossessionPredictionClient.preTick on the client's predicted copy, so both compute the same
     * motion. On the ground the input is MoveControl's "keys x speed" with the speed as the drive (the unit walks at
     * its RTS pace, sprinting 1.3x that); in the air the input is widened to a player's air control (AIR_CONTROL).
     */
    public static void applyKeys(Mob mob, float forward, float strafe, boolean jump, boolean descend, boolean sprint,
                                 float yaw, float pitch) {
        float f = forward, s = strafe;
        if (f != 0 && s != 0) {
            f *= 0.7071f;
            s *= 0.7071f;
        }
        boolean flyer = isFlyer(mob);
        boolean run = sprint && forward > 0 && !flyer;
        setSprintFlag(mob, run);
        float speed = (float) mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float drive = run ? speed * SPRINT_MULT : speed;
        mob.setYRot(yaw);
        mob.setXRot(pitch);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setSpeed(drive);   // (Mob.setSpeed also writes zza: the input below replaces it)
        float in = speed;
        if (!flyer && !mob.onGround() && !mob.isInWater() && !mob.isInLava() && !mob.onClimbable())
            in = Math.max(speed, AIR_CONTROL * speed * drive);
        mob.setZza(f * in);
        mob.setXxa(s * in);
        if (flyer) {
            float up = (jump ? 1 : 0) - (descend ? 1 : 0);
            up -= Mth.sin(pitch * Mth.DEG_TO_RAD) * f;   // fly where you look
            mob.setYya(Mth.clamp(up, -1f, 1f) * speed);
            mob.setJumping(false);
        } else {
            mob.setYya(0);
            mob.setJumping(jump);
        }
    }

    /** Flyers' travel differs per unit; a generous bound that still stops a teleport. */
    static double flyPerTick(double speed) {
        return Math.min(2.0, 0.35 + 2.5 * speed);
    }

    /**
     * Applies the newest movement claim (from drive, on the unit's own tick): the unit is moved toward the claimed
     * position through vanilla collision (Entity.move: walls, steps, fall damage), never further than it could have
     * gone since the last claim - by its own speed, plus any shove it is riding, at most the banked move budget - and
     * never up into the air beyond a jump unless it flies. If the unit ends up somewhere other than the claim (an
     * impossible claim, a wall the client didn't have, a server-side teleport) the possessor is told where it really
     * is (SYNC) and their client re-bases its prediction there. An honest client sees no correction at all.
     */
    static void applyClaim(Possession p, Mob mob, boolean flyer) {
        p.claimFresh = false;
        int ticks = p.lastSeq < 0 ? 1 : Mth.clamp(p.claimSeq - p.lastSeq, 1, MOVE_BUDGET_MAX);
        ticks = Math.min(ticks, Math.max(1, p.moveBudget));
        // never more movement than the server ticks that really passed since the last applied claim (plus a little
        // network jitter): an honest client claiming every tick keeps the bucket full, and the bucket alone let it
        // bank MOVE_BUDGET_MAX ticks - one claim with seq + 10 then jumped ~3-4 blocks at once (PR #31 CI, 3.20
        // blocks with sprint). A lag spike is still absorbed: no claim arrives during it, so the elapsed ticks grow.
        ticks = Math.min(ticks, p.ticksSinceClaim + CLAIM_JITTER_TICKS);
        p.moveBudget = Math.max(0, p.moveBudget - ticks);
        p.lastSeq = p.claimSeq;
        p.ticksSinceClaim = 0;

        double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        boolean sprinting = sprints(mob, p.forward, p.sprint);
        // jump held now, or on any packet since the last grounded claim (a tap-jump bunched by jitter)
        boolean jumped = p.jump || p.jumpSinceGround;
        // a sprint-jump's first tick carries vanilla's 0.2 push on top of the run (jumpFromGround); it rides out like
        // a shove (the allowance decays at air drag)
        if (sprinting && jumped && p.airTicks <= 1)
            p.shoveAllowance = Math.max(p.shoveAllowance, SPRINT_JUMP_BOOST);
        double perTick = (flyer ? flyPerTick(speed) : walkPerTick(speed, sprinting)) + p.shoveAllowance;
        double dx = p.claimX - mob.getX(), dy = p.claimY - mob.getY(), dz = p.claimZ - mob.getZ();
        boolean impossible = false;
        double maxH = perTick * ticks;
        double h = Math.sqrt(dx * dx + dz * dz);
        if (h > maxH) {
            dx *= maxH / h;
            dz *= maxH / h;
            impossible = true;
        }
        // knocked back with the keys at rest, field artillery keeps its set-up (SetUp: claimedWithoutShove is false),
        // so the claims may carry it only as far as its shoves do, axis by axis (lovish's option A, the #222
        // follow-up): each shove adds what it can carry the unit east or west and south or north (its own velocity at
        // air drag, see drive), and the claims spend it. Vanilla collision works per axis and only ever takes motion
        // away, so an honest prediction - lagging, hit twice from two sides, sliding along a wall - stays inside; a
        // claim that walks the crew somewhere the shoves never pushed it does not. Walking with the keys costs the
        // set-up anyway, so it is not capped
        boolean knockCap = !flyer && p.forward == 0 && p.strafe == 0 && p.serverShove > SHOVE_SEEN
                && com.solegendary.reignofnether.unit.SetUp.needsSetUp(mob);
        if (knockCap) {
            double cx = Mth.clamp(dx, -p.knockWest, p.knockEast), cz = Mth.clamp(dz, -p.knockNorth, p.knockSouth);
            if (cx != dx || cz != dz) {
                dx = cx;
                dz = cz;
                impossible = true;
            }
        }
        double fromX = mob.getX(), fromZ = mob.getZ();
        double maxUp, maxDown;
        boolean climbing = false;
        if (flyer) {
            maxUp = maxDown = perTick * ticks;
        } else {
            climbing = mob.onClimbable() || mob.isInWater() || mob.isInLava();
            // on the ground: a step or a jump's first tick (its +0.6 only with jump held); rising: the rest of a jump;
            // later only a little (the claims path leaves travel no gravity of its own, just the GROUND_PROBE drift)
            boolean grounded = p.airTicks == 0;
            // knockback lifts a unit without the jump key (vanilla: +0.4 up): a shove the server itself gave it counts
            // as a launch too, so an honest knocked-back client is not put back on the floor
            if (grounded) {
                p.jumpedFromGround = jumped || p.serverShove > SHOVE_SEEN;
                p.jumpSinceGround = false;
            }
            // climbing and swimming count as launched too: leaving a ladder at its top or climbing out of water puts
            // the unit above its last ground (the top rung, the water) with nothing under it for a tick or two, and
            // it was put back down (#308 review round 2). The rise above that ground is still capped below.
            if (climbing)
                p.jumpedFromGround = true;
            double upPerTick = climbing ? 0.3 : grounded ? 0.6 : p.airTicks < 7 ? 0.45 : 0.08;
            maxUp = upPerTick * ticks + (grounded && jumped ? 0.6 : 0);
            if (!climbing && !Double.isNaN(p.groundY)) {
                // the per-tick bounds alone refill on every landing: the rise above the last ground is bounded too
                double rise = p.jumpedFromGround ? jumpApex(mob) + STEP_UP : STEP_UP;
                maxUp = Math.min(maxUp, Math.max(0, p.groundY + rise - mob.getY()));
            }
            maxDown = 4.0 * ticks + 0.5;   // terminal velocity is ~3.9 a tick
            if (!climbing && p.airTicks > HOVER_TICKS && dy > -0.05 * ticks) {
                dy = Math.min(dy, -0.0784 * ticks);   // hovering walker: it falls
                impossible = true;
            }
        }
        if (dy > maxUp) {
            dy = maxUp;
            impossible = true;
        } else if (dy < -maxDown) {
            dy = -maxDown;
            impossible = true;
        }
        mob.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(dx, dy, dz));
        if (knockCap) {   // the shoves' reach the move spent (what collision let through)
            double mx = mob.getX() - fromX, mz = mob.getZ() - fromZ;
            p.knockEast = Math.max(0, p.knockEast - Math.max(0, mx));
            p.knockWest = Math.max(0, p.knockWest + Math.min(0, mx));
            p.knockSouth = Math.max(0, p.knockSouth - Math.max(0, mz));
            p.knockNorth = Math.max(0, p.knockNorth + Math.min(0, mz));
        }
        if (!flyer && !climbing && !p.jumpedFromGround && !Double.isNaN(p.groundY) && mob.getY() > p.groundY + 1.0e-3
                && mob.level().noCollision(mob, mob.getBoundingBox().move(0, -2 * GROUND_PROBE, 0))) {
            // up without jumping is a step, and a step ends standing on something: a client claiming its unit
            // floats up off the ground is put back down on it
            mob.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, p.groundY - mob.getY(), 0));
            impossible = true;
        }
        double ex = p.claimX - mob.getX(), ey = p.claimY - mob.getY(), ez = p.claimZ - mob.getZ();
        double err2 = ex * ex + ey * ey + ez * ez;
        if (!impossible && err2 <= CORRECT_DIST * CORRECT_DIST) {
            p.acceptedClaims++;
            return;
        }
        p.corrections++;
        if (p.correctionCooldown > 0)
            return;
        p.correctionCooldown = CORRECTION_GAP_TICKS;
        sendSync(p, mob, err2 > SNAP_DIST * SNAP_DIST ? PossessionClientboundPacket.SYNC_SNAP : 0);
    }

    static void sendSync(Possession p, Mob mob, int flags) {
        if (p.headless)
            return;
        ServerPlayer sp = p.level.getServer().getPlayerList().getPlayer(p.player);
        if (sp != null)
            PossessionClientboundPacket.sync(sp, p.unitId, p.lastSeq, mob.getX(), mob.getY(), mob.getZ(), flags);
    }

    /** A hero flying a scripted leap / dash / grapple arc (HeroCore): the server moves it, not the possessor. */
    static boolean scriptedMotion(Mob mob) {
        if (!(mob instanceof FactionHero fh))
            return false;
        HeroCore c = fh.heroCore();
        return c != null && c.inMotion();
    }

    static void setLook(Possession p, float yaw, float pitch) {
        if (Float.isFinite(yaw))
            p.yaw = Mth.wrapDegrees(yaw);
        if (Float.isFinite(pitch))
            p.pitch = Mth.clamp(pitch, -90f, 90f);
    }

    /**
     * Left click: the unit's own attack at whatever is under the crosshair. Melee strikes a target in reach; a
     * ranged unit fires its projectile at the target, or at the block it aims at. The server finds the target along
     * the look ray (the client never names one) and enforces the attack cooldown. False if not the possessor.
     */
    public static boolean attack(UUID player, int unitId, float yaw, float pitch) {
        Possession p = player == null ? null : BY_PLAYER.get(player);
        if (p == null || p.unitId != unitId)
            return false;
        setLook(p, yaw, pitch);
        // attacking ends a sprint, as it does for a player (the client stops it too; its next MOVE says so)
        p.sprint = false;
        if (p.level.getEntity(unitId) instanceof Mob m)
            setSprintFlag(m, false);
        if (p.attackCooldown > 0)
            return true;
        if (!(p.level.getEntity(unitId) instanceof Mob mob) || !(mob instanceof AttackerUnit attacker))
            return true;
        // field artillery still setting up holds the shot, and the click costs no cooldown (SetUp, lovish Oct 24)
        if (!com.solegendary.reignofnether.unit.SetUp.isSetUp(mob))
            return true;
        // a venting machine can't fire (brass/Heat): hold the shot the same way, the click costs no cooldown
        if (mob instanceof com.solegendary.reignofnether.brass.Machine machine
                && !com.solegendary.reignofnether.brass.Heat.canFire(machine))
            return true;
        boolean ranged = mob instanceof RangedAttackerUnit;
        float range = attacker.getAttackRange();
        double reach = ranged ? Math.max(range, 4) : Math.max(range, 1.5f) + mob.getBbWidth() * 0.5 + 1.0;
        LivingEntity target = lookTarget(mob, p.yaw, p.pitch, reach, true);
        if (AttackerUnit.refusesTarget(attacker, target))
            target = null;   // cannot hit air: no flyer as its target, the shot goes at the spot it aims at
        Vec3 at = target != null ? target.position() : ranged ? lookPoint(mob, p.yaw, p.pitch, reach) : null;
        // inside the dead zone the artillery's own fire method refuses the shell: hold the shot, and like the set-up
        // the click costs no cooldown (one refused click burnt the Bombard's 68 ticks; the set-up reds on main, Oct 28)
        if (at != null && mob instanceof com.solegendary.reignofnether.unit.interfaces.IndirectFire lob
                && lob.getMinRange() > 0) {
            double dx = at.x - mob.getX(), dz = at.z - mob.getZ();
            if (dx * dx + dz * dz < lob.getMinRange() * lob.getMinRange())
                return true;
        }
        mob.setYRot(p.yaw);
        mob.setYHeadRot(p.yaw);
        mob.swing(InteractionHand.MAIN_HAND);
        if (ranged) {
            RangedAttackerUnit shooter = (RangedAttackerUnit) mob;
            // the same "velocity" the unit's own goal passes: a bow's draw power (a full draw: 1) for bow users
            // (UnitBowAttackGoal), 20 for everything else (UnitRangedAttackGoal, tridents)
            float velocity = mob.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem ? 1f : 20f;
            if (target != null) {
                mob.setTarget(target);
                shooter.performUnitRangedAttack(target, velocity);
            } else {
                shooter.performUnitRangedAttack(at.x, at.y, at.z, velocity);
            }
        } else if (target != null) {
            // the unit's own held blow: a keyed thrust lands at its hit key under a player's hand too (the close camera
            // shows an early flinch the most); letting go of the unit drops a blow still held
            com.solegendary.reignofnether.unit.HeldBlow.strike(mob, target, Math.max(reach, mob.distanceTo(target)),
                () -> isPossessed(mob));
        }
        p.attackCooldown = Math.max(4, Math.round(attacker.getAttackCooldown() * COOLDOWN_MULT));
        return true;
    }

    /**
     * Keys 1-4: the unit's first four abilities, cast at the crosshair (an entity if the ability takes one and one
     * is aimed at, else the block). Goes through the ordinary order path (UnitActionItem: cooldowns, mana, seals)
     * and hands the AI back briefly so the cast goal runs. False if not the possessor.
     */
    public static boolean ability(UUID player, int unitId, int slot, float yaw, float pitch) {
        Possession p = player == null ? null : BY_PLAYER.get(player);
        if (p == null || p.unitId != unitId)
            return false;
        setLook(p, yaw, pitch);
        if (slot < 0 || slot > 3 || !(p.level.getEntity(unitId) instanceof Mob mob) || !(mob instanceof Unit unit))
            return true;
        Ability ability = abilityInSlot(unit, slot);
        if (ability == null)
            return true;
        // cast feedback (hero slice 3): say at once why a key did nothing - the same checks the order path makes, so
        // a refused key no longer hands the AI back for a cast that can't happen
        int why = castCheck(unit, ability, p);
        sendCast(p, slot, why);
        if (why != PossessionClientboundPacket.CAST_OK)
            return true;
        double reach = Math.max(ability.range, 4);
        LivingEntity target = ability.canTargetEntities ? lookTarget(mob, p.yaw, p.pitch, reach, false) : null;
        BlockPos bp = ability.range <= 0 ? mob.blockPosition() : BlockPos.containing(lookPoint(mob, p.yaw, p.pitch, reach));
        UnitServerEvents.addActionItem(unit.getOwnerName(), ability.action, target != null ? target.getId() : -1,
            new int[] { unitId }, bp, new BlockPos(0, 0, 0));
        p.aiTicks = ABILITY_AI_TICKS;
        p.aiAge = 0;
        return true;
    }

    /** Why key {@code ability} can't be cast now (PossessionClientboundPacket.CAST_*), CAST_OK when it can. */
    static int castCheck(Unit unit, Ability ability, Possession p) {
        if (ability instanceof com.solegendary.reignofnether.ability.HeroAbility ha && unit instanceof HeroUnit hero) {
            if (ha.getRank(hero) <= 0)
                return PossessionClientboundPacket.CAST_RANK;
            if (!ability.isOffCooldown(unit))
                return PossessionClientboundPacket.CAST_COOLDOWN;
            if (hero.getMana() < ha.manaCost)
                return PossessionClientboundPacket.CAST_MANA;
        } else if (!ability.isOffCooldown(unit)) {
            return PossessionClientboundPacket.CAST_COOLDOWN;
        }
        if (p.aiTicks > 0 && unit.isCasting())
            return PossessionClientboundPacket.CAST_BUSY;
        return PossessionClientboundPacket.CAST_OK;
    }

    static void sendCast(Possession p, int slot, int result) {
        if (p.headless)
            return;
        ServerPlayer sp = p.level.getServer().getPlayerList().getPlayer(p.player);
        if (sp != null)
            PossessionClientboundPacket.cast(sp, p.unitId, slot, result);
    }

    /**
     * Alt + 1-4 (hero slice 2): the possessor spends one of the hero's ability points on that slot's ability - the same
     * rules as the commander's rank-up menu (HeroAbility.rankUp: a point, the level requirement, rank under 3), so a
     * Hero-role player grows the hero they drive. False if not the possessor.
     */
    public static boolean rankUp(UUID player, int unitId, int slot) {
        Possession p = player == null ? null : BY_PLAYER.get(player);
        if (p == null || p.unitId != unitId)
            return false;
        if (slot < 0 || slot > 3 || !(p.level.getEntity(unitId) instanceof Unit unit)
                || !(unit instanceof com.solegendary.reignofnether.unit.interfaces.HeroUnit hero))
            return true;
        if (abilityInSlot(unit, slot) instanceof com.solegendary.reignofnether.ability.HeroAbility ha)
            ha.rankUp(hero);
        return true;
    }

    /** The {@code slot}th ability (0-3) a possessed unit casts on keys 1-4: its abilities with an order, in order. */
    public static Ability abilityInSlot(Unit unit, int slot) {
        if (unit.getAbilities() == null)
            return null;
        int i = 0;
        for (Ability a : unit.getAbilities().get()) {
            if (a == null || a.action == null)
                continue;
            if (i++ == slot)
                return a;
        }
        return null;
    }

    /** The nearest living thing along the look ray within {@code reach}, stopped by blocks. Hostile only on request. */
    static LivingEntity lookTarget(Mob mob, float yaw, float pitch, double reach, boolean hostileOnly) {
        Vec3 eye = mob.getEyePosition();
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 end = eye.add(dir.scale(reach));
        HitResult block = mob.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        if (block.getType() != HitResult.Type.MISS)
            end = block.getLocation();
        AABB search = new AABB(eye, end).inflate(1.0);
        String owner = mob instanceof Unit u ? u.getOwnerName() : "";
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity le : mob.level().getEntitiesOfClass(LivingEntity.class, search)) {
            if (le == mob || !le.isAlive() || le instanceof Player)
                continue;
            if (hostileOnly && le instanceof Unit u && (owner.equals(u.getOwnerName())
                    || AlliancesServerEvents.isAllied(owner, u.getOwnerName())))
                continue;
            // a little forgiving: a human aims at a moving mob with a server round trip of delay
            var hit = le.getBoundingBox().inflate(0.4).clip(eye, end);
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bestD) {
                    bestD = d;
                    best = le;
                }
            }
        }
        return best;
    }

    /** Where the look ray meets a block within {@code reach} (or its end in the air). */
    static Vec3 lookPoint(Mob mob, float yaw, float pitch, double reach) {
        Vec3 eye = mob.getEyePosition();
        Vec3 end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(reach));
        HitResult block = mob.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        return block.getType() == HitResult.Type.MISS ? end : block.getLocation();
    }

    // ---- the mixin hook ----

    /**
     * Called at the head of Mob.serverAiStep (PossessionMobMixin). True: this mob is possessed - the AI is skipped
     * and the possessor's input is applied instead. False for every other mob (and while an ability's AI window runs).
     */
    public static boolean drive(Mob mob) {
        if (BY_UNIT.isEmpty())
            return false;
        Possession p = BY_UNIT.get(mob.getId());
        if (p == null || p.level != mob.level())
            return false;
        boolean claimsBefore = p.claimsLastTick;
        p.claimsLastTick = false;
        // a shove dies out at air drag every tick, whatever moves the unit meanwhile (keys, an ability's AI window)
        p.serverShove *= 0.91f;
        // the server moving the unit itself starts / ends: the possessor's client stops / resumes predicting
        boolean serverMoves = p.aiTicks > 0 || scriptedMotion(mob);
        if (serverMoves != p.serverMoves) {
            p.serverMoves = serverMoves;
            p.claimFresh = false;
            sendSync(p, mob, serverMoves ? PossessionClientboundPacket.SYNC_SUSPEND : PossessionClientboundPacket.SYNC_SNAP);
        }
        if (p.aiTicks > 0) {
            setSprintFlag(mob, false);   // the cast goal walks; it does not sprint-jump
            return false;
        }
        boolean stale = p.ticksSinceInput > INPUT_STALE_TICKS;
        p.drivenTicks++;
        boolean flyer = isFlyer(mob);
        p.moveBudget = Math.min(MOVE_BUDGET_MAX, p.moveBudget + 1);
        p.ticksSinceClaim++;
        p.airTicks = mob.onGround() ? 0 : p.airTicks + 1;
        if (!flyer && (mob.onGround() || mob.onClimbable() || mob.isInWater() || mob.isInLava() || Double.isNaN(p.groundY)))
            p.groundY = mob.getY();
        if (p.correctionCooldown > 0)
            p.correctionCooldown--;
        if (!serverMoves && (p.claimFresh || p.ticksSinceClaim <= CLAIM_FRESH_TICKS)) {
            // prediction: the claims move the unit (applyClaim); no input-driven walk on top. A shove the server gave
            // it (knockback, a blast) reached the client as a motion packet and is in the claims, so it only widens
            // what a claim may cover; then the velocity is dropped to the ground probe (GROUND_PROBE): vanilla travel
            // moves by it before it adds gravity, and that move is what sets onGround for this tick
            // ...but only if the last tick was a claims tick too (it left only the probe): right after the keys or an
            // ability moved it, the velocity is that walk's own momentum, not a shove (round-6 review of #222)
            Vec3 dm = mob.getDeltaMovement();
            float shove = claimsBefore ? (float) Math.sqrt(dm.x * dm.x + dm.z * dm.z) : 0;
            p.claimsLastTick = true;
            p.shoveAllowance = Math.max(p.shoveAllowance * 0.91f, shove > 0.2f ? shove : 0);
            if (shove > SHOVE_SEEN) {
                // a shove adds how far it can carry the unit on each axis: never further than its velocity there could
                // through the air (decaying at air drag, 0.91 a tick: v / 0.09 in all). A new knockback after the last
                // has died out starts afresh, with the slack
                if (p.serverShove <= SHOVE_SEEN)
                    p.knockEast = p.knockWest = p.knockSouth = p.knockNorth = KNOCK_SLACK;
                double reach = 1 / (1 - 0.91);
                p.knockEast += Math.max(0, dm.x) * reach;
                p.knockWest += Math.max(0, -dm.x) * reach;
                p.knockSouth += Math.max(0, dm.z) * reach;
                p.knockNorth += Math.max(0, -dm.z) * reach;
            }
            p.serverShove = Math.max(p.serverShove, shove > SHOVE_SEEN ? shove : 0);
            if (shove > 0.2f && !p.headless) {
                // vanilla only sends the shove if this unit happened to tick before the attacker; send it ourselves
                // so the possessor's prediction always carries it (setting a velocity twice is harmless)
                ServerPlayer sp = p.level.getServer().getPlayerList().getPlayer(p.player);
                if (sp != null)
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(mob.getId(), dm));
            }
            if (p.claimFresh)
                applyClaim(p, mob, flyer);
            mob.setYRot(p.yaw);
            mob.setXRot(p.pitch);
            mob.setYHeadRot(p.yaw);
            mob.setYBodyRot(p.yaw);
            // everyone else sees the run (vanilla's sprint dust): the flag follows the claims' keys
            setSprintFlag(mob, sprints(mob, p.forward, p.sprint));
            mob.setSpeed(0);
            mob.setZza(0);
            mob.setXxa(0);
            mob.setYya(0);
            mob.setJumping(false);
            // a walker keeps a probe straight down, so travel's move tells on-ground from the world (GROUND_PROBE);
            // a flyer has no ground to find and stays put
            mob.setDeltaMovement(flyer ? Vec3.ZERO : GROUND_PROBE_VEC);
            return true;
        }
        // the AI's own convention (MoveControl): speed from the attribute, input scaled by it, so the unit walks at
        // exactly the pace it walks under RTS orders (sprinting: 1.3x)
        applyKeys(mob, stale ? 0 : p.forward, stale ? 0 : p.strafe, !stale && p.jump, !stale && p.descend,
            !stale && p.sprint, p.yaw, p.pitch);
        return true;
    }

    // ---- orders and pings become objectives ----

    /**
     * Strips possessed units out of an RTS order (UnitActionServerboundPacket.accept) and turns the order into an
     * objective for each one's possessor. Returns the ids that still take the order (the same array when none was
     * possessed).
     */
    public static int[] divertOrders(ServerLevel level, UnitAction action, int[] ids, BlockPos pos, int targetId) {
        if (BY_UNIT.isEmpty() || ids == null || ids.length == 0)
            return ids;
        int possessed = 0;
        for (int id : ids)
            if (BY_UNIT.containsKey(id))
                possessed++;
        if (possessed == 0)
            return ids;
        int[] out = new int[ids.length - possessed];
        int j = 0;
        for (int id : ids) {
            Possession p = BY_UNIT.get(id);
            if (p == null)
                out[j++] = id;
            else
                objectiveFromOrder(level, p, action, pos, targetId);
        }
        return out;
    }

    static void objectiveFromOrder(ServerLevel level, Possession p, UnitAction action, BlockPos pos, int targetId) {
        Objective o;
        Entity target = targetId > 0 && level != null ? level.getEntity(targetId) : null;
        switch (action) {
            case MOVE, ATTACK_MOVE, PATROL -> o = target != null && action == UnitAction.MOVE ? Objective.PROTECT : Objective.MOVE;
            case ATTACK -> o = target != null ? Objective.ATTACK : Objective.MOVE;
            case ATTACK_BUILDING -> o = Objective.DESTROY;
            case GUARD, FOLLOW -> o = Objective.PROTECT;
            case STOP, HOLD -> o = Objective.NONE;
            default -> {
                return;   // abilities, economy orders: nothing for a fighter in the field to do
            }
        }
        double x = target != null ? target.getX() : pos.getX() + 0.5;
        double y = target != null ? target.getY() : pos.getY();
        double z = target != null ? target.getZ() : pos.getZ() + 0.5;
        sendObjective(p, o, x, y, z, target != null ? target.getId() : -1);
    }

    /** A map ping (MapMarkerServerboundPacket): every possessor of the pinger's (or an ally's) units gets "go here". */
    public static void onPing(String pinger, int x, int z) {
        if (BY_PLAYER.isEmpty() || pinger == null)
            return;
        for (Possession p : BY_PLAYER.values())
            if (pinger.equals(p.ownerName) || AlliancesServerEvents.isAllied(pinger, p.ownerName))
                sendObjective(p, Objective.PING, x + 0.5, Double.NaN, z + 0.5, -1);
    }

    static void sendObjective(Possession p, Objective o, double x, double y, double z, int targetId) {
        p.objective = o;
        if (p.headless)
            return;
        ServerPlayer sp = p.level.getServer().getPlayerList().getPlayer(p.player);
        if (sp != null)
            PossessionClientboundPacket.objective(sp, o, x, y, z, targetId);
    }

    // ---- events ----

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || BY_PLAYER.isEmpty() || evt.getServer() == null)
            return;
        boolean slow = evt.getServer().getTickCount() % 10 == 0;
        for (Possession p : BY_PLAYER.values()) {
            Entity e = p.level.getEntity(p.unitId);
            if (!(e instanceof Mob mob) || !mob.isAlive() || mob.isRemoved()) {
                release(p.player, "Your unit is gone.");
                continue;
            }
            p.ticksSinceInput++;
            p.hitsThisTick = 0;
            if (p.attackCooldown > 0)
                p.attackCooldown--;
            if (p.aiTicks > 0) {
                p.aiTicks--;
                p.aiAge++;
                // the cast is done (no channel, nowhere left to walk): control comes straight back
                if (p.aiAge >= 10 && !(e instanceof Unit u && u.isCasting()) && mob.getNavigation().isDone())
                    p.aiTicks = 0;
                if (p.aiTicks == 0) {
                    mob.getNavigation().stop();
                    mob.setTarget(null);
                }
            }
            if (slow && !p.headless) {
                ServerPlayer sp = evt.getServer().getPlayerList().getPlayer(p.player);
                if (sp == null || sp.level() != mob.level()) {
                    release(p.player, null);
                    continue;
                }
                if (sp.distanceToSqr(mob) > PLAYER_LEASH * PLAYER_LEASH)
                    sp.teleportTo(mob.getX(), mob.getY() + 12, mob.getZ());
            }
        }
    }

    // after the totem check (HeroServerEvents cancels a death the totem saves)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent evt) {
        if (evt.isCanceled() || BY_UNIT.isEmpty() || evt.getEntity().level().isClientSide())
            return;
        Possession p = BY_UNIT.get(evt.getEntity().getId());
        if (p == null)
            return;
        String why = evt.getEntity() instanceof HeroUnit && HeroRoles.isHeroRole(p.playerName)
            ? "Your hero has fallen - you spectate until the commander revives it."
            : "Your unit was slain.";
        release(p.player, why);
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (BY_UNIT.isEmpty())
            return;
        Entity src = evt.getSource().getEntity();   // projectiles report their shooter here
        if (src != null && !src.level().isClientSide() && BY_UNIT.containsKey(src.getId()))
            evt.setAmount(evt.getAmount() * DAMAGE_BONUS);
    }

    /**
     * Hit markers and damage numbers (hero slice 3): the possessor hears about every hurt their unit deals - melee,
     * its projectiles (they report their shooter) and its abilities - with the final amount, and whether it killed.
     * Capped per tick so an area ability on a crowd is a handful of packets.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (BY_UNIT.isEmpty() || evt.isCanceled() || evt.getAmount() <= 0)
            return;
        Entity src = evt.getSource().getEntity();
        LivingEntity victim = evt.getEntity();
        if (src == null || src.level().isClientSide() || src == victim)
            return;
        Possession p = BY_UNIT.get(src.getId());
        if (p == null || p.headless || p.hitsThisTick >= HITS_PER_TICK)
            return;
        ServerPlayer sp = p.level.getServer().getPlayerList().getPlayer(p.player);
        if (sp == null)
            return;
        p.hitsThisTick++;
        boolean kills = evt.getAmount() >= victim.getHealth();
        PossessionClientboundPacket.hit(sp, victim.getId(), victim.getX(), victim.getY() + victim.getBbHeight(),
            victim.getZ(), evt.getAmount(), kills);
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent evt) {
        release(evt.getEntity().getUUID(), null);
    }

    /** A Hero-role player who relogs mid-match gets their role (and with it their team fog) back. */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity() instanceof ServerPlayer sp && HeroRoles.isHeroRole(sp.getName().getString()))
            PossessionClientboundPacket.role(sp, true);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        BY_UNIT.clear();
        BY_PLAYER.clear();
    }

    /** /rts-reset: nobody holds anything any more. */
    public static void clearAll() {
        for (UUID id : new ArrayList<>(BY_PLAYER.keySet()))
            release(id, null);
        BY_UNIT.clear();
        BY_PLAYER.clear();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        // /rts-possession allow|deny: let Hero-role teammates possess any of your units, not only the hero
        evt.getDispatcher().register(Commands.literal("rts-possession")
            .then(Commands.literal("allow").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                HeroRoles.setLends(sp.getName().getString(), true);
                sp.sendSystemMessage(Component.literal("Hero-role teammates may now possess any of your units."));
                return 1;
            }))
            .then(Commands.literal("deny").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                HeroRoles.setLends(sp.getName().getString(), false);
                sp.sendSystemMessage(Component.literal("Hero-role teammates may possess only your heroes."));
                return 1;
            }))
            .then(Commands.literal("release").executes(c -> {
                release(c.getSource().getPlayerOrException().getUUID(), null);
                return 1;
            })));
    }
}
