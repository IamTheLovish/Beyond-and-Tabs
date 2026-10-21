package com.solegendary.reignofnether.mixin;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla clamps generic.max_health to 1024 (Attributes.MAX_HEALTH), so every unit set above it - the World Tree
// Walker 1400, Leviathan 1200, War Mammoth and Celestial Dragon 1100 - silently played at 1024/1024 (lovish's Oct 19
// clips). Lift the ceiling for that one attribute; both sides load this mixin, so the client's sanitizeValue on the
// attribute sync agrees with the server.
@Mixin(RangedAttribute.class)
public abstract class MaxHealthCapMixin {

    @Unique
    private static final double reignofnether$MAX_HEALTH_CEILING = 100_000.0;

    @Shadow @Final @Mutable private double maxValue;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void reignofnether$liftMaxHealthCap(String descriptionId, double defaultValue, double min, double max,
                                               CallbackInfo ci) {
        if ("attribute.name.generic.max_health".equals(((Attribute) (Object) this).getDescriptionId()))
            this.maxValue = Math.max(this.maxValue, reignofnether$MAX_HEALTH_CEILING);
    }
}
