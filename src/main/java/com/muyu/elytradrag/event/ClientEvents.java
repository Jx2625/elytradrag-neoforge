package com.muyu.elytradrag.event;

import com.muyu.elytradrag.ElytraDragNeoForge;
import com.muyu.elytradrag.KeyBindings;
import com.muyu.elytradrag.config.ElytraDragConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import nl.enjarai.doabarrelroll.api.RollEntity;
import nl.enjarai.doabarrelroll.DoABarrelRollClient;
import nl.enjarai.doabarrelroll.config.ModConfig;

import java.lang.reflect.Field;

@EventBusSubscriber(modid = ElytraDragNeoForge.MODID, value = Dist.CLIENT)
public class ClientEvents {

    private static boolean wasBraking = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null) {
            wasBraking = false;
            return;
        }

        boolean isBraking = KeyBindings.BRAKE_KEY.isDown();

        if (isBraking && !player.onGround()) {
            // 核心 1：踩刹车时，直接关掉 DaBR 的引擎！
            setDaBREnabled(false);

            applyBrake(player);

            // 锁死视角插值，防止画面抽搐
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
        }

        // 核心 2：松开刹车的瞬间
        if (wasBraking && !isBraking) {
            //  清空实体 Roll
            if (player instanceof RollEntity rollEntity) {
                rollEntity.doABarrelRoll$setRolling(false);
                rollEntity.doABarrelRoll$setRoll(0.0f);
            }

            //  强行重置视角动量
            float currentYaw = player.getYRot();
            float currentPitch = player.getXRot();
            player.setYRot(currentYaw);
            player.setXRot(currentPitch);
            player.yRotO = currentYaw;
            player.xRotO = currentPitch;

            //  清空 DaBR 内部平滑器的残余角动量（防止唤醒后立刻起飞）
            clearDaBRSmoothers();

            //  重新唤醒 DaBR
            setDaBREnabled(true);
        }

        wasBraking = isBraking;
    }

    private static void applyBrake(Player player) {
        Vec3 velocity = player.getDeltaMovement();
        double speed = velocity.length();

        double minSpeed = ElytraDragConfig.INSTANCE.minimumSpeed.get();
        double multiplier = ElytraDragConfig.INSTANCE.dragMultiplier.get();

        if (speed > minSpeed) {
            double dragFactor = 1.0D - (0.05D * multiplier);
            dragFactor = Math.max(0.01D, dragFactor);

            Vec3 newVelocity = velocity.scale(dragFactor);
            if (newVelocity.length() < 0.1D) newVelocity = Vec3.ZERO;

            player.setDeltaMovement(newVelocity);
            player.hurtMarked = true;
            player.startFallFlying();
        } else {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.startFallFlying();
        }

        float maxFall = (float) (double) ElytraDragConfig.INSTANCE.maxFallDistance.get();
        if (player.getDeltaMovement().y > -1.0f && player.fallDistance > 1.0f) {
            player.fallDistance = 1.0f;
        } else if (player.fallDistance > maxFall) {
            player.fallDistance = maxFall;
        }
    }

    // 反射拨动 DaBR 总开关
    private static void setDaBREnabled(boolean enabled) {
        try {
            ModConfig.INSTANCE.setModEnabled(enabled);
        } catch (Exception e) {
            // 忽略可能的异常
        }
    }

    // 反射清空平滑器
    private static void clearDaBRSmoothers() {
        try {
            Field yawField = DoABarrelRollClient.class.getDeclaredField("YAW_SMOOTHER");
            Field pitchField = DoABarrelRollClient.class.getDeclaredField("PITCH_SMOOTHER");
            Field rollField = DoABarrelRollClient.class.getDeclaredField("ROLL_SMOOTHER");

            yawField.setAccessible(true);
            pitchField.setAccessible(true);
            rollField.setAccessible(true);

            Object yawSmoother = yawField.get(null);
            Object pitchSmoother = pitchField.get(null);
            Object rollSmoother = rollField.get(null);

            if (yawSmoother != null) yawSmoother.getClass().getMethod("reset").invoke(yawSmoother);
            if (pitchSmoother != null) pitchSmoother.getClass().getMethod("reset").invoke(pitchSmoother);
            if (rollSmoother != null) rollSmoother.getClass().getMethod("reset").invoke(rollSmoother);

        } catch (Exception e) {
            ElytraDragNeoForge.LOGGER.warn("Failed to clear DaBR smoothers: " + e.getMessage());
        }
    }
}