package com.muyu.elytradrag;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final String KEY_CATEGORY = "key.elytradrag.category";
    public static final String KEY_BRAKE = "key.elytradrag.brake";

    public static final KeyMapping BRAKE_KEY = new KeyMapping(
            KEY_BRAKE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            KEY_CATEGORY
    );
}