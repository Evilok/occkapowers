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

        int barW = 52;
        int barH = 6;
        int gap = 6;
        int totalW = 3 * barW + 2 * gap;
        int bx = sw / 2 - totalW / 2;
        int by = sh - 65;

        // Class name label centered above bars
        String label = "[ " + ClientPowerData.powerType.getId().toUpperCase() + " ]";
        graphics.drawCenteredString(mc.font, label, sw / 2, by - 11, getPowerArgb() | 0xFF000000);

        // Shift bar (always unlocked, no lock icon)
        renderBar(graphics, mc, bx, by, "SHIFT", ClientPowerData.shiftProgress(),
                ClientPowerData.shiftReady(), ClientPowerData.shiftCd, true, getSlotColor(0));

        // Ability bar
        renderBar(graphics, mc, bx + barW + gap, by, "ABILITY", ClientPowerData.abilityProgress(),
                ClientPowerData.abilityReady(), ClientPowerData.abilityCd, ClientPowerData.abilityUnlocked,
                getSlotColor(1));

        // Ult bar
        renderBar(graphics, mc, bx + 2 * (barW + gap), by, "ULT", ClientPowerData.ultProgress(),
                ClientPowerData.ultReady(), ClientPowerData.ultCd, ClientPowerData.ultUnlocked, getSlotColor(2));
    }

    private void renderBar(GuiGraphics g, Minecraft mc,
            int x, int y, String name,
            float progress, boolean ready, int cdTicks,
            boolean unlocked, int color) {
        int bw = 52, bh = 6, barY = y + 9;

        // Background panel
        g.fill(x - 1, y - 1, x + bw + 1, y + 25, 0xAA000000);

        if (!unlocked) {
            // Locked state - grey fill with lock indicator
            g.fill(x, barY, x + bw, barY + bh, 0xFF555555);
            g.drawCenteredString(mc.font, name, x + bw / 2, y, 0xFFAAAAAA);
            g.drawCenteredString(mc.font, "LOCKED", x + bw / 2, y + 16, 0xFFFF5555);
        } else {
            // Progress fill
            int fillW = (int) (bw * progress);
            int fillColor = (ready ? color : darken(color, 0.45f)) | 0xFF000000;
            g.fill(x, barY, x + fillW, barY + bh, fillColor);

            // Thin border
            g.fill(x, barY, x + bw, barY + 1, 0x88FFFFFF);
            g.fill(x, barY + bh - 1, x + bw, barY + bh, 0x88FFFFFF);
            g.fill(x, barY, x + 1, barY + bh, 0x88FFFFFF);
            g.fill(x + bw - 1, barY, x + bw, barY + bh, 0x88FFFFFF);

            // Name label
            int nameColor = ready ? 0xFFFFFFFF : 0xFFAAAAAA;
            g.drawCenteredString(mc.font, name, x + bw / 2, y, nameColor);

            // CD or READY text
            if (!ready) {
                String cdText = String.format("%.1fs", cdTicks / 20f);
                g.drawCenteredString(mc.font, cdText, x + bw / 2, y + 16, 0xFFFF5555);
            } else {
                g.drawCenteredString(mc.font, "READY", x + bw / 2, y + 16, 0xFF55FF55);
            }
        }
    }

    // Returns color as int (no alpha) for a given slot index
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
