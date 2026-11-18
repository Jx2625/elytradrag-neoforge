package com.muyu.elytradrag.event;

import com.muyu.elytradrag.ElytraDragNeoForge;
import com.muyu.elytradrag.config.ElytraDragConfig;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = ElytraDragNeoForge.MODID)
public class ServerEvents {

    private static final Set<UUID> BRAKING_PLAYERS = new HashSet<>();

    public static void handleBrakeState(Player player, boolean isBraking) {
        if (player == null) {
            return;
        }
        // 兜底校验：客户端异常/包乱序时，只认"在空中（正在滑翔）"的刹车上报。
        // 站着按 Shift 不再能把 UUID 塞进来，也不会顺带截断落地摔落伤害。
        boolean accepted = isBraking && (player.isFallFlying() || !player.onGround());
        if (accepted) {
            BRAKING_PLAYERS.add(player.getUUID());
        } else {
            BRAKING_PLAYERS.remove(player.getUUID());
        }
    }

    // 0. 玩家断开/重生时清掉残留标记，避免 UUID 泄漏 / 同 UUID 重连或重生继承旧状态
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        BRAKING_PLAYERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        BRAKING_PLAYERS.remove(event.getEntity().getUUID());
    }

    // 1. 拦截摔落伤害，并截断到配置的距离
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (BRAKING_PLAYERS.contains(player.getUUID())) {
                float maxFall = (float) (double) ElytraDragConfig.INSTANCE.maxFallDistance.get();

                if (event.getDistance() > maxFall) {
                    event.setDistance(maxFall);
                }
            }
        }
    }

    // 2. 拦截高速撞击方块的动能伤害
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (BRAKING_PLAYERS.contains(player.getUUID())) {
                // 配置关闭 → 放行原版动能伤害
                if (!ElytraDragConfig.INSTANCE.removeKineticDamage.get()) {
                    return;
                }
                if (event.getSource().is(DamageTypes.FLY_INTO_WALL)) {
                    event.setCanceled(true);
                }
            }
        }
    }
}
