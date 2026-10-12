package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feeds the top-right player panel (BAR's player list, see PlayerPanelClientEvents). Every 2 s each connected player
 * gets the match roster: alive RTS players plus the ones already defeated (PlayerServerEvents moves them to
 * postGameRtsPlayers). Metal / energy income, stored / storage and commander health go only to the player itself
 * and its allies.
 *
 * 2 s is plenty for a glance-at panel and keeps it to one tiny packet per player per 40 ticks even at 8v8; the
 * commander scan is a single pass over all units at that cadence.
 *
 * The same pass totals each owner's army (metal in non-worker units), unit count and faction powers off cooldown for
 * the HUD's stats table and the spectator observer bar (design/hud.md); kills and losses come from the kill feed's
 * tally (KillFeedServerEvents). Teams are numbered by alliance so a spectator's view can group them, and every
 * connected player who is not in the match is listed as a spectator - nothing here assumes a host: each client gets
 * its own copy, so a mid-match joiner in a lobby match sees the same table as the player who started it.
 */
public class PlayerPanelServerEvents {

    static final int SYNC_TICKS = 40;
    private static int ticks = 0;
    private static boolean sentEmpty = true;   // nothing to clear before the first match

    /**
     * One roster line before it is filtered for a recipient. {@code popUsed} (units plus queued production, as the
     * server's own population check counts it), {@code popWorkers} and {@code popArmy} feed the spectator's observer
     * table, which used to count the units its own client had loaded - far bots always read 0 (playtest Oct 12).
     */
    public record Row(String name, String factionKey, boolean alive, float metalIncome, float energyIncome,
                      float commanderHp, float metalStored, float metalStorage, float energyStored, float energyStorage,
                      float armyValue, int units, int kills, int losses, int tier, int powersReady,
                      int popUsed, int popWorkers, int popArmy) {
        /** A row without the stored / storage figures (older call sites and tests). */
        public Row(String name, String factionKey, boolean alive, float metalIncome, float energyIncome, float commanderHp) {
            this(name, factionKey, alive, metalIncome, energyIncome, commanderHp, 0, 0, 0, 0);
        }

        /** A row without the army figures (older call sites and tests). */
        public Row(String name, String factionKey, boolean alive, float metalIncome, float energyIncome,
                   float commanderHp, float metalStored, float metalStorage, float energyStored, float energyStorage) {
            this(name, factionKey, alive, metalIncome, energyIncome, commanderHp, metalStored, metalStorage,
                energyStored, energyStorage, 0, 0, 0, 0, 1, 0);
        }

        /** A row without the population figures (older call sites and tests). */
        public Row(String name, String factionKey, boolean alive, float metalIncome, float energyIncome,
                   float commanderHp, float metalStored, float metalStorage, float energyStored, float energyStorage,
                   float armyValue, int units, int kills, int losses, int tier, int powersReady) {
            this(name, factionKey, alive, metalIncome, energyIncome, commanderHp, metalStored, metalStorage,
                energyStored, energyStorage, armyValue, units, kills, losses, tier, powersReady, 0, 0, 0);
        }
    }

    // the faction powers the observer bar counts as "ready" (BotPlayer.useFactionPowers casts the same set)
    private static final java.util.Set<Class<?>> FACTION_POWERS = java.util.Set.of(
        com.solegendary.reignofnether.ability.abilities.CryptTide.class,
        com.solegendary.reignofnether.ability.abilities.WarDrums.class,
        com.solegendary.reignofnether.ability.abilities.BastionAegis.class,
        com.solegendary.reignofnether.ability.abilities.TotemOfThePack.class,
        com.solegendary.reignofnether.ability.abilities.MagmaRupture.class,
        com.solegendary.reignofnether.ability.abilities.HolyBell.class,
        com.solegendary.reignofnether.ability.abilities.WitheringFog.class,
        com.solegendary.reignofnether.ability.abilities.SunriseSortie.class,
        com.solegendary.reignofnether.ability.abilities.SoulWisps.class,
        com.solegendary.reignofnether.ability.abilities.AwakenThicket.class,
        com.solegendary.reignofnether.ability.abilities.Bloom.class);

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || ++ticks < SYNC_TICKS)
            return;
        ticks = 0;
        List<ServerPlayer> players = evt.getServer().getPlayerList().getPlayers();
        List<Row> rows = buildRows();
        if (rows.isEmpty()) {
            if (!sentEmpty) {   // the match just ended or was reset: clear every panel once
                List<PlayerPanelClientboundPacket.Entry> none = List.of();
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerPanelClientboundPacket(none));
                com.solegendary.reignofnether.hud.KillFeedServerEvents.resetTally();
            }
            sentEmpty = true;
            return;
        }
        sentEmpty = false;
        int[] teams = teamsOf(rows);
        List<PlayerPanelClientboundPacket.Entry> spectators = spectatorEntries(players, rows);
        for (ServerPlayer sp : players) {
            List<PlayerPanelClientboundPacket.Entry> list = entriesFor(sp.getName().getString(), rows, teams);
            list.addAll(spectators);
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new PlayerPanelClientboundPacket(list));
        }
    }

    /**
     * Team index per row: a player allied to an earlier row joins its team, anyone else starts a new one. O(n^2) on
     * at most 16 players once per 2 s. Public for the game test.
     */
    public static int[] teamsOf(List<Row> rows) {
        int[] team = new int[rows.size()];
        int next = 0;
        for (int i = 0; i < rows.size(); i++) {
            team[i] = -1;
            for (int j = 0; j < i; j++)
                if (AlliancesServerEvents.isAllied(rows.get(i).name(), rows.get(j).name())) {
                    team[i] = team[j];
                    break;
                }
            if (team[i] < 0)
                team[i] = next++;
        }
        return team;
    }

    /** Connected players who are not in the match (lobby spectators, mid-match joiners): names only. */
    static List<PlayerPanelClientboundPacket.Entry> spectatorEntries(List<ServerPlayer> players, List<Row> rows) {
        List<PlayerPanelClientboundPacket.Entry> out = new ArrayList<>();
        for (ServerPlayer sp : players) {
            String n = sp.getName().getString();
            boolean inMatch = false;
            for (Row r : rows)
                if (r.name().equals(n)) {
                    inMatch = true;
                    break;
                }
            if (!inMatch)
                out.add(new PlayerPanelClientboundPacket.Entry(n, "", false, false, 0, 0, -1, 0, 0, 0, 0,
                    PlayerPanelClientboundPacket.FLAG_SPECTATOR, 127, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        }
        return out;
    }

    public static List<Row> buildRows() {
        // copy-on-write lists (match.MatchRoster): no lock needed on the game thread
        List<RTSPlayer> alive = new ArrayList<>(PlayerServerEvents.rtsPlayers);
        List<RTSPlayer> gone = new ArrayList<>(PlayerServerEvents.postGameRtsPlayers);
        if (alive.isEmpty())   // postGame players linger until the next reset; no live match means no panel
            return List.of();

        // healthiest commander per owner: one pass over all units (as before the stats table)
        Map<String, Float> commanderHp = new HashMap<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof Unit u) || !CommanderServerEvents.isCommander(le) || !le.isAlive())   // rare tag first
                continue;
            float hp = le.getMaxHealth() > 0 ? le.getHealth() / le.getMaxHealth() : 0;
            commanderHp.merge(u.getOwnerName(), hp, Math::max);
        }
        Map<String, float[]> army = armyTotals();
        List<Row> rows = new ArrayList<>(alive.size() + gone.size());
        for (RTSPlayer p : alive)
            rows.add(row(p, true, commanderHp, army));
        for (RTSPlayer p : gone) {
            boolean dup = false;   // a winner can sit in both lists for the last tick of a match
            for (Row r : rows)
                if (r.name().equals(p.name)) {
                    dup = true;
                    break;
                }
            if (!dup)
                rows.add(row(p, false, commanderHp, army));
        }
        return rows;
    }

    // {army metal, units, powers ready, worker population, army population} per owner for the stats table, observer
    // bar and the spectator's observer table. Recounted when a unit joined or
    // left (UnitServerEvents.getAllUnitsModCount) or after ARMY_STALE_NS, whichever comes first: in a match the 2 s
    // roster pass nearly always recounts (cooldowns tick too), but repeated passes over an unchanged army - the 8v8
    // stress benchmark calls this back to back - reuse it. The owner is read once per unit (an entity-data lookup).
    static final long ARMY_STALE_NS = 1_500_000_000L;
    private static final Map<String, float[]> armyByOwner = new HashMap<>();
    private static int armyModCount = Integer.MIN_VALUE, armySize = -1;
    private static long armyAtNs = 0;

    static Map<String, float[]> armyTotals() {
        List<LivingEntity> units = UnitServerEvents.getAllUnits();
        int mod = UnitServerEvents.getAllUnitsModCount();
        long now = System.nanoTime();
        if (mod == armyModCount && units.size() == armySize && now - armyAtNs < ARMY_STALE_NS)
            return armyByOwner;
        armyModCount = mod;
        armySize = units.size();
        armyAtNs = now;
        armyByOwner.clear();
        for (LivingEntity le : units) {
            if (!(le instanceof Unit u) || !le.isAlive())
                continue;
            String owner = u.getOwnerName();
            if (owner == null || owner.isEmpty())
                continue;
            float[] a = armyByOwner.computeIfAbsent(owner, k -> new float[5]);
            a[1]++;
            boolean worker = le instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
            if (u.getCost() != null) {
                if (!worker)
                    a[0] += u.getCost().metal();
                a[worker ? 3 : 4] += u.getCost().population;
            }
            // Abilities.get() builds a list, so only units that can carry a power look: commanders (their powers are
            // added at runtime) and unit types that were seen with one
            if (u.getAbilities() != null && (CommanderServerEvents.isCommander(le) || carriesPower(le, u)))
                for (com.solegendary.reignofnether.ability.Ability ab : u.getAbilities().get())
                    if (FACTION_POWERS.contains(ab.getClass()) && ab.isOffCooldown(u))
                        a[2]++;
        }
        return armyByOwner;
    }

    // unit class -> whether its ability list (fixed per type) holds a faction power; learnt once per class
    private static final Map<Class<?>, Boolean> POWER_CARRIERS = new HashMap<>();

    private static boolean carriesPower(LivingEntity le, Unit u) {
        Boolean known = POWER_CARRIERS.get(le.getClass());
        if (known != null)
            return known;
        boolean has = false;
        for (com.solegendary.reignofnether.ability.Ability ab : u.getAbilities().get())
            if (FACTION_POWERS.contains(ab.getClass())) {
                has = true;
                break;
            }
        POWER_CARRIERS.put(le.getClass(), has);
        return has;
    }

    private static Row row(RTSPlayer p, boolean alive, Map<String, Float> commanderHp, Map<String, float[]> army) {
        ResourceLocation key = p.faction == null ? null : Factions.getKey(p.faction);
        float metal = 0, energy = 0, mStored = 0, mStorage = 0, eStored = 0, eStorage = 0;
        if (alive) {
            EconomyServerEvents.PlayerEconomy eco = EconomyServerEvents.getEconomy(p.name);
            metal = eco.metalIncome + eco.metalConverted;   // same "income" the economy bar shows
            energy = eco.energyIncome;
            mStorage = eco.metalStorage;
            eStorage = eco.energyStorage;
            // the pool itself (BAR's bars are stored / storage, the income is the number next to them)
            for (Resources res : ResourcesServerEvents.resourcesList)
                if (res.ownerName.equals(p.name)) {
                    mStored = res.getMetal();
                    eStored = res.getEnergy();
                    break;
                }
        }
        float[] a = army.get(p.name);
        int tier = com.solegendary.reignofnether.research.ResearchServerEvents.playerHasResearch(p.name,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3) ? 3
            : com.solegendary.reignofnether.research.ResearchServerEvents.playerHasResearch(p.name,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2) ? 2 : 1;
        // population as the server's own cap check counts it (units plus queued production): one walk over the
        // owner's buildings for each side in play, every 2 s
        int popUsed = alive ? UnitServerEvents.getCurrentPopulation(p.name) : 0;
        return new Row(p.name, key == null ? "" : key.toString(), alive, metal, energy,
            alive ? commanderHp.getOrDefault(p.name, -1f) : -1f, mStored, mStorage, eStored, eStorage,
            a == null ? 0 : a[0], a == null ? 0 : (int) a[1],
            com.solegendary.reignofnether.hud.KillFeedServerEvents.kills(p.name),
            com.solegendary.reignofnether.hud.KillFeedServerEvents.losses(p.name),
            tier, a == null ? 0 : (int) a[2], popUsed, a == null ? 0 : (int) a[3], a == null ? 0 : (int) a[4]);
    }

    /**
     * The roster as {@code recipient} may see it: itself first, then its allies, then everyone else; economy and
     * commander data only on its own and its allies' lines. Public for the game test.
     */
    public static List<PlayerPanelClientboundPacket.Entry> entriesFor(String recipient, List<Row> rows) {
        return entriesFor(recipient, rows, teamsOf(rows));
    }

    public static List<PlayerPanelClientboundPacket.Entry> entriesFor(String recipient, List<Row> rows, int[] teams) {
        List<PlayerPanelClientboundPacket.Entry> self = new ArrayList<>(1);
        List<PlayerPanelClientboundPacket.Entry> allies = new ArrayList<>();
        List<PlayerPanelClientboundPacket.Entry> enemies = new ArrayList<>();
        // an observer (never in the match, defeated, or a winner who chose Spectate) sees every side's numbers
        // A Hero-role player (design/hero_mode.md) is not an RTS player either, but plays for a team: they see their
        // commanders' numbers like an ally's, never the enemy's (CR-06 - they got everyone's, as an observer)
        boolean hero = com.solegendary.reignofnether.hero.HeroRoles.isHeroRole(recipient);
        boolean observer = !hero && !PlayerServerEvents.isRTSPlayer(recipient);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean me = r.name().equals(recipient);
            boolean ally = !me && (AlliancesServerEvents.isAllied(recipient, r.name())
                || (hero && com.solegendary.reignofnether.hero.HeroRoles.servesOwner(recipient, r.name())));
            boolean data = me || ally || observer;
            int relation = (me ? PlayerPanelClientboundPacket.FLAG_SELF : 0) | (ally ? PlayerPanelClientboundPacket.FLAG_ALLY : 0);
            var e = new PlayerPanelClientboundPacket.Entry(r.name(), r.factionKey(), r.alive(), data,
                data ? r.metalIncome() : 0, data ? r.energyIncome() : 0, data ? r.commanderHp() : -1,
                data ? r.metalStored() : 0, data ? r.metalStorage() : 0, data ? r.energyStored() : 0, data ? r.energyStorage() : 0,
                relation, i < teams.length ? teams[i] : 0,
                data ? r.armyValue() : 0, data ? r.units() : 0, data ? r.kills() : 0, data ? r.losses() : 0,
                data ? r.tier() : 0, data ? r.powersReady() : 0,
                data ? r.popUsed() : 0, data ? r.popWorkers() : 0, data ? r.popArmy() : 0);
            (me ? self : ally ? allies : enemies).add(e);
        }
        // alive before defeated within each group, so the lines that matter stay on top
        allies.sort((a, b) -> Boolean.compare(b.alive(), a.alive()));
        enemies.sort((a, b) -> Boolean.compare(b.alive(), a.alive()));
        self.addAll(allies);
        self.addAll(enemies);
        return self;
    }
}
