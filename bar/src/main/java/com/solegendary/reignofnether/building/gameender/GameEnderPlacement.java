package com.solegendary.reignofnether.building.gameender;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.attackwarnings.AttackWarningClientboundPacket;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.data.DataType;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.research.ResearchServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The shared life of a <b>game ender</b> (design/big_units.md section 2): BAR's nuke silo / Ragnarok as a fantasy
 * building. Server side only; subclasses supply {@link #strike} (what happens at the target) and the look.
 * <ol>
 *   <li><b>Announced when placed.</b> The first server tick of the placement - construction has only just begun -
 *   tells every player who started what and where, and pings every enemy's attack alert at the site (their alert
 *   hotkey jumps the camera there). Scouting is never the problem; reacting in time is.</li>
 *   <li><b>Long construction</b> (the building's buildTimeBlocks) during which it is an ordinary, fragile site.</li>
 *   <li><b>Charging stages.</b> Once built, {@link #STAGES} stages of {@link #stageTicks} each, every one announced.
 *   It does not charge while its owner lacks Tier 3.</li>
 *   <li><b>Raidable.</b> Every {@link #RAID_STEP} of max HP it loses while charging knocks one stage's worth of charge
 *   off (never below zero): a raid that lands 30% resets a full charge.</li>
 *   <li><b>The strike.</b> At full charge it aims at the nearest enemy capitol (else the nearest enemy building),
 *   tells the victim, shows a column at the spot for {@link #TELEGRAPH_TICKS}, then either a charged
 *   {@link WardSpirePlacement} of the victim's side within {@link WardSpirePlacement#RANGE} intercepts it, or the strike
 *   lands. Then it charges again from zero.</li>
 * </ol>
 * Charge state is saved ({@link #CHARGE}, design/code_review.md R2-01): it used to start over on a reload, and the
 * unsaved "announced" flag made every reload re-broadcast the start and re-ping every enemy's alert - and a save-scum
 * (singleplayer) or a server restart wiped a bot's or a player's charge. A strike that was mid-telegraph comes back
 * fully charged and re-aims (and re-announces) on its first tick: the defender still gets the full warning. Cost at 8v8: a counter per tick per game ender (a handful per match at most), a
 * health read per tick while charging, one scan of the building list per strike.
 */
public abstract class GameEnderPlacement extends BuildingPlacement {

    public static final int STAGES = 3;
    public static final int STAGE_TICKS = 50 * 20;
    public static final int TELEGRAPH_TICKS = 5 * 20;
    /** Fraction of max HP that knocks one stage's worth of charge off. */
    public static final float RAID_STEP = 0.10f;
    /** Ticks between target searches while fully charged with nobody to hit. */
    static final int RETARGET_TICKS = 100;

    /** True once the start has been announced (first server tick). */
    public boolean announced = false;
    /** Completed stages, 0..STAGES. */
    public int stage = 0;
    /** Ticks into the current stage. */
    public int stageProgress = 0;
    /** Ticks per stage: {@link #STAGE_TICKS} in play; a game test may shorten it. */
    public int stageTicks = STAGE_TICKS;
    /** Ticks left on the strike's telegraph (0 = no strike under way). */
    public int telegraph = 0;
    @Nullable public BlockPos strikePos = null;
    @Nullable public String strikeVictim = null;
    public int strikesLanded = 0, strikesIntercepted = 0;
    /** The buildings to aim at and look for wards in; null = every building on the server. Game tests set it. */
    @Nullable public List<BuildingPlacement> pool = null;

    float raidDamage = 0;
    private int lastHealth = Integer.MIN_VALUE;
    private int retarget = 0;
    /** The highest stage index already paid for ({@link #payForStage}); -1 = none since the last strike. */
    private int paidStage = -1;
    private int payRetry = 0;
    /** Fractional charge carried between ticks when {@link #chargeRate} is below 1. */
    private float chargeAcc = 0;
    /** Ticks left of the last landed strike's lingering effect ({@link #tickAftermath}); 0 = none. Public for the game test. */
    public int aftermath = 0;

    protected GameEnderPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                                 ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    // ---- save / load (BuildingPlacement.writeSaveState / readSaveState; design/code_review.md R2-01) ----

    /** {announced, stage, stageProgress, raidDamage} of a game ender; absent on every other building. */
    public static final DataType<int[]> CHARGE = DataType.createRegistered(
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "game_ender_charge"),
        (tag, server) -> tag.contains("stage")
            ? new int[] { tag.getBoolean("announced") ? 1 : 0, tag.getInt("stage"), tag.getInt("progress"), tag.getInt("raid") }
            : null,
        v -> {
            CompoundTag tag = new CompoundTag();
            if (v != null && v.length >= 4) {
                tag.putBoolean("announced", v[0] != 0);
                tag.putInt("stage", v[1]);
                tag.putInt("progress", v[2]);
                tag.putInt("raid", v[3]);
            }
            return tag;
        },
        () -> null);

    @Override
    public void writeSaveState() {
        getDataStorage().setData(CHARGE, new int[] { announced ? 1 : 0, Math.min(stage, STAGES), stageProgress, (int) raidDamage });
    }

    @Override
    public void readSaveState() {
        int[] v = getDataStorage().getData(CHARGE);
        if (v == null || v.length < 4)
            return;
        announced = v[0] != 0;
        stage = Math.max(0, Math.min(STAGES, v[1]));
        stageProgress = Math.max(0, Math.min(stageTicks - 1, v[2]));
        raidDamage = Math.max(0, v[3]);
        retarget = 0;   // fully charged: aim again on the first tick
    }

    /** What lands at {@code pos} for the victim {@code victim} when nothing intercepts it. */
    protected abstract void strike(ServerLevel sl, BlockPos pos, String victim);

    /** The particle the telegraph column and the charging crown use. */
    protected abstract ParticleOptions chargeParticle();

    // ---- faction rules (round 2, design/big_units.md 2.5-2.9): every hook defaults to the Sun Spire's plain charge ----

    /**
     * Called once before stage {@code stageIndex} starts charging (and retried once a second while it returns false):
     * the Doom Forge's metal and the Heaven's Bell's energy are paid here. A stage a raid knocks the charge back into is
     * never paid twice.
     */
    protected boolean payForStage(ServerLevel sl, int stageIndex) {
        return true;
    }

    /** This tick's charge rate, 0..1 (0 = paused): the Maelstrom Engine's flood ring, the Earthcracker's vent. */
    protected float chargeRate(ServerLevel sl) {
        return 1f;
    }

    /** A stage below the last has just completed (the full charge goes straight to the strike instead). */
    protected void onStageComplete(ServerLevel sl, int completedStage) { }

    /** One tick of a landed strike's lingering effect, while {@link #aftermath} counts down (it runs during the recharge). */
    protected void tickAftermath(ServerLevel sl) { }

    /**
     * Pays {@code metal} and {@code energy} from {@code owner}'s pool at once, or nothing. An owner without a pool
     * (neutral, scripted) pays nothing and always succeeds, like EconomyServerEvents.requestFlow treats them.
     */
    static boolean payUpfront(String owner, int metal, int energy) {
        com.solegendary.reignofnether.resources.Resources pool = null;
        for (com.solegendary.reignofnether.resources.Resources r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                pool = r;
        if (pool == null)
            return true;
        if (pool.getMetal() + 0.001f < metal || pool.getEnergy() + 0.001f < energy)
            return false;
        pool.addMetal(-metal);
        pool.addEnergy(-energy);
        return true;
    }

    public Component displayName() {
        return Component.translatable("buildings.reignofnether." + getBuilding().structureName);
    }

    @Override
    public void tick(Level tickLevel) {
        super.tick(tickLevel);
        if (tickLevel instanceof ServerLevel sl)
            tickGameEnder(sl);
    }

    /** One server tick: announce, then (once built) charge, aim, telegraph and resolve. Public for the game test. */
    public void tickGameEnder(ServerLevel sl) {
        if (isDestroyedServerside || ownerName == null || ownerName.isBlank())
            return;
        if (!announced)
            announceStart(sl);
        if (!isBuilt)
            return;
        trackRaid();
        if (aftermath > 0) {
            aftermath--;
            tickAftermath(sl);
        }
        if (telegraph > 0) {
            telegraph--;
            if (telegraph % 5 == 0)
                drawColumn(sl);
            if (telegraph == 0)
                resolveStrike(sl);
            return;
        }
        if (stage >= STAGES) {
            // fully charged with nothing to hit yet: look again now and then
            if (--retarget <= 0) {
                retarget = RETARGET_TICKS;
                beginStrike(sl);
            }
            return;
        }
        if (!ResearchServerEvents.playerHasResearch(ownerName, ProductionItems.RESEARCH_TIER_3))
            return;   // dormant: the build button is the client gate, this the server's
        if (paidStage < stage) {
            if (payRetry > 0) {
                payRetry--;
                return;
            }
            if (!payForStage(sl, stage)) {
                payRetry = 20;   // waiting on the owner's economy: look again in a second
                return;
            }
            paidStage = stage;
        }
        chargeAcc += Math.max(0f, Math.min(1f, chargeRate(sl)));
        if (chargeAcc < 1f)
            return;
        chargeAcc -= 1f;
        stageProgress++;
        if (stageProgress % 40 == 0)
            crown(sl);
        if (stageProgress >= stageTicks) {
            stageProgress = 0;
            stage++;
            PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.game_ender_stage", false,
                ownerName, displayName(), stage, STAGES);
            // each stage rings a little higher, so the site itself says how close the strike is
            com.solegendary.reignofnether.sounds.Sfx.GAMEENDER_STAGE.play(sl, centrePos.getX() + 0.5, centrePos.getY() + 1, centrePos.getZ() + 0.5, 1f,
                0.8f + 0.12f * stage);
            if (stage >= STAGES)
                beginStrike(sl);
            else
                onStageComplete(sl, stage);
        }
    }

    /** Tells everyone who started what and where, and pings every enemy's alert at the site. Public for the game test. */
    public void announceStart(ServerLevel sl) {
        announced = true;
        PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.game_ender_started", true,
            ownerName, displayName(), centrePos.getX(), centrePos.getZ());
        synchronized (PlayerServerEvents.rtsPlayers) {
            for (RTSPlayer p : PlayerServerEvents.rtsPlayers)
                if (p.name != null && !p.name.equals(ownerName) && !AlliancesServerEvents.isAllied(ownerName, p.name))
                    AttackWarningClientboundPacket.sendWarning(p.name, centrePos, false);
        }
        com.solegendary.reignofnether.sounds.Sfx.GAMEENDER_START.playGlobal(sl, 1f, 1f);   // everyone hears it wake (plus an announcer line)
    }

    /** Charge lost to a raid since the last tick, read off the building's health. */
    private void trackRaid() {
        int hp = getHealth();
        if (lastHealth != Integer.MIN_VALUE && hp < lastHealth)
            onRaided(lastHealth - hp);
        lastHealth = hp;
    }

    /**
     * {@code damage} HP lost while charging: every {@link #RAID_STEP} of max HP takes one stage's worth of charge off
     * (never below zero). A strike already under way is committed and unaffected. Public for the game test.
     */
    public void onRaided(int damage) {
        if (telegraph > 0 || damage <= 0)
            return;
        raidDamage += damage;
        float step = Math.max(1, getMaxHealth() * RAID_STEP);
        while (raidDamage >= step) {
            raidDamage -= step;
            int total = Math.max(0, Math.min(stage, STAGES) * stageTicks + stageProgress - stageTicks);
            int before = stage;
            stage = total / stageTicks;
            stageProgress = total % stageTicks;
            if (stage < before)
                PlayerServerEvents.sendMessageToPlayer(ownerName, "server.reignofnether.game_ender_raided", false,
                    displayName(), stage, STAGES);
        }
    }

    /** Charge in stage units (0..STAGES), for the HUD and the bot. */
    public float charge() {
        return stage + (float) stageProgress / Math.max(1, stageTicks);
    }

    /**
     * Aims at the nearest enemy capitol (else the nearest enemy building) and starts the telegraph. False when there is
     * nobody to hit. Public for the game test.
     */
    public boolean beginStrike(ServerLevel sl) {
        BuildingPlacement target = pickTarget();
        if (target == null)
            return false;
        aimAt(sl, target.centrePos, target.ownerName);
        return true;
    }

    /** Starts the strike's telegraph at {@code pos} against {@code victim}. Public for the game test. */
    public void aimAt(ServerLevel sl, BlockPos pos, String victim) {
        stage = STAGES;
        stageProgress = 0;
        strikePos = pos;
        strikeVictim = victim;
        telegraph = TELEGRAPH_TICKS;
        PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.game_ender_aimed", true,
            ownerName, displayName(), victim);
        AttackWarningClientboundPacket.sendWarning(victim, pos, false);
        com.solegendary.reignofnether.sounds.Sfx.GAMEENDER_AIM.playGlobal(sl, 1f, 1f);
        drawColumn(sl);
    }

    /** The nearest enemy capitol, else the nearest enemy building, or null. Public for the 8v8 benchmark. */
    @Nullable
    public BuildingPlacement pickTarget() {
        BuildingPlacement bestCap = null, best = null;
        double bestCapD = Double.MAX_VALUE, bestD = Double.MAX_VALUE;
        for (BuildingPlacement bp : pool != null ? pool : BuildingServerEvents.getBuildings()) {
            if (bp == this || bp.isDestroyedServerside || !isEnemy(bp.ownerName))
                continue;
            double d = bp.centrePos.distSqr(centrePos);
            if (bp.isCapitol && d < bestCapD) {
                bestCapD = d;
                bestCap = bp;
            }
            if (d < bestD) {
                bestD = d;
                best = bp;
            }
        }
        return bestCap != null ? bestCap : best;
    }

    boolean isEnemy(@Nullable String other) {
        return other != null && !other.isBlank() && !other.equals(ownerName) && !AlliancesServerEvents.isAllied(ownerName, other);
    }

    /** A charged Ward Spire of the victim's side within its range of {@code pos}, or null. Public for the game test. */
    @Nullable
    public WardSpirePlacement interceptor(BlockPos pos, String victim) {
        WardSpirePlacement best = null;
        double bestD = Double.MAX_VALUE;
        for (BuildingPlacement bp : pool != null ? pool : BuildingServerEvents.getBuildings()) {
            if (!(bp instanceof WardSpirePlacement w) || !w.canIntercept())
                continue;
            if (!w.ownerName.equals(victim) && !AlliancesServerEvents.isAllied(w.ownerName, victim))
                continue;
            double dx = w.centrePos.getX() - pos.getX(), dz = w.centrePos.getZ() - pos.getZ(), d = dx * dx + dz * dz;
            if (d <= (double) WardSpirePlacement.RANGE * WardSpirePlacement.RANGE && d < bestD) {
                bestD = d;
                best = w;
            }
        }
        return best;
    }

    /** The telegraph ends: intercepted by a ward, or the strike lands. Then the charge starts over. Public for the game test. */
    public void resolveStrike(ServerLevel sl) {
        telegraph = 0;
        BlockPos pos = strikePos;
        String victim = strikeVictim;
        stage = 0;
        stageProgress = 0;
        paidStage = -1;
        chargeAcc = 0;
        strikePos = null;
        strikeVictim = null;
        if (pos == null || victim == null)
            return;
        WardSpirePlacement ward = interceptor(pos, victim);
        if (ward != null) {
            ward.spendCharge(sl, pos);
            strikesIntercepted++;
            PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.game_ender_intercepted", true,
                displayName(), ownerName, victim);
            com.solegendary.reignofnether.sounds.Sfx.GAMEENDER_INTERCEPT.playGlobal(sl, 1f, 1f);
            return;
        }
        com.solegendary.reignofnether.sounds.Sfx.GAMEENDER_STRIKE.play(sl, pos);
        strike(sl, pos, victim);
        strikesLanded++;
    }

    // ---- shared strike maths (round 2) ----------------------------------------------------------------------------

    /**
     * Enemy buildings whose centre is within {@code r} blocks (horizontally) of {@code pos}, collected first so a hit
     * that destroys one can't edit the list being walked. One scan of the building list: once per strike or per second
     * of an aftermath, never per tick.
     */
    protected List<BuildingPlacement> enemyBuildingsNear(BlockPos pos, double r) {
        List<BuildingPlacement> out = new ArrayList<>();
        double r2 = r * r;
        for (BuildingPlacement bp : pool != null ? pool : BuildingServerEvents.getBuildings()) {
            if (bp == this || bp.isDestroyedServerside || !isEnemy(bp.ownerName))
                continue;
            double dx = bp.centrePos.getX() - pos.getX(), dz = bp.centrePos.getZ() - pos.getZ();
            if (dx * dx + dz * dz <= r2)
                out.add(bp);
        }
        return out;
    }

    /**
     * Every living enemy unit within {@code r} blocks of (cx, cz) (and {@code r} vertically of {@code y}) into
     * {@code out} (cleared first); {@code scratch} is the caller's reused grid buffer. Returns {@code out}.
     */
    protected List<LivingEntity> enemyUnitsNear(ServerLevel sl, double cx, double y, double cz, double r,
                                                List<LivingEntity> scratch, List<LivingEntity> out) {
        return enemyUnitsNear(sl, cx, y, cz, r, r, scratch, out);
    }

    /**
     * {@link #enemyUnitsNear} with its own vertical reach {@code dy}: a strike aimed at a capitol's centre sits at the
     * building's mid-height, so a narrow strike must still reach the units on the ground round it.
     */
    protected List<LivingEntity> enemyUnitsNear(ServerLevel sl, double cx, double y, double cz, double r, double dy,
                                                List<LivingEntity> scratch, List<LivingEntity> out) {
        out.clear();
        double r2 = r * r;
        for (LivingEntity le : com.solegendary.reignofnether.unit.UnitGrid.near(sl, cx, cz, r, scratch)) {
            if (!le.isAlive() || le.level() != sl || !(le instanceof com.solegendary.reignofnether.unit.interfaces.Unit u)
                    || !isEnemy(u.getOwnerName()))
                continue;
            double dx = le.getX() - cx, dz = le.getZ() - cz;
            if (dx * dx + dz * dz <= r2 && Math.abs(le.getY() - y) <= dy)
                out.add(le);
        }
        scratch.clear();
        return out;
    }

    /** The telegraph: a column of the charge particle rising from the strike spot. */
    void drawColumn(ServerLevel sl) {
        if (strikePos == null)
            return;
        ParticleOptions p = chargeParticle();
        for (int y = 0; y < 24; y += 2)
            sl.sendParticles(p, strikePos.getX() + 0.5, strikePos.getY() + 1 + y, strikePos.getZ() + 0.5, 2, 0.4, 0.4, 0.4, 0);
    }

    /** The charging look: a ring at the crown that grows with every stage. */
    void crown(ServerLevel sl) {
        double top = maxCorner.getY() + 1.5, r = 1.5 + stage;
        ParticleOptions p = chargeParticle();
        for (int i = 0; i < 8 + stage * 4; i++) {
            double a = i * Math.PI * 2 / (8 + stage * 4);
            sl.sendParticles(p, centrePos.getX() + 0.5 + Math.cos(a) * r, top, centrePos.getZ() + 0.5 + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }
}
