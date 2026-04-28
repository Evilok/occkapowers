package com.occka.occkapowers.client;

import com.occka.occkapowers.OcckaPowers;
import com.occka.occkapowers.ability.PowerType;
import com.occka.occkapowers.network.NetworkHandler;
import com.occka.occkapowers.network.PacketActivateAbility;
import com.occka.occkapowers.network.PacketFireUltShoot;
import com.occka.occkapowers.network.PacketLaserUltChannel;
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

    // Локальный флаг канала лазера — не нужен в ClientPowerData
    private static boolean laserChanneling = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        // --- Shift ability ---
        if (KeyBindings.KEY_SHIFT_ABILITY.isDown()) {
            shiftHeldTicks++;
            if (shiftHeldTicks == 1 || shiftHeldTicks % 20 == 0) {
                NetworkHandler.CHANNEL.sendToServer(new PacketShiftHeld());
            }
        } else {
            if (shiftHeldTicks > 0) shiftHeldTicks = 0;
        }

        // --- Ability (one-shot) ---
        while (KeyBindings.KEY_ABILITY.consumeClick()) {
            NetworkHandler.CHANNEL.sendToServer(new PacketActivateAbility(1));
        }

        // --- Ult ---
        boolean isLaser = ClientPowerData.powerType == PowerType.LASER
                && ClientPowerData.ultUnlocked
                && ClientPowerData.ultCd == 0;

        if (isLaser || laserChanneling) {
            boolean keyDown = KeyBindings.KEY_ULT.isDown();

            if (keyDown && !laserChanneling) {
                // Первый тик — запускаем канал
                laserChanneling = true;
                NetworkHandler.CHANNEL.sendToServer(new PacketActivateAbility(2));
            } else if (keyDown && laserChanneling) {
                // Держим — тикаем канал
                NetworkHandler.CHANNEL.sendToServer(new PacketLaserUltChannel(true));
            } else if (!keyDown && laserChanneling) {
                // Отпустили
                laserChanneling = false;
                NetworkHandler.CHANNEL.sendToServer(new PacketLaserUltChannel(false));
            }

            // Сбрасываем флаг если сервер поставил КД (ultCd > 0 = канал завершён)
            if (laserChanneling && ClientPowerData.ultCd > 0) {
                laserChanneling = false;
            }
        } else {
            // Все остальные классы — обычная one-shot ульта
            while (KeyBindings.KEY_ULT.consumeClick()) {
                NetworkHandler.CHANNEL.sendToServer(new PacketActivateAbility(2));
            }
        }

        // --- Fire ult LMB ---
        if (ClientPowerData.fireUltActive && mc.options.keyAttack.consumeClick()) {
            NetworkHandler.CHANNEL.sendToServer(new PacketFireUltShoot());
        }
    }
}
