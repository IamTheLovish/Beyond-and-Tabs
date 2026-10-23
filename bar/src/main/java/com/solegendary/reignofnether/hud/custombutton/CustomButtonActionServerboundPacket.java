package com.solegendary.reignofnether.hud.custombutton;

import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class CustomButtonActionServerboundPacket {
	
	public ResourceLocation button;
	public boolean isLeft;
	
	public CustomButtonActionServerboundPacket(ResourceLocation button, boolean isLeft) {
		this.button = button;
		this.isLeft = isLeft;
	}
	
	public static CustomButtonActionServerboundPacket decode(FriendlyByteBuf buf) {
		return new CustomButtonActionServerboundPacket(buf.readResourceLocation(), buf.readBoolean());
	}
	
	public static void runLeftClickCommand(ResourceLocation button) {
		PacketHandler.INSTANCE.sendToServer(new CustomButtonActionServerboundPacket(button, true));
	}
	
	public static void runRightClickCommand(ResourceLocation button) {
		PacketHandler.INSTANCE.sendToServer(new CustomButtonActionServerboundPacket(button, false));
	}
	
	public void encode(FriendlyByteBuf buf) {
		buf.writeResourceLocation(button);
		buf.writeBoolean(isLeft);
	}
	
	public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
		NetworkEvent.Context ctx = ctxSupplier.get();
		ctx.enqueueWork(() -> {
			ServerPlayer player = ctx.getSender();
			if (player == null)
				return;
			
			accept(player);
		});
		ctx.setPacketHandled(true);
	}

	/** What the server does with this packet from {@code player} (the network handler's body, callable by game tests). */
	public void accept(ServerPlayer player) {
		accept(player, id -> {
			CustomButton button = CustomButtonServerEvents.customButtons.get(id);
			return button == null ? null : new Actions(button.leftClickActions, button.rightClickActions);
		});
	}

	/** A button's two action lists (the lookup is a parameter because a CustomButton is a client class: game tests can't build one). */
	public record Actions(List<CustomButtonActions.CustomButtonAction> left, List<CustomButtonActions.CustomButtonAction> right) { }

	public void accept(ServerPlayer player, Function<ResourceLocation, Actions> lookup) {
		Actions found = lookup.apply(this.button);
		if (found == null)
			return;
		List<CustomButtonActions.CustomButtonAction> actions = found.left();
		if (actions == null || actions.isEmpty())
			return;
		for (CustomButtonActions.CustomButtonAction action : actions) {
			action.execute(player);
		}
	}
}