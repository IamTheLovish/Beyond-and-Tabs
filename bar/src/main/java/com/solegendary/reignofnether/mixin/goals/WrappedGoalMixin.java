package com.solegendary.reignofnether.mixin.goals;

import com.solegendary.reignofnether.perf.GoalTimes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Times every call GoalSelector makes on a goal, per goal class (perf/GoalTimes, the [Goals] line): GoalSelector calls
 * the wrapper, the wrapper calls the goal, so a HEAD/RETURN pair here times the goal's own work and nothing of the
 * selector's. Does nothing but a volatile read unless [Sys] is measuring on the server thread. A call that throws is
 * not counted (its stamp is overwritten by the next call).
 */
@Mixin(WrappedGoal.class)
public abstract class WrappedGoalMixin {

    @Shadow @Final private Goal goal;

    // one stamp: GoalSelector never nests two calls on the same wrapper
    @Unique private long reignofnether$goalStarted;

    @Inject(method = "tick", at = @At("HEAD"))
    private void reignofnether$tickHead(CallbackInfo ci) {
        reignofnether$goalStarted = GoalTimes.start();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void reignofnether$tickReturn(CallbackInfo ci) {
        GoalTimes.stop(goal, GoalTimes.TICK, reignofnether$goalStarted);
    }

    @Inject(method = "canUse", at = @At("HEAD"))
    private void reignofnether$canUseHead(CallbackInfoReturnable<Boolean> cir) {
        reignofnether$goalStarted = GoalTimes.start();
    }

    @Inject(method = "canUse", at = @At("RETURN"))
    private void reignofnether$canUseReturn(CallbackInfoReturnable<Boolean> cir) {
        GoalTimes.stop(goal, GoalTimes.CAN_USE, reignofnether$goalStarted);
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"))
    private void reignofnether$canContinueHead(CallbackInfoReturnable<Boolean> cir) {
        reignofnether$goalStarted = GoalTimes.start();
    }

    @Inject(method = "canContinueToUse", at = @At("RETURN"))
    private void reignofnether$canContinueReturn(CallbackInfoReturnable<Boolean> cir) {
        GoalTimes.stop(goal, GoalTimes.CAN_CONTINUE, reignofnether$goalStarted);
    }

    @Inject(method = "start", at = @At("HEAD"))
    private void reignofnether$startHead(CallbackInfo ci) {
        reignofnether$goalStarted = GoalTimes.start();
    }

    @Inject(method = "start", at = @At("RETURN"))
    private void reignofnether$startReturn(CallbackInfo ci) {
        GoalTimes.stop(goal, GoalTimes.START, reignofnether$goalStarted);
    }
}
