package com.solegendary.reignofnether.mixin.goals;

import com.solegendary.reignofnether.perf.GoalTimes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Times every call GoalSelector makes on a goal, per goal class (perf/GoalTimes, the [Goals] line): the four calls
 * GoalSelector makes on a goal's wrapper - canContinueToUse, canUse and start in tick, tick in tickRunningGoals (one
 * call site each in 1.20.1) - are redirected through a handler that stamps the time around the same call.
 *
 * <p>Redirects, not HEAD/RETURN injects on WrappedGoal (#470 review r1, item 2): an {@code @Inject} handler is handed a
 * new CallbackInfo on every call, measuring or not - two small allocations per goal call, ~10,000 a tick at 700 units.
 * A redirect handler gets only the wrapper, so when [Sys] is not measuring the cost is one volatile read per goal call
 * and nothing is allocated (GoalSelectorMixinShapeTest keeps it so). The same pattern as LivingEntityMixin's timed
 * travel and serverAiStep. A call that throws is not counted and propagates as before.
 */
@Mixin(GoalSelector.class)
public abstract class GoalSelectorMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/goal/WrappedGoal;canContinueToUse()Z"))
    private boolean reignofnether$timedCanContinueToUse(WrappedGoal goal) {
        long started = GoalTimes.start();
        boolean result = goal.canContinueToUse();
        if (started != GoalTimes.OFF)
            GoalTimes.stop(goal.getGoal(), GoalTimes.CAN_CONTINUE, started);
        return result;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/goal/WrappedGoal;canUse()Z"))
    private boolean reignofnether$timedCanUse(WrappedGoal goal) {
        long started = GoalTimes.start();
        boolean result = goal.canUse();
        if (started != GoalTimes.OFF)
            GoalTimes.stop(goal.getGoal(), GoalTimes.CAN_USE, started);
        return result;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/goal/WrappedGoal;start()V"))
    private void reignofnether$timedStart(WrappedGoal goal) {
        long started = GoalTimes.start();
        goal.start();
        if (started != GoalTimes.OFF)
            GoalTimes.stop(goal.getGoal(), GoalTimes.START, started);
    }

    @Redirect(method = "tickRunningGoals", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/goal/WrappedGoal;tick()V"))
    private void reignofnether$timedTick(WrappedGoal goal) {
        long started = GoalTimes.start();
        goal.tick();
        if (started != GoalTimes.OFF)
            GoalTimes.stop(goal.getGoal(), GoalTimes.TICK, started);
    }
}
