package com.solegendary.reignofnether.mixin;

import com.electronwill.nightconfig.core.Config;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The loaded config behind a spec (Forge keeps it private), for ConfigValueReloadRaceMixin. */
@Mixin(value = ForgeConfigSpec.class, remap = false)
public interface ForgeConfigSpecAccessor {
    @Accessor("childConfig")
    Config bat$childConfig();
}
