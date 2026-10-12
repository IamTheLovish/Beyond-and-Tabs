package com.solegendary.reignofnether.onboarding;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The First Battle sessions of a world, so a tutorial left half-way resumes at the same step (title screen ->
 * First Battle -> Resume loads the world again). Kept apart from RoN's TutorialSaveData: that one belongs to RoN's
 * downloadable tutorial map and its villager-era stages.
 */
public class OnboardingSaveData extends SavedData {

    static final String NAME = "beyondandtabs-onboarding";

    final Map<String, OnboardingSession> sessions = new LinkedHashMap<>();

    public static OnboardingSaveData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(OnboardingSaveData::load, OnboardingSaveData::new, NAME);
    }

    public static OnboardingSaveData load(CompoundTag tag) {
        OnboardingSaveData data = new OnboardingSaveData();
        ListTag list = tag.getList("sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            OnboardingSession s = OnboardingSession.load(list.getCompound(i));
            if (!s.student.isEmpty())
                data.sessions.put(s.student, s);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (OnboardingSession s : sessions.values())
            list.add(s.save());
        tag.put("sessions", list);
        return tag;
    }
}
