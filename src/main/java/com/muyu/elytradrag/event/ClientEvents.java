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
            applyBrake(player);

            // 刹车时：把当前的旋转同步，防止插值画面抽搐
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
        }

        // 核心修复：松开刹车的瞬间
        if (wasBraking && !isBraking) {
            // 1. 清理 DaBR 的 Roll 倾斜
            if (player instanceof RollEntity rollEntity) {
                rollEntity.doABarrelRoll$setRolling(false);
                rollEntity.doABarrelRoll$setRoll(0.0f);
            }

            // 2. 终极绝杀：强行重置坐标系的旋转缓存！
            // 这会让任何残余的偏航、俯仰角动量立刻在物理引擎中清零。
            // 无论 DaBR 在底层怎么算，我们直接给它来个重装系统。
            player.setYRot(player.getYRot());
            player.setXRot(player.getXRot());
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
            player.setDeltaMovement(player.getDeltaMovement()); // 刷新物理向量
        }

        wasBraking = isBraking;
    }

    private static void applyBrake(Player player) {
        // 物理减速代码（保持不变）
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
}