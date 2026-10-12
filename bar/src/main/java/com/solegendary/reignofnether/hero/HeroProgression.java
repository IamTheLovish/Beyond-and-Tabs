package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hero progression for the faction heroes (design/hero_mode.md section 4, slice 2): the XP curve, the Deadlock-style
 * <b>boons</b>, the stat growth that tapers after level 8 (lovish's "tier 2.5" dip) and XP from capture points.
 * <p>
 * The XP curve is Reign of Nether's own ({@link HeroUnit#getHeroLevel(int)}: each level costs 160 XP more than the last,
 * 320 for level 2 up to 1600 for level 10, 8640 in all). A <b>boon</b> is a level-up announced: "Boon received: ability
 * point, power increase" - one ability point (each of the four abilities has three ranks, 12 ranks for 10 points, so a
 * build is a choice) and the level's stat growth ({@link #growthAt}). The growth is front-loaded: +8% health and damage per
 * boon to level 5, +5% to level 8, +2% after. Since the XP per level keeps climbing, the XP it costs to buy 1% of power
 * rises twelvefold from level 3 to level 9 ({@link #xpPerPercentAt}) - a hero races ahead early, keeps pace through
 * tier 2 and then flattens out while the RTS side's T3 keeps growing, which is the dip. What a late hero adds is its
 * fourth ability, a team utility (vision, shields, a tidepool, refunds, repairs), not more damage.
 * <p>
 * Capture points pay the team's heroes: {@link #CAPTURE_XP} when a site is taken and {@link #HOLD_XP_PER_SECOND} while
 * it is held - to a hero within {@link #CAPTURE_RANGE} blocks only, so it rewards the hero being there
 * (CapturePointServerEvents calls {@link #onCapture} and {@link #onHold}).
 */
public final class HeroProgression {
    private HeroProgression() { }

    public static final int CAPTURE_XP = 100;
    public static final float HOLD_XP_PER_SECOND = 1.0f;   // 10 XP per 10 s on the point
    public static final double CAPTURE_RANGE = 24;

    // the dip: per-boon stat growth by the level just reached
    public static final float GROWTH_EARLY = 0.08f;   // levels 2-5
    public static final float GROWTH_MID = 0.05f;     // levels 6-8
    public static final float GROWTH_LATE = 0.02f;    // levels 9-10

    /** Stat growth (fraction of base health and damage) gained on reaching {@code level}. */
    public static float growthAt(int level) {
        if (level <= 1)
            return 0;
        if (level <= 5)
            return GROWTH_EARLY;
        if (level <= 8)
            return GROWTH_MID;
        return GROWTH_LATE;
    }

    /** Health and damage multiplier at {@code level}: 1.0 at level 1, 1.51 at level 10. */
    public static float statMultiplier(int level) {
        float m = 1;
        for (int l = 2; l <= Math.min(level, HeroUnit.MAX_LEVEL); l++)
            m += growthAt(l);
        return m;
    }

    /** Total XP at which {@code level} is reached (0 for level 1), on Reign of Nether's curve. */
    public static int xpForLevel(int level) {
        int total = 0;
        int step = (int) (200 * HeroUnit.EXP_REQ_MULTIPLIER);
        for (int l = 2; l <= Math.min(level, HeroUnit.MAX_LEVEL); l++) {
            total += step;
            step += (int) (100 * HeroUnit.EXP_REQ_MULTIPLIER);
        }
        return total;
    }

    /** XP spent on the level that reaches {@code level}, per percent of stats it buys: how expensive power gets. */
    public static float xpPerPercentAt(int level) {
        float g = growthAt(level);
        if (g <= 0)
            return Float.MAX_VALUE;
        return (xpForLevel(level) - xpForLevel(level - 1)) / (g * 100);
    }

    // ---- boons ----

    /**
     * Server side, after a faction hero levelled from {@code before} to {@code after} (HeroUnit.addExperience already gave
     * the ability points, played the level-up sound and burst): heal by the health the boon added, and tell the owner and
     * whoever drives it. The possessing player's HUD shows its own banner from the experience sync ({@link #clientBoon}).
     */
    public static void onBoon(FactionHero hero, int before, int after) {
        LivingEntity le = (LivingEntity) hero;
        float gained = hero.kit().health * (statMultiplier(after) - statMultiplier(before));
        if (gained > 0)
            le.heal(gained);
        if (le.level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, le.getX(), le.getY() + 1, le.getZ(), 24, 0.4, 0.6, 0.4, 0.25);
        String owner = hero.getOwnerName();
        if (owner != null && !owner.isBlank())
            PlayerServerEvents.sendMessageToPlayer(owner, "hud.hero.reignofnether.boon", false, after);
        PossessionServer.Possession p = PossessionServer.ofUnit(le.getId());
        if (p != null && p.playerName != null && !p.playerName.equals(owner))
            PlayerServerEvents.sendMessageToPlayer(p.playerName, "hud.hero.reignofnether.boon", false, after);
    }

    // client side: the last boon a hero received, for the possession HUD's banner (written on the client thread only,
    // from the experience sync in FactionHero.setExperience)
    public static volatile int clientBoonUnitId = -1;
    public static volatile int clientBoonLevel = 0;
    public static volatile long clientBoonAtMillis = 0;

    public static void clientBoon(int unitId, int level) {
        clientBoonUnitId = unitId;
        clientBoonLevel = level;
        clientBoonAtMillis = System.currentTimeMillis();
    }

    // ---- capture points ----

    private static final List<LivingEntity> scratch = new ArrayList<>();
    // fractional hold XP per hero entity id, so 1 XP/s pays out exactly at any tick length
    private static final Map<Integer, Float> holdCarry = new HashMap<>();

    /** A capture site was just taken by {@code owner}: every hero of that team within range gets {@link #CAPTURE_XP}. */
    public static void onCapture(ServerLevel level, Entity point, String owner) {
        if (owner == null || owner.isBlank())
            return;
        for (LivingEntity le : UnitGrid.near(level, point.getX(), point.getZ(), CAPTURE_RANGE, scratch))
            if (isTeamHeroInRange(le, point, owner))
                ((HeroUnit) le).addExperience(CAPTURE_XP);
    }

    /** {@code seconds} of {@code owner} holding a capture site: its team's heroes in range earn hold XP. */
    public static void onHold(ServerLevel level, Entity point, String owner, float seconds) {
        if (owner == null || owner.isBlank() || seconds <= 0)
            return;
        for (LivingEntity le : UnitGrid.near(level, point.getX(), point.getZ(), CAPTURE_RANGE, scratch)) {
            if (!isTeamHeroInRange(le, point, owner))
                continue;
            float carry = holdCarry.getOrDefault(le.getId(), 0f) + HOLD_XP_PER_SECOND * seconds;
            int whole = (int) carry;
            holdCarry.put(le.getId(), carry - whole);
            if (whole > 0)
                ((HeroUnit) le).addExperience(whole);
        }
        if (holdCarry.size() > 256)   // dead heroes' ids: a few floats, cleared now and then
            holdCarry.clear();
    }

    static boolean isTeamHeroInRange(LivingEntity le, Entity point, String owner) {
        if (!(le instanceof HeroUnit) || !(le instanceof Unit u) || !le.isAlive())
            return false;
        if (le.distanceToSqr(point) > CAPTURE_RANGE * CAPTURE_RANGE)
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isBlank() && AlliancesServerEvents.isAlliedOrOwned(o, owner);
    }
}
