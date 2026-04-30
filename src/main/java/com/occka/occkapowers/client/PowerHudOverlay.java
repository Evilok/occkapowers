package com.occka.occkapowers.client;
 
import com.occka.occkapowers.ability.PowerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
 
public class PowerHudOverlay {
 
    public void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;
        if (ClientPowerData.powerType == PowerType.NONE)
            return;
 
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int barW = 52, gap = 6;
        int totalW = 3 * barW + 2 * gap;
        int bx = sw / 2 - totalW / 2;
        int by = sh - 65;
 
        String label = "[ " + ClientPowerData.powerType.getId().toUpperCase() + " ]";
        graphics.drawCenteredString(mc.font, label, sw / 2, by - 11, getPowerArgb() | 0xFF000000);
 
        // SHIFT
        boolean hasShiftCharges = ClientPowerData.shiftMaxCharges > 0;
        float shiftProgress = hasShiftCharges
                ? (ClientPowerData.shiftCharges >= ClientPowerData.shiftMaxCharges ? 1f
                        : (float) (ClientPowerData.shiftChargeCdMax - ClientPowerData.shiftChargeCd)
                                / ClientPowerData.shiftChargeCdMax)
                : ClientPowerData.shiftProgress();
        boolean shiftReady = hasShiftCharges
                ? ClientPowerData.shiftCharges > 0
                : ClientPowerData.shiftReady();
        int shiftCdTicks = hasShiftCharges
                ? ClientPowerData.shiftChargeCd
                : ClientPowerData.shiftCd;
 
        renderBar(graphics, mc, bx, by, "SHIFT",
                shiftProgress, shiftReady,
                shiftCdTicks,
                true, getSlotColor(0),
                ClientPowerData.shiftCharges, ClientPowerData.shiftMaxCharges,
                ClientPowerData.shiftChargeCd, ClientPowerData.shiftChargeCdMax);
 
        // ABILITY
        boolean hasAbilityCharges = ClientPowerData.abilityMaxCharges > 0;
        float abilityProgress = hasAbilityCharges
                ? (ClientPowerData.abilityCharges >= ClientPowerData.abilityMaxCharges ? 1f
                        : (float) (ClientPowerData.abilityChargeCdMax - ClientPowerData.abilityChargeCd)
                                / ClientPowerData.abilityChargeCdMax)
                : ClientPowerData.abilityProgress();
        boolean abilityReady = hasAbilityCharges
                ? ClientPowerData.abilityCharges > 0
                : ClientPowerData.abilityReady();
        int abilityCdTicks = hasAbilityCharges
                ? ClientPowerData.abilityChargeCd
                : ClientPowerData.abilityCd;
 
        renderBar(graphics, mc, bx + barW + gap, by, "ABILITY",
                abilityProgress, abilityReady,
                abilityCdTicks,
                ClientPowerData.abilityUnlocked, getSlotColor(1),
                ClientPowerData.abilityCharges, ClientPowerData.abilityMaxCharges,
                ClientPowerData.abilityChargeCd, ClientPowerData.abilityChargeCdMax);
 
        // ULT
        boolean hasUltCharges = ClientPowerData.ultMaxCharges > 0;
        float ultProgress = hasUltCharges
                ? (ClientPowerData.ultCharges >= ClientPowerData.ultMaxCharges ? 1f
                        : (float) (ClientPowerData.ultChargeCdMax - ClientPowerData.ultChargeCd)
                                / ClientPowerData.ultChargeCdMax)
                : ClientPowerData.ultProgress();
        boolean ultReady = hasUltCharges
                ? ClientPowerData.ultCharges > 0
                : ClientPowerData.ultReady();
        int ultCdTicks = hasUltCharges
                ? ClientPowerData.ultChargeCd
                : ClientPowerData.ultCd;
 
        renderBar(graphics, mc, bx + 2 * (barW + gap), by, "ULT",
                ultProgress, ultReady,
                ultCdTicks,
                ClientPowerData.ultUnlocked, getSlotColor(2),
                ClientPowerData.ultCharges, ClientPowerData.ultMaxCharges,
                ClientPowerData.ultChargeCd, ClientPowerData.ultChargeCdMax);
    }
 
    private void renderBar(GuiGraphics g, Minecraft mc,
            int x, int y, String name,
            float progress, boolean ready, int cdTicks,
            boolean unlocked, int color,
            int charges, int maxCharges, int chargeCd, int chargeCdMax) {
        int bw = 52, bh = 6, barY = y + 9;
 
        g.fill(x - 1, y - 1, x + bw + 1, y + 25, 0xAA000000);
 
        if (!unlocked) {
            g.fill(x, barY, x + bw, barY + bh, 0xFF555555);
            g.drawCenteredString(mc.font, name, x + bw / 2, y, 0xFFAAAAAA);
            g.drawCenteredString(mc.font, "LOCKED", x + bw / 2, y + 16, 0xFFFF5555);
            return;
        }
 
        int fillW = (int) (bw * Math.max(0, Math.min(1, progress)));
        int fillColor = (ready ? color : darken(color, 0.45f)) | 0xFF000000;
        g.fill(x, barY, x + fillW, barY + bh, fillColor);
 
        // Border
        g.fill(x, barY, x + bw, barY + 1, 0x88FFFFFF);
        g.fill(x, barY + bh - 1, x + bw, barY + bh, 0x88FFFFFF);
        g.fill(x, barY, x + 1, barY + bh, 0x88FFFFFF);
        g.fill(x + bw - 1, barY, x + bw, barY + bh, 0x88FFFFFF);
 
        int nameColor = ready ? 0xFFFFFFFF : 0xFFAAAAAA;
 
        if (maxCharges > 0) {
            String displayName = charges > 0 ? name + " x" + charges : name;
            g.drawCenteredString(mc.font, displayName, x + bw / 2, y, nameColor);
 
            if (charges >= maxCharges) {
                g.drawCenteredString(mc.font, "READY", x + bw / 2, y + 16, 0xFF55FF55);
            } else if (charges > 0) {
                String cdText = String.format("%.1fs", chargeCd / 20f);
                g.drawCenteredString(mc.font, cdText, x + bw / 2, y + 16, 0xFFFFAA00);
            } else {
                String cdText = String.format("%.1fs", chargeCd / 20f);
                g.drawCenteredString(mc.font, cdText, x + bw / 2, y + 16, 0xFFFF5555);
            }
        } else {
            g.drawCenteredString(mc.font, name, x + bw / 2, y, nameColor);
            if (!ready) {
                String cdText = String.format("%.1fs", cdTicks / 20f);
                g.drawCenteredString(mc.font, cdText, x + bw / 2, y + 16, 0xFFFF5555);
            } else {
                g.drawCenteredString(mc.font, "READY", x + bw / 2, y + 16, 0xFF55FF55);
            }
        }
    }
 
    private int getSlotColor(int slot) {
        int[][] colors = switch (ClientPowerData.powerType) {
            case FIRE -> new int[][] { { 0xFF4400 }, { 0xFF7700 }, { 0xFF0000 } };
            case AIR -> new int[][] { { 0xAADDFF }, { 0x88BBFF }, { 0xDDEEFF } };
            case WATER -> new int[][] { { 0x0055EE }, { 0x0099FF }, { 0x00CCFF } };
            case ICE -> new int[][] { { 0x88DDFF }, { 0x44AAFF }, { 0xCCFFFF } };
            case CHAOS -> new int[][] { { 0xAA0000 }, { 0xFF4400 }, { 0x8800AA } };
            case LIGHTNING -> new int[][] { { 0xFFFF00 }, { 0xFFCC00 }, { 0xFFFF88 } };
            case ADEPT -> new int[][] { { 0x22AA44 }, { 0x55DD66 }, { 0x88FFAA } };
            case LASER -> new int[][] { { 0xEE0000 }, { 0xFF4400 }, { 0xFF6600 } };
            case GEO -> new int[][] { { 0x886644 }, { 0x664422 }, { 0xAA8855 } };
            case VOID -> new int[][] { { 0x8800EE }, { 0xAA00CC }, { 0x440066 } };
            case LIGHT -> new int[][] { { 0xFFFF88 }, { 0xFFEE44 }, { 0xFFFFCC } };
            case SUPERFORCE -> new int[][] { { 0xFFAA00 }, { 0xFF8800 }, { 0xFFCC44 } };
            case GRAVITY -> new int[][] { { 0x444466 }, { 0x333355 }, { 0x666688 } };
            case ECHO -> new int[][] { { 0x44EE88 }, { 0x22CC66 }, { 0x88FFAA } };
            case FLOWER -> new int[][] { { 0xddff88 }, { 0xa2ff88 }, { 0xa2ff88 } };
            default -> new int[][] { { 0xAAAAAA }, { 0xAAAAAA }, { 0xAAAAAA } };
        };
        return colors[Math.min(slot, 2)][0];
    }
 
    private int getPowerArgb() {
        return switch (ClientPowerData.powerType) {
            case FIRE -> 0xFF4400;
            case AIR -> 0xAADDFF;
            case WATER -> 0x0099FF;
            case ICE -> 0x88EEFF;
            case SUPERFORCE -> 0xFFAA00;
            case CHAOS -> 0x8800AA;
            case ADEPT -> 0x44EE77;
            case LIGHTNING -> 0xFFFF00;
            case LASER -> 0xFF2200;
            case GEO -> 0xAA8844;
            case VOID -> 0x9900EE;
            case LIGHT -> 0xFFEE44;
            case GRAVITY -> 0x5555AA;
            case FLOWER -> 0xdeffd5;
            case ECHO -> 0x44EE88;
            default -> 0xFFFFFF;
        };
    }
 
    private int darken(int color, float f) {
        int r = (int) (((color >> 16) & 0xFF) * f);
        int g = (int) (((color >> 8) & 0xFF) * f);
        int b = (int) ((color & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }
}