package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Damage rules of the T3 roles (claude/t3-roles.md), in one hurt hook - the BigUnitServerEvents pattern:
 * <ul>
 *   <li><b>Dawn Aegis</b> (Sun Colossus, the Anchor): a friendly or allied unit within
 *   {@link SunColossusUnit#AEGIS_RADIUS} of a live Colossus takes x{@link SunColossusUnit#AEGIS_TAKEN_MULT} damage.
 *   Never the Colossus itself (its own mass is its shield), never twice (two Colossi are one aegis), and never on top
 *   of an Oriflamme's protection - a unit under both keeps the stronger (BAR's rule: no multiplicative buff piles).</li>
 * </ul>
 * The live Colossi keep themselves in a small list (each adds itself from its server tick), so the hook costs one
 * isEmpty check while none is fielded and a distance check per Colossus otherwise - never a scan of every unit.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public class T3ServerEvents {

    private static final List<SunColossusUnit> COLOSSI = new ArrayList<>();

    /** One "has fielded an experimental" line per player and unit type per this long (see AnnounceGate). */
    public static final long ANNOUNCE_WINDOW_MS = 60_000;
    private static final AnnounceGate ANNOUNCE_GATE = new AnnounceGate();

    /**
     * A T3 unit just arrived: tell everyone, once per owner and unit type per {@link #ANNOUNCE_WINDOW_MS} (the
     * second Colossus no longer repeats the first one's line), and as one chat line, not three (the blank lines
     * either side of sendMessageToAllPlayers tripled the space it took over the map).
     */
    public static void announceFielded(String owner, net.minecraft.network.chat.Component unitName) {
        if (!ANNOUNCE_GATE.allow(owner + "|" + unitName.getString(), System.currentTimeMillis(), ANNOUNCE_WINDOW_MS))
            return;
        com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToAllPlayersNoNewlines(
            "server.reignofnether.t3_arrived", true, owner, unitName);
    }

    /** Called by each Colossus' server tick while it lives (idempotent). */
    public static void trackColossus(SunColossusUnit c) {
        if (!COLOSSI.contains(c))
            COLOSSI.add(c);
    }

    /** Is {@code le} under a friendly Colossus' aegis right now (not the Colossus itself)? */
    public static boolean underAegis(LivingEntity le) {
        if (COLOSSI.isEmpty() || !(le instanceof Unit u) || le instanceof SunColossusUnit)
            return false;
        String owner = u.getOwnerName();
        if (owner == null || owner.isEmpty())
            return false;
        double r2 = (double) SunColossusUnit.AEGIS_RADIUS * SunColossusUnit.AEGIS_RADIUS;
        for (int i = COLOSSI.size() - 1; i >= 0; i--) {
            SunColossusUnit c = COLOSSI.get(i);
            if (c.isRemoved() || !c.isAlive()) {
                COLOSSI.remove(i);
                continue;
            }
            if (c.level() != le.level())
                continue;
            String co = c.getOwnerName();
            if (!owner.equals(co) && !AlliancesServerEvents.isAllied(owner, co))
                continue;
            if (c.distanceToSqr(le) <= r2)
                return true;
        }
        return false;
    }

    /**
     * The Dawn Aegis' damage-taken multiplier for {@code victim}: AEGIS_TAKEN_MULT under a friendly aegis, else 1 - and
     * 1 while an Oriflamme already protects it (the stronger of the two holds; the Oriflamme's own hook applies its).
     */
    public static float aegisTakenMult(LivingEntity victim) {
        if (!underAegis(victim))
            return 1f;
        float oriflamme = BigUnitServerEvents.auraTakenMult(victim);
        return oriflamme <= SunColossusUnit.AEGIS_TAKEN_MULT ? 1f : SunColossusUnit.AEGIS_TAKEN_MULT / Math.min(1f, oriflamme);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent evt) {
        LivingEntity victim = evt.getEntity();
        if (victim.level().isClientSide() || COLOSSI.isEmpty())
            return;
        float mult = aegisTakenMult(victim);
        if (mult != 1f)
            evt.setAmount(evt.getAmount() * mult);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        COLOSSI.clear();
        ANNOUNCE_GATE.clear();
    }
}
