package com.muyu.elytradrag.event;

import com.muyu.elytradrag.ElytraDragNeoForge;
import com.muyu.elytradrag.KeyBindings;
import com.muyu.elytradrag.config.ElytraDragConfig;
import com.muyu.elytradrag.network.BrakeStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import nl.enjarai.doabarrelroll.api.RollEntity;
import nl.enjarai.doabarrelroll.DoABarrelRollClient;
import nl.enjarai.doabarrelroll.config.ModConfig;

import java.lang.reflect.Field;

@EventBusSubscriber(modid = ElytraDragNeoForge.MODID, value = Dist.CLIENT)
public class ClientEvents {

    private static boolean wasBraking = false;
    // 新增：记录踩刹车前 DaBR 到底开没开
    private static boolean daBRWasEnabledBeforeBrake = true;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null) {
            wasBraking = false;
            return;
        }

        boolean isBraking = KeyBindings.BRAKE_KEY.isDown();

        // 1. 发包同步
        if (isBraking != wasBraking) {
            PacketDistributor.sendToServer(new BrakeStatePacket(isBraking));
        }

        // 2. 踩刹车的瞬间（新增记录原始状态）
        if (isBraking && !wasBraking) {
            // 在关闭它之前，先看看玩家有没有在配置里关掉它
            daBRWasEnabledBeforeBrake = ModConfig.INSTANCE.getModEnabled();
        }

        // 3. 踩刹车时的逻辑
        if (isBraking && !player.onGround() && player.isFallFlying()) {
            setDaBREnabled(false);
            applyBrake(player);

            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
        }

        // 4. 松开刹车的瞬间
        if (wasBraking && !isBraking) {
            if (player instanceof RollEntity rollEntity) {
                rollEntity.doABarrelRoll$setRolling(false);
                rollEntity.doABarrelRoll$setRoll(0.0f);
            }

            float currentYaw = player.getYRot();
            float currentPitch = player.getXRot();
            player.setYRot(currentYaw);
            player.setXRot(currentPitch);
            player.yRotO = currentYaw;
            player.xRotO = currentPitch;

            clearDaBRSmoothers();

            // 核心修复：恢复玩家进游戏时（或者刹车前）的状态，而不是强行设为 true！
            setDaBREnabled(daBRWasEnabledBeforeBrake);
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
        } else {
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
        }
    }

    private static void setDaBREnabled(boolean enabled) {
        try {
            ModConfig.INSTANCE.setModEnabled(enabled);
        } catch (Exception e) {
            // 忽略可能的异常
        }
    }

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