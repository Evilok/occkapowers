package com.occka.occkapowers.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
        public static final KeyMapping KEY_SHIFT_ABILITY = new KeyMapping(
                        "key.occkapowers.shift",
                        KeyConflictContext.IN_GAME,
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_R,
                        "key.categories.occkapowers");

        public static final KeyMapping KEY_ABILITY = new KeyMapping(
                        "key.occkapowers.ability",
                        KeyConflictContext.IN_GAME,
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_F,
                        "key.categories.occkapowers");

        public static final KeyMapping KEY_ULT = new KeyMapping(
                        "key.occkapowers.ult",
                        KeyConflictContext.IN_GAME,
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_G,
                        "key.categories.occkapowers");
}
