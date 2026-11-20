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

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null) {
            wasBraking = false;
            return;
        }

        boolean isBraking = KeyBindings.BRAKE_KEY.isDown();

        // 1. 当刹车状态发生改变时，发送网络包给服务端！
        if (isBraking != wasBraking) {
            PacketDistributor.sendToServer(new BrakeStatePacket(isBraking));
        }

        // 2. 踩刹车时的客户端逻辑
        if (isBraking && !player.onGround()) {
            // 核心修复：只有在玩家处于滑翔状态，或者具备滑翔条件时，才执行刹车逻辑
            // 这里使用 player.isFallFlying() 是最严格的判定。如果你希望在刚起跳还没滑翔时也能按刹车，可以改成检测是否装备了鞘翅。
            if (player.isFallFlying()) {
                setDaBREnabled(false);
                applyBrake(player);

            // 锁死视角插值，防止画面抽搐
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
            }
        }

        // 3. 松开刹车的瞬间
        if (wasBraking && !isBraking) {
            // 清空实体 Roll
            if (player instanceof RollEntity rollEntity) {
                rollEntity.doABarrelRoll$setRolling(false);
                rollEntity.doABarrelRoll$setRoll(0.0f);
            }

            // 强行重置视角动量
            float currentYaw = player.getYRot();
            float currentPitch = player.getXRot();
            player.setYRot(currentYaw);
            player.setXRot(currentPitch);
            player.yRotO = currentYaw;
            player.xRotO = currentPitch;

            // 清空 DaBR 内部平滑器的残余角动量
            clearDaBRSmoothers();
            // 重新唤醒 DaBR
            setDaBREnabled(true);
        }

        wasBraking = isBraking;
    }

    // 纯减速逻辑（防摔已经抽离到服务端）
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

        // 注意：这里删掉了 FallProtection 的调用，因为它现在在服务端跑了！
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