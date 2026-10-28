package com.solegendary.reignofnether.time;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.NightSourceAddon;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public class NightUtils {

    /**
     * One side's night sources: the built night-source buildings (centre and range squared, the centre from the
     * building's current blocks as before) and the units under NIGHT_WARPING (their positions are read live at each
     * check, so a moving unit counts where it is). isInRangeOfNightSource used to walk every building - each night
     * source's centre again from all its blocks, a Mausoleum's range again from all buildings - and every unit, per
     * call; it is called per undead unit for sun burn, per Spider for its sight range (fog, every 10 ticks) and from
     * Unit.tick, on both sides (Oct 26 perf audit: an 8v8 holds ~1,700 buildings and ~1,000 units, and the list is
     * usually empty). Rebuilt on the first check of each tick, and at once when the building or unit list changes
     * size; a source that turns on or off without that (a building finishing, an effect starting or ending) counts
     * from the next tick.
     */
    private static final class Sources {
        boolean stale = true;
        int buildingCount = -1, unitCount = -1, unitModCount = -1;
        float[] buildings = new float[24];   // x, z, rangeSqr per source
        int buildingFloats = 0;
        final ArrayList<LivingEntity> units = new ArrayList<>();
        float[] unitRangeSqr = new float[8];
    }

    private static final Sources SERVER = new Sources(), CLIENT = new Sources();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase == TickEvent.Phase.START)
            SERVER.stale = true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase == TickEvent.Phase.START)
            CLIENT.stale = true;
    }

    public static boolean isInRangeOfNightSource(Vec3 pos, boolean clientSide) {
        if ((clientSide && TimeClientEvents.isBloodMoonActive()) ||
            (!clientSide && TimeServerEvents.isBloodMoonActive())) {
            return true;
        }
        Sources s = sources(clientSide);
        float px = (float) pos.x, pz = (float) pos.z;
        float[] b = s.buildings;
        for (int i = 0; i < s.buildingFloats; i += 3) {
            float dx = px - b[i], dz = pz - b[i + 1];
            if (dx * dx + dz * dz < b[i + 2])
                return true;
        }
        for (int i = 0; i < s.units.size(); i++) {
            Vec3 at = s.units.get(i).position();
            float dx = px - (float) at.x, dz = pz - (float) at.z;
            if (dx * dx + dz * dz < s.unitRangeSqr[i])
                return true;
        }
        return false;
    }

    private static Sources sources(boolean clientSide) {
        Sources s = clientSide ? CLIENT : SERVER;
        List<BuildingPlacement> buildings = clientSide ? BuildingClientEvents.getBuildings() : BuildingServerEvents.getBuildings();
        List<LivingEntity> units = clientSide ? UnitClientEvents.getAllUnits() : UnitServerEvents.getAllUnits();
        int unitModCount = clientSide ? 0 : UnitServerEvents.getAllUnitsModCount();
        if (!s.stale && buildings.size() == s.buildingCount && units.size() == s.unitCount && unitModCount == s.unitModCount)
            return s;

        s.buildingFloats = 0;
        for (BuildingPlacement building : buildings) {
            if (building.isDestroyedServerside) continue;
            NightSourceAddon nsa = building.getBuilding().getActiveAddon(NightSourceAddon.class);
            if (nsa == null)
                continue;
            int range = nsa.getNightRange(building);
            if (range <= 0)
                continue;
            BlockPos centrePos = BuildingUtils.getCentrePos(building.getBlocks());
            if (s.buildingFloats + 3 > s.buildings.length)
                s.buildings = Arrays.copyOf(s.buildings, s.buildings.length * 2);
            s.buildings[s.buildingFloats++] = centrePos.getX();
            s.buildings[s.buildingFloats++] = centrePos.getZ();
            s.buildings[s.buildingFloats++] = range * range;
        }
        s.units.clear();
        for (LivingEntity le : units) {
            MobEffectInstance mei = le.getEffect(MobEffectRegistrar.NIGHT_WARPING.get());
            if (mei == null)
                continue;
            int range = mei.getAmplifier() + 1;
            if (s.units.size() == s.unitRangeSqr.length)
                s.unitRangeSqr = Arrays.copyOf(s.unitRangeSqr, s.unitRangeSqr.length * 2);
            s.unitRangeSqr[s.units.size()] = range * range;
            s.units.add(le);
        }
        s.stale = false;
        s.buildingCount = buildings.size();
        s.unitCount = units.size();
        s.unitModCount = unitModCount;
        return s;
    }

    public static boolean isSunBurnTick(Mob mob) {
        if (mob.level().isClientSide)
            return false;

        if (mob instanceof Unit unit && ResearchServerEvents.playerHasCheat(unit.getOwnerName(), "slipslopslap"))
            return false;

        if (mob.tickCount % 10 == 0 && TimeUtils.isDay(mob.level())) {
            BlockPos blockpos = new BlockPos((int) mob.getX(), (int) mob.getEyeY(), (int) mob.getZ());
            boolean isProtected = mob.isInWaterRainOrBubble() || mob.isInPowderSnow || mob.wasInPowderSnow || mob.isOnFire();
            // Return early if mob is protected or sky is not visible
            if (isProtected || !mob.level().canSeeSky(blockpos)) return false;

            // Check if mob is within range of any NightSource
            Vec3 mobEyePos = mob.getEyePosition();
            return !NightUtils.isInRangeOfNightSource(mobEyePos, mob.level().isClientSide);
        }
        return false;
    }
}
