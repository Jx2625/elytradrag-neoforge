package com.muyu.elytradrag.compat;

import nl.enjarai.doabarrelroll.api.RollEntity;
import nl.enjarai.doabarrelroll.config.ModConfig;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;

public final class DaBRRollFreeze {

    private static Boolean daBRWasEnabled = null;
    private static boolean active = false;

    private static Field rollBackField;
    private static Field lastRollBackField;
    private static boolean rollBackResolved = false;

    private DaBRRollFreeze() {}

    public static void beginBrake() {
        try {
            if (daBRWasEnabled == null) {
                daBRWasEnabled = ModConfig.INSTANCE.getModEnabled();
            }
            ModConfig.INSTANCE.setModEnabled(false);
            active = true;
        } catch (Exception ignored) {}
    }

    public static void endBrake(Player player) {
        if (player instanceof RollEntity rollEntity) {
            try {
                rollEntity.doABarrelRoll$setRolling(false);
                rollEntity.doABarrelRoll$setRoll(0.0f);
            } catch (Exception ignored) {}
        }

        // 关键：清 PlayerMixin 的 prevRoll（上次转动残留）
        clearPrevRoll(player);

        clearRollBack();
        clearSmoothers();

        if (daBRWasEnabled != null) {
            try {
                ModConfig.INSTANCE.setModEnabled(daBRWasEnabled);
            } catch (Exception ignored) {}
            daBRWasEnabled = null;
        }
        active = false;
    }

    private static void clearPrevRoll(Player player) {
        try {
            Field f = Player.class.getDeclaredField("prevRoll");
            f.setAccessible(true);
            f.setFloat(player, 0.0f);
            System.out.println("[ElytraDrag] clearPrevRoll OK");
        } catch (Throwable t) {
            System.out.println("[ElytraDrag] clearPrevRoll FAILED: " + t);
        }
    }

    private static void clearRollBack() {
        try {
            if (!rollBackResolved) {
                Class<?> clazz = Class.forName("nl.enjarai.doabarrelroll.ModEventsClient");
                rollBackField = clazz.getDeclaredField("rollBack");
                lastRollBackField = clazz.getDeclaredField("lastRollBack");
                rollBackField.setAccessible(true);
                lastRollBackField.setAccessible(true);
                rollBackResolved = true;
            }
            rollBackField.setFloat(null, 0.0f);
            lastRollBackField.setFloat(null, 0.0f);
        } catch (Throwable t) {
            rollBackResolved = false;
            rollBackField = null;
            lastRollBackField = null;
        }
    }

    private static void clearSmoothers() {
        try {
            Class<?> clientClass = Class.forName("nl.enjarai.doabarrelroll.DoABarrelRollClient");
            String[] names = {"PITCH_SMOOTHER", "YAW_SMOOTHER", "ROLL_SMOOTHER"};
            for (String name : names) {
                Field f = clientClass.getDeclaredField(name);
                f.setAccessible(true);
                Object smoother = f.get(null);
                if (smoother != null) {
                    smoother.getClass().getMethod("reset").invoke(smoother);
                }
            }
        } catch (Throwable t) {}
    }
}
