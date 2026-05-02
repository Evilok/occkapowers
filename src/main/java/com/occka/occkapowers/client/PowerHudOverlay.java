package com.occka.occkapowers.client;

import com.occka.occkapowers.ability.PowerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class PowerHudOverlay {
    private float displayedCreeperCharge = 0.0f;

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

        if (ClientPowerData.powerType == PowerType.MERC) {
            renderMercMadnessOrb(graphics, mc, Math.min(sw - 17, bx + totalW + 24), by + 12);
        } else if (ClientPowerData.powerType == PowerType.CREEPER) {
            renderCreeperChargeOrb(graphics, mc, Math.min(sw - 17, bx + totalW + 24), by + 12);
        }
        if (ClientPowerData.powerType == PowerType.SOUL_REAPER) {
            renderSoulChargeOrb(graphics, mc, Math.min(sw - 17, bx + totalW + 24), by + 12);
        }
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
//
    private int getSlotColor(int slot) {
        int[][] colors = switch (ClientPowerData.powerType) {
            case FIRE -> new int[][] { { 0xFF4400 }, { 0xFF7700 }, { 0xFF0000 } };
            case AIR -> new int[][] { { 0xAADDFF }, { 0x88BBFF }, { 0xDDEEFF } };
            case WATER -> new int[][] { { 0x0055EE }, { 0x0099FF }, { 0x00CCFF } };
            case ICE -> new int[][] { { 0x88DDFF }, { 0x44AAFF }, { 0xCCFFFF } };
            case CHAOS -> new int[][] { { 0xAA0000 }, { 0xFF4400 }, { 0x8800AA } };
            case BRUTE -> new int[][] { { 0x8A1E14 }, { 0xB23822 }, { 0xE56B3F } };
            case LIGHTNING -> new int[][] { { 0xFFFF00 }, { 0xFFCC00 }, { 0xFFFF88 } };
            case CREEPER -> new int[][] { { 0x22BB22 }, { 0x44DD44 }, { 0x00FF66 } };
            case ADEPT -> new int[][] { { 0x22AA44 }, { 0x55DD66 }, { 0x88FFAA } };
            case LASER -> new int[][] { { 0xEE0000 }, { 0xFF4400 }, { 0xFF6600 } };
            case VADER -> new int[][] { { 0x330000 }, { 0x880000 }, { 0xFF0000 } };
            case GEO -> new int[][] { { 0x886644 }, { 0x664422 }, { 0xAA8855 } };
            case VOID -> new int[][] { { 0x8800EE }, { 0xAA00CC }, { 0x440066 } };
            case LIGHT -> new int[][] { { 0xFFFF88 }, { 0xFFEE44 }, { 0xFFFFCC } };
            case FLASH -> new int[][] { { 0xd9b40f }, { 0xd9710f }, { 0xd9450f } };
            case SUPERFORCE -> new int[][] { { 0xFFAA00 }, { 0xFF8800 }, { 0xFFCC44 } };
            case GRAVITY -> new int[][] { { 0x444466 }, { 0x333355 }, { 0x666688 } };
            case ECHO -> new int[][] { { 0x44EE88 }, { 0x22CC66 }, { 0x88FFAA } };
            case FLOWER -> new int[][] { { 0xddff88 }, { 0xa2ff88 }, { 0xa2ff88 } };
            case MERC -> new int[][] { { 0x771010 }, { 0xCC1010 }, { 0xFF2020 } };
            case SOUL_REAPER -> new int[][] { { 0x223344 }, { 0x336666 }, { 0x66CCCC } };
            case DAGATH -> new int[][] { { 0x4E7A28 }, { 0x7A5A2A }, { 0x8E3F1F } };
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
            case CREEPER -> 0x33CC44;
            case CHAOS -> 0x8800AA;
            case ADEPT -> 0x44EE77;
            case LIGHTNING -> 0xFFFF00;
            case VADER -> 0xCC0000;
            case LASER -> 0xFF2200;
            case GEO -> 0xAA8844;
            case BRUTE -> 0xB23822;
            case VOID -> 0x9900EE;
            case LIGHT -> 0xFFEE44;
            case FLASH -> 0xf7fc56;
            case GRAVITY -> 0x5555AA;
            case FLOWER -> 0xdeffd5;
            case ECHO -> 0x44EE88;
            case MERC -> 0xCC1010;
            case SOUL_REAPER -> 0x66CCCC;
            case DAGATH -> 0x7A9A35;
            default -> 0xFFFFFF;
        };
    }

    private void renderMercMadnessOrb(GuiGraphics g, Minecraft mc, int cx, int cy) {
        int r = 12;
        int madness = Math.max(0, Math.min(100, ClientPowerData.madness));
        float fill = madness / 100f;
        int fillTop = cy + r - (int) (2 * r * fill);

        g.drawCenteredString(mc.font, "MAD", cx, cy - r - 9, 0xFFFF7777);

        for (int dy = -r; dy <= r; dy++) {
            int y = cy + dy;
            int half = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - half, y, cx + half + 1, y + 1, 0xAA130708);

            if (y >= fillTop) {
                float row = (float) (y - fillTop) / Math.max(1, cy + r - fillTop);
                int red = madness >= 80 ? 0xFF : 0xB8 + (int) (0x30 * row);
                int green = madness >= 80 ? 0x20 + (int) (0x28 * row) : 0x08 + (int) (0x18 * row);
                int blue = 0x08 + (int) (0x10 * row);
                int color = 0xDD000000 | (red << 16) | (green << 8) | blue;
                g.fill(cx - half + 2, y, cx + half - 1, y + 1, color);
            }
        }

        for (int dy = -r; dy <= r; dy++) {
            int y = cy + dy;
            int half = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - half, y, cx - half + 2, y + 1, 0xCC2A0505);
            g.fill(cx + half - 1, y, cx + half + 1, y + 1, 0xCCFF4444);
        }

        for (int dx = -r; dx <= r; dx++) {
            int half = (int) Math.sqrt(r * r - dx * dx);
            g.fill(cx + dx, cy - half, cx + dx + 1, cy - half + 2, 0xCCFF7777);
            g.fill(cx + dx, cy + half - 1, cx + dx + 1, cy + half + 1, 0xCC350606);
        }

        g.fill(cx - 5, cy - 7, cx - 1, cy - 3, 0x55FFFFFF);
        g.drawCenteredString(mc.font, String.valueOf(madness), cx, cy - 4, 0xFFFFFFFF);
    }

    private void renderSoulChargeOrb(GuiGraphics g, Minecraft mc, int cx, int cy) {
        int r = 12;
        int soul = Math.max(0, Math.min(100, ClientPowerData.soulCharge));
        float fill = soul / 100f;
        int fillTop = cy + r - (int) (2 * r * fill);

        g.drawCenteredString(mc.font, "SOUL", cx, cy - r - 9, 0xFF9FB9C5);

        for (int dy = -r; dy <= r; dy++) {
            int y = cy + dy;
            int half = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - half, y, cx + half + 1, y + 1, 0xAA081015);

            if (y >= fillTop) {
                float row = (float) (y - fillTop) / Math.max(1, cy + r - fillTop);
                int red = 0x45 + (int) (0x22 * row);
                int green = 0x68 + (int) (0x34 * row);
                int blue = 0x75 + (int) (0x48 * row);
                int color = 0xDD000000 | (red << 16) | (green << 8) | blue;
                g.fill(cx - half + 2, y, cx + half - 1, y + 1, color);
            }
        }

        for (int dy = -r; dy <= r; dy++) {
            int y = cy + dy;
            int half = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - half, y, cx - half + 2, y + 1, 0xCC1B2C34);
            g.fill(cx + half - 1, y, cx + half + 1, y + 1, 0xCC9EC7D2);
        }

        for (int dx = -r; dx <= r; dx++) {
            int half = (int) Math.sqrt(r * r - dx * dx);
            g.fill(cx + dx, cy - half, cx + dx + 1, cy - half + 2, 0xCCAED3DC);
            g.fill(cx + dx, cy + half - 1, cx + dx + 1, cy + half + 1, 0xCC15262D);
        }

        g.fill(cx - 5, cy - 7, cx - 1, cy - 3, 0x66D9F4FF);
        g.drawCenteredString(mc.font, String.valueOf(soul), cx, cy - 4, 0xFFE6F8FF);
    }

    private void renderCreeperChargeOrb(GuiGraphics g, Minecraft mc, int cx, int cy) {
        int r = 12;
        displayedCreeperCharge += (ClientPowerData.creeperCharge - displayedCreeperCharge) * 0.25f;
        int charge = Math.max(0, Math.min(100, Math.round(displayedCreeperCharge)));
        float fill = charge / 100f;
        int fillTop = cy + r - (int) (2 * r * fill);

        g.drawCenteredString(mc.font, "CHG", cx, cy - r - 9, 0xFF77FFAA);

        for (int dy = -r; dy <= r; dy++) {
            int y = cy + dy;
            int half = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - half, y, cx + half + 1, y + 1, 0xAA061408);

            if (y >= fillTop) {
                float row = (float) (y - fillTop) / Math.max(1, cy + r - fillTop);
                int color = lerpColor(0x33DD44, 0x3399FF, Math.min(1.0f, fill * 0.85f + row * 0.15f));
                g.fill(cx - half + 2, y, cx + half - 1, y + 1, 0xDD000000 | color);
            }
        }

        int border = lerpColor(0x55FF66, 0x55AAFF, fill);
        int darkBorder = lerpColor(0x0B5A12, 0x0A2F7A, fill);
        for (int dy = -r; dy <= r; dy++) {
            int y = cy + dy;
            int half = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - half, y, cx - half + 2, y + 1, 0xCC000000 | darkBorder);
            g.fill(cx + half - 1, y, cx + half + 1, y + 1, 0xCC000000 | border);
        }

        for (int dx = -r; dx <= r; dx++) {
            int half = (int) Math.sqrt(r * r - dx * dx);
            g.fill(cx + dx, cy - half, cx + dx + 1, cy - half + 2, 0xCC000000 | border);
            g.fill(cx + dx, cy + half - 1, cx + dx + 1, cy + half + 1, 0xCC000000 | darkBorder);
        }

        g.fill(cx - 5, cy - 7, cx - 1, cy - 3, 0x55FFFFFF);
        g.drawCenteredString(mc.font, charge + "%", cx, cy - 4, 0xFFFFFFFF);
    }

    private int lerpColor(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int fr = (from >> 16) & 0xFF;
        int fg = (from >> 8) & 0xFF;
        int fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF;
        int tg = (to >> 8) & 0xFF;
        int tb = to & 0xFF;
        int r = (int) (fr + (tr - fr) * t);
        int g = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | b;
    }

    private int darken(int color, float f) {
        int r = (int) (((color >> 16) & 0xFF) * f);
        int g = (int) (((color >> 8) & 0xFF) * f);
        int b = (int) ((color & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }
}
