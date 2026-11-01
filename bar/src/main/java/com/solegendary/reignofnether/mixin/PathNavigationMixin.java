package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.pathfinding.LineOfWalk;
import com.solegendary.reignofnether.unit.pathfinding.PathfinderConfig;
import com.solegendary.reignofnether.unit.pathfinding.VanillaPathBudget;
import com.solegendary.reignofnether.unit.pathfinding.WidePathMemo;
import com.solegendary.reignofnether.unit.units.monsters.SpiderUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

// No unit follows its path strictly: they aim at cell centres (PathConverter.CenteredPath, so they don't hug
// walls) but follow LOOSELY - vanilla corner-cutting and reach tolerance, no per-node lock, no stuck-recovery
// oscillation. Keeping the body off walls/edges/ceilings is the pathfinder's job (2x2 footprint + crowdingMalus),
// not the follower's. The ONLY thing we still relax is the VERTICAL advance gate, so a unit perched on a stair
// step (held up by its width ~1.0 above the next node) commits to the drop instead of spinning on the ledge.
@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin implements WidePathMemo.Holder {

    @Shadow @Final protected Mob mob;
    @Shadow protected Path path;
    @Shadow private float maxVisitedNodesMultiplier;

    @Shadow protected abstract Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange);
    @Shadow protected abstract boolean canUpdatePath();

    @Unique private final WidePathMemo reignofnether$wideMemo = new WidePathMemo();

    @Override
    public WidePathMemo reignofnether$wideMemo() {
        return reignofnether$wideMemo;
    }

    // Distance-scaled vanilla search for units (VanillaPathBudget): a short search first, the stock full-range one only
    // when the short one finds no complete path - and for a wide body, not again while the same full search failed
    // a moment ago (WidePathMemo). Every public createPath overload funnels through this one.
    @Inject(method = "createPath(Ljava/util/Set;IZI)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void reignofnether$scaledSearch(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy,
                                            CallbackInfoReturnable<Path> cir) {
        if (!(mob instanceof Unit) || mob.level().isClientSide() || targets.isEmpty())
            return;
        // vanilla answers this with the path in hand, no search at all: leave it to vanilla
        if (this.path != null && !this.path.isDone() && targets.contains(((PathNavigation) (Object) this).getTargetPos()))
            return;
        VanillaPathBudget.calls++;
        float full = (float) mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        float range = VanillaPathBudget.shortRange(VanillaPathBudget.farthest(mob, targets), full);
        boolean wide = reignofnether$isWideUnit();
        if (range >= full && !wide)
            return;   // nothing to save: the stock search as before
        float baseMultiplier = this.maxVisitedNodesMultiplier;
        Path p;
        try {
            this.maxVisitedNodesMultiplier = VanillaPathBudget.nodeMultiplier(baseMultiplier, range, full, wide,
                mob.getAttributeBaseValue(Attributes.FOLLOW_RANGE));
            p = createPath(targets, regionOffset, offsetUpward, accuracy, range);
        } finally {
            this.maxVisitedNodesMultiplier = baseMultiplier;
        }
        VanillaPathBudget.shortSearches++;
        // a complete path, or no path for a reason a wider search can not fix (airborne for a tick, below the world)
        if ((p != null && p.canReach()) || (p == null && !canUpdatePath())) {
            cir.setReturnValue(p);
            return;
        }
        BlockPos from = mob.blockPosition();
        long now = mob.level().getGameTime();
        // a wide body that searched the whole range for these targets from here a moment ago and found no way: the
        // same answer again, for 12-33 ms on the server thread (WidePathMemo) - keep the short search's
        if (wide && reignofnether$wideMemo.failedLately(from, targets, now)) {
            VanillaPathBudget.wideRepeats++;
            cir.setReturnValue(p);
            return;
        }
        VanillaPathBudget.fallbacks++;
        VanillaPathBudget.fallbacksTotal++;
        Path again = createPath(targets, regionOffset, offsetUpward, accuracy, full);
        if (wide)
            reignofnether$wideMemo.fullSearched(from, targets, now, again != null && again.canReach());
        cir.setReturnValue(again != null ? again : p);
    }

    @Unique
    private boolean reignofnether$isWideUnit() {
        if (mob instanceof SpiderUnit spider && spider.isWallClimbing()) return false;
        return mob instanceof Unit && mob.getBbWidth() > 1.0f;
    }

    @Unique
    private boolean reignofnether$isClimbingSpider() {
        return mob instanceof SpiderUnit spider && spider.isWallClimbing();
    }

    // Vanilla auto-recompute must never touch a unit (RTS or vanilla pathed). On any block change,
    // ServerLevel.sendBlockUpdated asks every mob's navigation shouldRecomputePath (true within
    // remaining-path-length blocks of the path midpoint - a huge sphere for a long march) and then
    // recomputePath() replaces the path with a synchronous vanilla createPath(targetPos). targetPos is
    // whatever the last vanilla createPath set (eg. an attack goal's chase target) and stop() never clears
    // it, so the unit veers back toward a STALE position - the multiplayer "units walk backwards /
    // oscillate" bug. Block-change reactions are the RTS system's job (dirty-chunk reclassify + the move
    // goal's own repath). This holds in vanilla mode (gamerule off) too: every unit goal repaths itself on its own
    // throttle (move goal when its path is done, chase goals every 5-20 ticks), so the stock recompute only added a
    // second, unthrottled createPath per nearby unit on every block change - half of bat:buildings in the Oct 22
    // 490-entity match was this, each placed building block re-pathing every unit within range. A path a new block
    // cuts is still caught by vanilla's own stuck check (stop -> the goal repaths). Non-unit mobs keep stock behaviour.
    @Unique
    private boolean reignofnether$ownsRepath() {
        return mob instanceof Unit;
    }

    @Inject(method = "shouldRecomputePath", at = @At("HEAD"), cancellable = true)
    private void reignofnether$noBlockUpdateRecompute(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (reignofnether$ownsRepath()) cir.setReturnValue(false);
    }

    // Also gate recomputePath itself: it can still fire via a pending hasDelayedRecomputation from before
    // the gamerule turned on, or any direct caller.
    @Inject(method = "recomputePath", at = @At("HEAD"), cancellable = true)
    private void reignofnether$noRecompute(CallbackInfo ci) {
        if (reignofnether$ownsRepath()) ci.cancel();
    }

    @ModifyConstant(method = "followThePath", constant = @Constant(doubleValue = 1.0))
    private double reignofnether$widenVerticalReach(double original) {
        // Climbing spiders barely gate on Y at all: a climb is a straight column (same X/Z), so the horizontal
        // reach already keeps them on the wall. With a big vertical window they advance to the far end of the
        // column right away and just go for it up/down, instead of fussing over each block of the climb. Wide
        // units get the gate relaxed to cover a full MAX_FALL_DROP: the pathfinder now emits multi-block drop
        // nodes (see GridAStar fall pass), so a body perched at the lip of a 2-3 block drop must be able to
        // validate the landing node below it and commit to the fall, instead of spinning on the ledge unable to
        // reach a node it's standing right above. +0.5 so a drop of exactly MAX_FALL_DROP still validates.
        if (reignofnether$isClimbingSpider()) return 16.0;
        return reignofnether$isWideUnit() ? PathfinderConfig.MAX_FALL_DROP + 0.5 : original;
    }

    // The other half of the descent deadlock: vanilla only advances to the next node once the body is within
    // maxDistanceToWaypoint (~bbWidth/2) HORIZONTALLY of it. A wide body perched on a block can never get its
    // centre that close to a lower-forward node while it's still grounded (its base overhangs the lip, so it
    // never falls into reach) - so the path never advances and it spins on the edge. When the next node is a drop
    // below the mob and the mob is roughly over the lip, advance anyway: the follow target jumps to the node
    // BEYOND the drop, giving a forward pull that walks the body off the ledge. One advance per tick (a staircase
    // steps down one node at a time); never past the final target node. Narrow units / climbing spiders excluded.
    @Inject(method = "followThePath", at = @At("HEAD"))
    private void reignofnether$commitDescent(CallbackInfo ci) {
        if (!reignofnether$isWideUnit()) return;
        Path p = this.path;
        if (p == null || p.isDone()) return;
        int idx = p.getNextNodeIndex();
        if (idx >= p.getNodeCount() - 1) return; // keep the final target node; only commit intermediate drops
        Node next = p.getNode(idx);
        if (next.y >= mob.getY() - 0.5) return; // not a drop below the mob's feet
        double dx = (next.x + 0.5) - mob.getX();
        double dz = (next.z + 0.5) - mob.getZ();
        double reach = mob.getBbWidth() / 2.0 + 1.0;
        if (dx * dx + dz * dz <= reach * reach)
            p.advance();
    }

    // Straight lines (LineOfWalk; troop animation gain 2, lovish Oct 30 "Smooth the real path"): after vanilla's own
    // step, a ground unit aims at the farthest of the next few nodes its whole body can walk to in a straight line, so
    // it walks an off-axis route straight instead of zig-zagging node to node (and its body stops rocking). Judged
    // when the path changes, when the unit reaches the node it aimed at, and every LineOfWalk.REAIM_TICKS ticks on the
    // way (from where it is then, so a long route is aimed further along it) - not every tick, and not on every node it
    // passes while no line is clear (in a town that would be every 3-4 ticks a unit). At each re-aim the line to the
    // node it aims at is judged again from where it now is: a unit pushed off it (a crowd, a knockback) walks on from
    // the nearest node it skipped instead of through cells nobody checked.
    @Unique private Path reignofnether$linedPath = null;
    @Unique private int reignofnether$linedFrom = -1;
    @Unique private int reignofnether$linedTo = -1;
    @Unique private boolean reignofnether$linedFound = false;
    @Unique private long reignofnether$linedAt = 0;

    @Inject(method = "followThePath", at = @At("TAIL"))
    private void reignofnether$walkTheLine(CallbackInfo ci) {
        Path p = this.path;
        if (p == null || p.isDone() || !(mob instanceof Unit) || !((Object) this instanceof GroundPathNavigation)
                || reignofnether$isClimbingSpider() || !mob.onGround() || mob.isInWater())
            return;
        int next = p.getNextNodeIndex();
        long now = mob.level().getGameTime();
        boolean samePath = p == reignofnether$linedPath;
        if (samePath && now - reignofnether$linedAt < LineOfWalk.REAIM_TICKS
                && (next <= reignofnether$linedTo || !reignofnether$linedFound))
            return;
        if (!samePath || next > reignofnether$linedTo)
            reignofnether$linedFrom = next;
        reignofnether$linedPath = p;
        reignofnether$linedAt = now;
        // aiming past nodes it skipped, and the line there is lost: back to the nearest of them
        if (next > reignofnether$linedFrom && !LineOfWalk.canWalkTo(mob, p, next)) {
            int back = LineOfWalk.nearest(mob, p, reignofnether$linedFrom, next);
            p.setNextNodeIndex(back);
            reignofnether$linedFrom = reignofnether$linedTo = back;
            reignofnether$linedFound = false;
            return;
        }
        int to = next >= p.getNodeCount() - 1 ? next : LineOfWalk.farthest(mob, p);
        reignofnether$linedTo = Math.max(to, next);
        // walking a line (now or since linedFrom): at its end the next is judged at once, not REAIM_TICKS later
        reignofnether$linedFound = reignofnether$linedTo > reignofnether$linedFrom;
        if (to > next)
            p.setNextNodeIndex(to);
    }
}
