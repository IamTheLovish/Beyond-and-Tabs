package com.solegendary.reignofnether.network;

import com.solegendary.reignofnether.ability.HeroAbility;
import com.solegendary.reignofnether.hero.HeroAction;
import com.solegendary.reignofnether.hero.HeroClientboundPacket;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesAction;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitKills;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.packets.UnitKillsClientboundPacket;
import com.solegendary.reignofnether.unit.packets.UnitSyncAbilityClientboundPacket;
import com.solegendary.reignofnether.unit.packets.UnitSyncMobEffectsClientboundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Re-sends what a narrowed {@link Audience} held back, the moment a player may know it again (rewrite 3). New file.
 * Before, every unit packet went to everyone, so a client that could not see a unit still received its hero mana,
 * status effects and kill tally (a fog leak) - and a client that did not have the entity loaded dropped them anyway,
 * so a unit that came into view showed whatever it had last been told. Now:
 * <ul>
 *   <li><b>A unit comes into view</b> (or loads on a client): UnitSyncBatcher already decides once a second, per
 *       player and unit, whether that player may know the unit (its side, a spectator, or in their fog's view); when
 *       that turns true it calls {@link #onUnitKnown}, which sends the unit's event-driven state to that player alone:
 *       status effects and kills always (no client asks for them when an entity loads), hero XP / ranks / mana when
 *       the client did not just load the entity (a loading client asks for those itself - UnitClientEvents
 *       .onEntityJoin - and is answered by {@link #syncHeroTo}).</li>
 *   <li><b>What a player may know changes</b> ({@link #viewChanged}: a defeat makes them a spectator, an alliance or
 *       a Hero role adds a side): their stockpiles and, for every unit, its private state too (cooldowns, skill
 *       points), because the entities are already loaded and no client request will come.</li>
 * </ul>
 * Everything here goes to one player, through {@link #sink}. Server thread only.
 */
public final class AudienceResync {
    private AudienceResync() { }

    /**
     * Where a resync packet for one player goes: by default that player's connection. A field so a game test can see
     * what would be sent without a connection (swapped and restored inside one server-thread callback, like
     * HeroRoles.roleSink, so parallel tests never see it).
     */
    public static volatile BiConsumer<ServerPlayer, Object> sink =
        (sp, packet) -> PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), packet);

    // per online player: what they privately knew at the last unit sync
    private static final Map<UUID, Audience.View> LAST_VIEW = new HashMap<>();

    /**
     * Records {@code view} for {@code sp} and says whether it differs from the last one. A player seen for the first
     * time has nothing stale to correct (the join syncs and their client's own requests cover them): false.
     * On a change, also re-sends the stockpiles they may now know.
     */
    public static boolean viewChanged(ServerPlayer sp, Audience.View view) {
        Audience.View last = LAST_VIEW.put(sp.getUUID(), view);
        if (last == null || view.sameAs(last))
            return false;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (view.knowsPrivately(r.ownerName))
                sink.accept(sp, new ResourcesClientboundPacket(ResourcesAction.SYNC, r.ownerName,
                    r.food, r.wood, r.ore, r.emerald, BlockPos.ZERO, ""));
        return true;
    }

    public static void forget(UUID player) {
        LAST_VIEW.remove(player);
    }

    public static void clear() {
        LAST_VIEW.clear();
    }

    /**
     * {@code sp} may know {@code unit} again (UnitSyncBatcher). {@code fresh}: the player had no snapshot of it, i.e.
     * their client only just loaded the entity (or this is their first sync) and asks for hero state and cooldowns
     * itself; {@code privately}: they are on its side or a spectator; {@code viewChanged}: see {@link #viewChanged}.
     */
    public static void onUnitKnown(ServerPlayer sp, LivingEntity unit, boolean fresh, boolean privately,
                                   boolean viewChanged) {
        for (MobEffectInstance mei : unit.getActiveEffects())
            sink.accept(sp, new UnitSyncMobEffectsClientboundPacket(unit.getId(), MobEffect.getId(mei.getEffect()),
                mei.getAmplifier(), mei.getDuration()));
        int kills = UnitKills.get(unit);
        if (kills > 0)
            sink.accept(sp, new UnitKillsClientboundPacket(new int[] { unit.getId() }, new int[] { kills }));
        if (fresh && !viewChanged)
            return;   // the client is loading the entity and asks for the rest (UnitClientEvents.onEntityJoin)
        if (unit instanceof HeroUnit hero)
            syncHeroTo(sp, hero, privately);
        if (privately && viewChanged) {
            UnitSyncAbilityClientboundPacket cooldowns = UnitSyncAbilityClientboundPacket.of(unit);
            if (cooldowns != null)
                sink.accept(sp, cooldowns);
        }
    }

    /**
     * A hero's state to one player: what a hero you can see shows (XP and with it the level, ability ranks, mana -
     * Warcraft III lets you read an enemy hero the same way) and, for its own side and spectators, the private part
     * (unspent skill points, charges such as a Necromancer's souls).
     */
    public static void syncHeroTo(ServerPlayer sp, HeroUnit hero, boolean privately) {
        int id = ((LivingEntity) hero).getId();
        sink.accept(sp, new HeroClientboundPacket(HeroAction.SET_EXPERIENCE, id, hero.getExperience(), 0));
        List<HeroAbility> abilities = hero.getHeroAbilities();
        for (int i = 0; i < Math.min(4, abilities.size()); i++)
            sink.accept(sp, new HeroClientboundPacket(HeroAction.SET_ABILITY_RANK, id, abilities.get(i).getRank(hero), i));
        // max first: the client clamps mana to its max (FactionHero.setMana)
        sink.accept(sp, new HeroClientboundPacket(HeroAction.SET_MAX_MANA, id, hero.getMaxMana(), 0));
        sink.accept(sp, new HeroClientboundPacket(HeroAction.SET_MANA, id, hero.getMana(), 0));
        if (privately) {
            sink.accept(sp, new HeroClientboundPacket(HeroAction.SET_SKILL_POINTS, id, hero.getSkillPoints(), 0));
            sink.accept(sp, new HeroClientboundPacket(HeroAction.SET_CHARGES, id, hero.getChargesForSaveData(), 0));
        }
    }
}
