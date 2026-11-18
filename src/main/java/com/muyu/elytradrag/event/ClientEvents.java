package com.muyu.elytradrag.event;

import com.muyu.elytradrag.ElytraDragNeoForge;
import com.muyu.elytradrag.KeyBindings;
import com.muyu.elytradrag.compat.DaBRRollFreeze;
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

@EventBusSubscriber(modid = ElytraDragNeoForge.MODID, value = Dist.CLIENT)
public class ClientEvents {

    private static boolean wasBraking = false;
    private static boolean freezeActive = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        // 退世界/暂停/开界面：复位，严格还原 DaBR 配置
        if (player == null || mc.level == null || mc.isPaused() || mc.screen != null) {
            if (wasBraking || freezeActive) {
                if (player != null) {
                    DaBRRollFreeze.endBrake(player);
                }
                wasBraking = false;
                freezeActive = false;
            }
            return;
        }

        boolean isBraking = KeyBindings.BRAKE_KEY.isDown();

        // 1. 发包同步 + DaBR 开关切换（边沿触发）
        if (isBraking != wasBraking) {
            PacketDistributor.sendToServer(new BrakeStatePacket(isBraking));

            if (isBraking) {
                DaBRRollFreeze.beginBrake();
                freezeActive = true;
            } else {
                DaBRRollFreeze.endBrake(player);
                freezeActive = false;
            }
        }

        // 2. 持续刹车：施加减速 + 锁插值（不再逐帧归零 roll）
        if (isBraking && !player.onGround() && player.isFallFlying()) {
            applyBrake(player);
            player.yRotO = player.getYRot();
            player.xRotO = player.getXRot();
        }

        wasBraking = isBraking;
    }

    private static void applyBrake(Player player) {
        Vec3 velocity = player.getDeltaMovement();
        double speed = velocity.length() * 20.0;    // 格/秒，与 HUD 显示一致

        double minSpeed = ElytraDragConfig.INSTANCE.minimumSpeed.get();
        double multiplier = ElytraDragConfig.INSTANCE.dragMultiplier.get();

        if (speed > minSpeed) {
            double dragFactor = 1.0D - (0.05D * multiplier);
            dragFactor = Math.max(0.01D, dragFactor);
            player.setDeltaMovement(velocity.scale(dragFactor));
            player.hurtMarked = true;
        }
        // speed <= minSpeed 时什么都不做，保留最小速度，避免悬停抽搐
    }
}
