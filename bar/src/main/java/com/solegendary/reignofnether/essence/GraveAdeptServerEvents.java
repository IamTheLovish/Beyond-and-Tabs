package com.solegendary.reignofnether.essence;

import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.GraveAdeptUnit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Grave Adept's rules (design/essence_casters.md "Gravebound Gravecaller" and Revision 1; its numbers are on
 * {@link GraveAdeptUnit}). Stub for the game tests written first.
 */
public final class GraveAdeptServerEvents {

    private GraveAdeptServerEvents() { }

    /**
     * What a unit's death is worth: exactly what the hero orbs of HeroServerEvents pay a hero ((population + 1) x 5,
     * plus 5 a level for a hero, in orbs of 2 worth 10 XP each; an odd 1 is worth nothing). 0 for a free unit.
     */
    public static int xpFor(LivingEntity dead) {
        if (!(dead instanceof Unit u) || u.getCost() == null || u.getCost().population <= 0)
            return 0;
        int value = (u.getCost().population + 1) * 5;
        if (dead instanceof HeroUnit hero)
            value += hero.getHeroLevel() * 5;
        return value / 2 * 10;
    }

    /** {@code caster}'s Grave Adept, if it stands. */
    @Nullable
    public static GraveAdeptUnit adeptOf(LivingEntity caster) {
        return null;
    }

    /** Feeds {@code adept} {@code xp}, applying any new level's health and damage. */
    public static void addXp(GraveAdeptUnit adept, int xp) {
    }

    /** The skeletons {@code adept} raised still standing. */
    public static List<Mob> raisedBy(GraveAdeptUnit adept) {
        return List.of();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
    }
}
