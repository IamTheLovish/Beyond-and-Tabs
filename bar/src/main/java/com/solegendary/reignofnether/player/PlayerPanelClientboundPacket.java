package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.hud.playerdisplay.PlayerPanelClientEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * BAR's player list: the match roster for the top-right player panel, sent every 2 s by PlayerPanelServerEvents.
 * Each recipient gets its own copy: income, stored / storage (the panel's two thin eco bars) and commander health
 * are only filled in for the recipient and its allies, so an enemy's economy never reaches the wire. An empty
 * roster clears the panel (match over / reset).
 *
 * The HUD's tabbed stats table and the spectator observer bar (design/hud.md) ride on the same packet: each line
 * also says whether it is the recipient, an ally or a spectator (never-in-the-match player connected to the
 * server), which team it fights on (players allied to each other share an index), and - behind the same ally /
 * observer gate - army value (metal in non-worker units), unit count, units killed and lost, tech tier and how many
 * faction powers are off cooldown.
 */
public class PlayerPanelClientboundPacket {

    public static final int FLAG_ALIVE = 1;
    public static final int FLAG_ALLY_DATA = 2;
    public static final int FLAG_SELF = 4, FLAG_ALLY = 8, FLAG_SPECTATOR = 16;
    static final int MAX_ENTRIES = 64;

    /**
     * One line. {@code popUsed} / {@code popWorkers} / {@code popArmy} (behind the same gate) are the server's
     * population counts for the spectator's observer table, which used to count only the units its client had loaded.
     */
    public record Entry(String name, String factionKey, boolean alive, boolean hasAllyData,
                        float metalIncome, float energyIncome, float commanderHp,
                        float metalStored, float metalStorage, float energyStored, float energyStorage,
                        int relation, int team, float armyValue, int units, int kills, int losses, int tier,
                        int powersReady, int popUsed, int popWorkers, int popArmy) {
        public boolean isSelf() {
            return (relation & FLAG_SELF) != 0;
        }

        public boolean isAlly() {
            return (relation & FLAG_ALLY) != 0;
        }

        public boolean isSpectator() {
            return (relation & FLAG_SPECTATOR) != 0;
        }

        /** Fill of the metal bar, 0-1 (0 when the storage is unknown). */
        public float metalFill() {
            return metalStorage > 0 ? Math.max(0, Math.min(1, metalStored / metalStorage)) : 0;
        }

        public float energyFill() {
            return energyStorage > 0 ? Math.max(0, Math.min(1, energyStored / energyStorage)) : 0;
        }
    }

    public final List<Entry> entries;

    public PlayerPanelClientboundPacket(List<Entry> entries) {
        this.entries = entries;
    }

    // a static decoder rather than a second constructor, so the List constructor is never ambiguous
    public static PlayerPanelClientboundPacket decode(FriendlyByteBuf buffer) {
        int n = Math.min(buffer.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = buffer.readUtf(64);
            String faction = buffer.readUtf(128);
            int flags = buffer.readByte();
            int team = buffer.readByte();
            boolean ally = (flags & FLAG_ALLY_DATA) != 0;
            float metal = ally ? buffer.readFloat() : 0;
            float energy = ally ? buffer.readFloat() : 0;
            float hp = ally ? buffer.readFloat() : -1;
            float mStored = ally ? buffer.readFloat() : 0, mStorage = ally ? buffer.readFloat() : 0;
            float eStored = ally ? buffer.readFloat() : 0, eStorage = ally ? buffer.readFloat() : 0;
            float army = ally ? buffer.readFloat() : 0;
            int units = ally ? buffer.readVarInt() : 0, kills = ally ? buffer.readVarInt() : 0;
            int losses = ally ? buffer.readVarInt() : 0;
            int tier = ally ? buffer.readByte() : 0, powers = ally ? buffer.readByte() : 0;
            int popUsed = ally ? buffer.readVarInt() : 0, popWorkers = ally ? buffer.readVarInt() : 0;
            int popArmy = ally ? buffer.readVarInt() : 0;
            entries.add(new Entry(name, faction, (flags & FLAG_ALIVE) != 0, ally, metal, energy, hp,
                mStored, mStorage, eStored, eStorage, flags & (FLAG_SELF | FLAG_ALLY | FLAG_SPECTATOR), team,
                army, units, kills, losses, tier, powers, popUsed, popWorkers, popArmy));
        }
        return new PlayerPanelClientboundPacket(entries);
    }

    public void encode(FriendlyByteBuf buffer) {
        int n = Math.min(entries.size(), MAX_ENTRIES);
        buffer.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            buffer.writeUtf(e.name(), 64);
            buffer.writeUtf(e.factionKey(), 128);
            buffer.writeByte((e.alive() ? FLAG_ALIVE : 0) | (e.hasAllyData() ? FLAG_ALLY_DATA : 0)
                | (e.relation() & (FLAG_SELF | FLAG_ALLY | FLAG_SPECTATOR)));
            buffer.writeByte(Math.max(0, Math.min(127, e.team())));
            if (e.hasAllyData()) {
                buffer.writeFloat(e.metalIncome());
                buffer.writeFloat(e.energyIncome());
                buffer.writeFloat(e.commanderHp());
                buffer.writeFloat(e.metalStored());
                buffer.writeFloat(e.metalStorage());
                buffer.writeFloat(e.energyStored());
                buffer.writeFloat(e.energyStorage());
                buffer.writeFloat(e.armyValue());
                buffer.writeVarInt(Math.max(0, e.units()));
                buffer.writeVarInt(Math.max(0, e.kills()));
                buffer.writeVarInt(Math.max(0, e.losses()));
                buffer.writeByte(Math.max(0, Math.min(127, e.tier())));
                buffer.writeByte(Math.max(0, Math.min(127, e.powersReady())));
                buffer.writeVarInt(Math.max(0, e.popUsed()));
                buffer.writeVarInt(Math.max(0, e.popWorkers()));
                buffer.writeVarInt(Math.max(0, e.popArmy()));
            }
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PlayerPanelClientEvents.sync(this.entries)));
        ctx.get().setPacketHandled(true);
        return true;
    }
}
