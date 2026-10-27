package com.solegendary.reignofnether.mixin;

import com.electronwill.nightconfig.core.Config;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Forge 47's ConfigValue.get() checks its cached value and returns it in two separate reads of a plain field. A config
 * reload on another thread (the config file watcher after any setting is saved, or setConfig's correct + afterReload)
 * calls clearCache() and can land between the two reads, so get() hands back null and the caller's unboxing throws
 * (PR run 40469588386: COMMON_SETUP crashed with "ResourceCostConfigEntry.getWood() is null"; PR #285 fixed that one
 * entry). Every other config read in the mod - about 90 client reads per frame in hud/, display settings, the
 * minimap - has the same window, and each settings click saves the file and starts a reload.
 *
 * A loaded value is never null, so a null return with a loaded config is always this race: read the value straight
 * from the loaded config instead of the cache. This is what get() itself does when the cache is empty, so no value
 * changes; only the null goes. An unloaded config keeps Forge's own behaviour.
 */
@Mixin(value = ForgeConfigSpec.ConfigValue.class, remap = false)
public abstract class ConfigValueReloadRaceMixin {

    @Shadow private ForgeConfigSpec spec;
    @Shadow @Final private List<String> path;
    @Shadow @Final private Supplier<?> defaultSupplier;

    @Shadow protected abstract Object getRaw(Config config, List<String> path, Supplier<?> defaultSupplier);

    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private void bat$neverNullWhileLoaded(CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() != null || this.spec == null)
            return;
        Config loaded = ((ForgeConfigSpecAccessor) this.spec).bat$childConfig();
        if (loaded == null)
            return;
        cir.setReturnValue(getRaw(loaded, this.path, this.defaultSupplier));
    }
}
