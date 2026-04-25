package com.occka.occkapowers.client;

import com.occka.occkapowers.OcckaPowers;
import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketActivateAbility;
import com.occka.occkapowers.network.PacketFireUltShoot;
import com.occka.occkapowers.network.PacketShiftHeld;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OcckaPowers.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {
    private static final PowerHudOverlay hudOverlay = new PowerHudOverlay();
    public static final IGuiOverlay POWER_HUD = (gui, graphics, partialTick, w, h) -> hudOverlay.renderHud(graphics);

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.KEY_SHIFT_ABILITY);
        event.register(KeyBindings.KEY_ABILITY);
        event.register(KeyBindings.KEY_ULT);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "power_hud", POWER_HUD);
    }
}

@Mod.EventBusSubscriber(modid = OcckaPowers.MOD_ID, value = Dist.CLIENT)
class ClientTickHandler {
    private static int shiftHeldTicks = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null)
            return;

        // Shift ability: send every 20 ticks while held
        if (KeyBindings.KEY_SHIFT_ABILITY.isDown()) {
            shiftHeldTicks++;
            // Send every 20 ticks (~1/sec) - matches server-side 1s effect duration
            if (shiftHeldTicks == 1 || shiftHeldTicks % 20 == 0) {
                NetworkHandler.CHANNEL.sendToServer(new PacketShiftHeld());
            }
        } else {
            if (shiftHeldTicks > 0) shiftHeldTicks = 0;
        }

        while (KeyBindings.KEY_ABILITY.consumeClick()) {
            NetworkHandler.CHANNEL.sendToServer(new PacketActivateAbility(1));
        }
        while (KeyBindings.KEY_ULT.consumeClick()) {
            NetworkHandler.CHANNEL.sendToServer(new PacketActivateAbility(2));
        }

        // Fire ult: send LMB click to server
        if (ClientPowerData.fireUltActive && mc.options.keyAttack.consumeClick()) {
            NetworkHandler.CHANNEL.sendToServer(new PacketFireUltShoot());
        }
    }
}
