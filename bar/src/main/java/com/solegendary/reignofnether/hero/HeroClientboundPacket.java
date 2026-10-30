package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.ability.HeroAbility;
import com.solegendary.reignofnether.network.Audience;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class HeroClientboundPacket {

    HeroAction action;
    int unitId;
    float value;
    int abilityIndex;

    public static void setExperience(int unitId, int value) {
        PacketHandler.INSTANCE.send(Audience.seenUnit(unitId), // XP (the level): readable off a hero you can see (R2-09)
                new HeroClientboundPacket(HeroAction.SET_EXPERIENCE, unitId, value, 0));
    }

    /** Kill XP the hero just earned (HeroServerEvents): a "+N XP" over it, for its owner alone. */
    public static void xpGained(int unitId, String owner, int value) {
        if (owner == null || owner.isBlank() || value <= 0)
            return;
        PacketHandler.INSTANCE.send(Audience.owner(owner),
                new HeroClientboundPacket(HeroAction.XP_GAINED, unitId, value, 0));
    }

    public static void setSkillPoints(int unitId, int value) {
        PacketHandler.INSTANCE.send(Audience.teamOfUnit(unitId), // unspent points: its side and spectators
                new HeroClientboundPacket(HeroAction.SET_SKILL_POINTS, unitId, value, 0));
    }

    public static void setCharges(int unitId, int value) {
        PacketHandler.INSTANCE.send(Audience.teamOfUnit(unitId), // charges (souls): its side and spectators
                new HeroClientboundPacket(HeroAction.SET_CHARGES, unitId, value, 0));
    }

    public static void setAbilityRank(int unitId, int rank, int abilityIndex) {
        PacketHandler.INSTANCE.send(Audience.seenUnit(unitId), // ranks show on a hero you can see
                new HeroClientboundPacket(HeroAction.SET_ABILITY_RANK, unitId, rank, abilityIndex));
    }

    public static void setMana(int unitId, float value) {
        PacketHandler.INSTANCE.send(Audience.seenUnit(unitId), // a hero's mana: never through the fog (R2-09)
                new HeroClientboundPacket(HeroAction.SET_MANA, unitId, value, 0));
    }

    public static void setMaxMana(int unitId, float value) {
        PacketHandler.INSTANCE.send(Audience.seenUnit(unitId), // as setMana; AudienceResync re-sends on sight
                new HeroClientboundPacket(HeroAction.SET_MAX_MANA, unitId, value, 0));
    }

    public static void activateAbilityClientside(int unitId, int abilityIndex) {
        PacketHandler.INSTANCE.send(Audience.seenUnit(unitId), // a visible ability effect
                new HeroClientboundPacket(HeroAction.ACTIVATE_ABILITY_CLIENTSIDE, unitId, 0, abilityIndex));
    }

    public static void deactivateAbilityClientside(int unitId, int abilityIndex) {
        PacketHandler.INSTANCE.send(Audience.seenUnit(unitId), // a visible ability effect
                new HeroClientboundPacket(HeroAction.DEACTIVATE_ABILITY_CLIENTSIDE, unitId, 0, abilityIndex));
    }

    public HeroClientboundPacket(HeroAction action, int unitId, float value, int abilityIndex) {
        this.action = action;
        this.unitId = unitId;
        this.value = value;
        this.abilityIndex = abilityIndex;
    }

    public HeroClientboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(HeroAction.class);
        this.unitId = buffer.readInt();
        this.value = buffer.readFloat();
        this.abilityIndex = buffer.readInt();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeInt(this.unitId);
        buffer.writeFloat(this.value);
        buffer.writeInt(this.abilityIndex);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);

        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    // the level's id index, not a walk over every client unit: faction heroes send mana and
                    // experience syncs to every player who may see them, sixteen heroes at 8v8 (code_review R2-04);
                    // an id this client doesn't hold (a hero out of its sight) is simply skipped
                    var mcLevel = net.minecraft.client.Minecraft.getInstance().level;
                    HeroUnit hero = mcLevel != null && mcLevel.getEntity(unitId) instanceof HeroUnit h ? h : null;

                    if (hero != null) {
                        switch (action) {
                            case SET_EXPERIENCE -> hero.setExperience((int) value);
                            case XP_GAINED -> com.solegendary.reignofnether.resources.ResourcesClientEvents.addFloatingText(
                                "+" + (int) value + " XP", ((net.minecraft.world.entity.LivingEntity) hero).blockPosition().above(2));
                            case SET_SKILL_POINTS -> hero.setSkillPoints((int) value);
                            case SET_CHARGES -> hero.setChargesFromSaveData((int) value);
                            case SET_ABILITY_RANK -> {
                                List<HeroAbility> abls = hero.getHeroAbilities();
                                if (abls.size() > abilityIndex) abls.get(abilityIndex).setRank(hero, (int)value);
                                for (HeroAbility abl : abls)
                                    abl.updateStatsForRank(hero);
                                hero.updateAbilityButtons();
                            }
                            case SET_MANA -> hero.setMana(value);
                            case SET_MAX_MANA -> hero.setMaxMana(value);
                            case ACTIVATE_ABILITY_CLIENTSIDE -> hero.activateAbilityClientside(abilityIndex);
                            case DEACTIVATE_ABILITY_CLIENTSIDE -> hero.deactivateAbilityClientside(abilityIndex);
                        }
                    }
                    success.set(true);
                });
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}
