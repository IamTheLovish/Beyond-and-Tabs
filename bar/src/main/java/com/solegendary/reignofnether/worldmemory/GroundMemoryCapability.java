package com.solegendary.reignofnether.worldmemory;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The ground memory field as a Forge chunk capability: it lives on the server's LevelChunk, saves into the chunk's
 * own NBT (Forge's "ForgeCaps" tag, {@link #ID}; ~1 KB, and only for chunks that hold marks), loads and unloads with
 * the chunk, and needs no registry of its own (claude/world-that-remembers.md 3.1). Server chunks only: the client
 * keeps its copies in GroundMemoryClient, independent of the client chunk cache, which drops and rebuilds chunks as
 * the fog and the view move (the "see it as you last saw it" rule).
 * <p>
 * Registration on the mod bus ({@link #register}) and attachment on the Forge bus ({@link Attach}) both self-register
 * through {@code @Mod.EventBusSubscriber}, as ResourceIndexEvents and GameTestShards do.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GroundMemoryCapability {
    private GroundMemoryCapability() { }

    public static final ResourceLocation ID = new ResourceLocation(ReignOfNether.MOD_ID, "ground_memory");
    public static final Capability<GroundMemory> CAP = CapabilityManager.get(new CapabilityToken<>() { });

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent evt) {
        evt.register(GroundMemory.class);
    }

    /** The field of a server chunk, or null for a client chunk / before attachment. Allocation-free after the first call. */
    @Nullable
    public static GroundMemory of(LevelChunk chunk) {
        if (chunk == null)
            return null;
        return chunk.getCapability(CAP).orElse(null);
    }

    /** One chunk's provider: the field plus its NBT round trip. */
    public static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final GroundMemory memory;
        private final LazyOptional<GroundMemory> optional;

        Provider(GroundMemory memory) {
            this.memory = memory;
            this.optional = LazyOptional.of(() -> this.memory);
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            return cap == CAP ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            memory.save(tag);
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            // may run on a chunk-loading worker thread (ChunkSerializer.read): plain data, nothing shared
            GroundMemory loaded = GroundMemory.load(tag, memory.lastDecayTick());
            if (!loaded.isEmpty()) {
                memory.setAll(loaded.cells());
                memory.setLastDecayTick(loaded.lastDecayTick());
            }
        }
    }

    /** Attaches a provider to every server chunk (Forge bus). */
    @Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
    public static final class Attach {
        private Attach() { }

        @SubscribeEvent
        public static void onAttach(AttachCapabilitiesEvent<LevelChunk> evt) {
            LevelChunk chunk = evt.getObject();
            if (chunk == null || chunk.getLevel() == null || chunk.getLevel().isClientSide())
                return;
            evt.addCapability(ID, new Provider(new GroundMemory(chunk.getLevel().getGameTime())));
        }
    }
}
