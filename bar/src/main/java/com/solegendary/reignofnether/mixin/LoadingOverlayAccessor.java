package com.solegendary.reignofnether.mixin;

import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The reload a loading overlay is waiting on (ForgeLoadingOverlay passes the same instance to its super). */
@Mixin(LoadingOverlay.class)
public interface LoadingOverlayAccessor {
    @Accessor("reload")
    ReloadInstance reignofnether$getReload();
}
