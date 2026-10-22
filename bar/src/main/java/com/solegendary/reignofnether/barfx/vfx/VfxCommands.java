package com.solegendary.reignofnether.barfx.vfx;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * {@code /bat vfx <id> [scale]} - plays an effect where you look (the block under the crosshair, 96 blocks at most;
 * 6 blocks ahead when the look hits nothing), and {@code /bat vfx <id> self [scale]} - on you, following you. For
 * tuning an effect: edit its JSON, F3+T, run the command again. Goes through the real server path (BarFx event, fog
 * of war, send range), so what you see is what players see. Ops only (permission 2).
 */
public final class VfxCommands {
    private VfxCommands() { }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("bat")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("vfx")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(VfxIds.SHIPPED, b))
                    .executes(ctx -> play(ctx.getSource(), StringArgumentType.getString(ctx, "id"), 1f, false))
                    .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1f, 8f))
                        .executes(ctx -> play(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                            FloatArgumentType.getFloat(ctx, "scale"), false)))
                    .then(Commands.literal("self")
                        .executes(ctx -> play(ctx.getSource(), StringArgumentType.getString(ctx, "id"), 1f, true))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.1f, 8f))
                            .executes(ctx -> play(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                                FloatArgumentType.getFloat(ctx, "scale"), true)))))));
    }

    static int play(CommandSourceStack src, String id, float scale, boolean self) {
        if (!VfxIds.valid(id)) {
            src.sendFailure(Component.literal("Not an effect id: " + id + " (lower-case letters, digits, _ and /)"));
            return 0;
        }
        Entity who = src.getEntity();
        if (self && who != null) {
            Vfx.on(src.getLevel(), id, who, scale);
            src.sendSuccess(() -> Component.literal("vfx " + id + " on you (x" + scale + ")"), false);
            return 1;
        }
        Vec3 at = src.getPosition();
        if (who instanceof ServerPlayer p) {
            Vec3 eye = p.getEyePosition();
            Vec3 end = eye.add(p.getLookAngle().scale(96));
            BlockHitResult hit = src.getLevel().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
            at = hit.getType() == HitResult.Type.MISS ? eye.add(p.getLookAngle().scale(6)) : hit.getLocation();
        }
        Vfx.at(src.getLevel(), id, at, scale);
        Vec3 shown = at;
        src.sendSuccess(() -> Component.literal(String.format("vfx %s at %.1f %.1f %.1f (x%.2f)%s", id, shown.x, shown.y,
            shown.z, scale, VfxIds.SHIPPED.contains(id) ? "" : " - not a shipped id; the client says if it knows it")), false);
        return 1;
    }
}
